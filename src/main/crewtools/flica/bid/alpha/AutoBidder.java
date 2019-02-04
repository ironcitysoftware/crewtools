/**
 * Copyright 2018 Iron City Software LLC
 *
 * This file is part of CrewTools.
 *
 * CrewTools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CrewTools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CrewTools.  If not, see <http://www.gnu.org/licenses/>.
 */

package crewtools.flica.bid.alpha;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.YearMonth;
import org.subethamail.smtp.server.SMTPServer;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.bid.AutoBidderCommandLineConfig;
import crewtools.flica.bid.FlicaMessageHandler;
import crewtools.flica.bid.RuntimeStats;
import crewtools.flica.bid.ScheduleWrapperTree;
import crewtools.flica.bid.StatusService;
import crewtools.flica.bid.TripDatabase;
import crewtools.flica.parser.OpenTimeParser;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.ScheduleParser;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;
import crewtools.util.FileUtils;
import crewtools.util.FlicaConfig;
import crewtools.util.SystemClock;

// Runs during SAP or opentime, receives email alerts and swaps trips as necessary.
public class AutoBidder {
  // TODO: config
  private static final int IMPROVE_DAYS_OF_MONTH[] = new int[] { 1, 2, 3, 4, 5, 6 };
  private static final AwardDomicile DOMICILE = null;
  private static final Rank RANK = null;
  private static final DateTime SAP_START = null;
  private final int SMTP_PORT = 0;
  private final int SCHEDULE_CHECK_INTERVAL_MINUTES = 60;
  private final int OPENTIME_CHECK_INTERVAL_MINUTES = 60;

  private final Logger logger = Logger.getLogger(AutoBidder.class.getName());


  public static void main(String args[]) throws Exception {
    new AutoBidder().run(args);
  }

  public void run(String args[]) throws Exception {
    run(new AutoBidderCommandLineConfig(args));
  }

  private void run(AutoBidderCommandLineConfig cmdLine) throws Exception {
    BidConfig bidConfig = FileUtils.readBidConfig();
    YearMonth yearMonth = YearMonth.parse(bidConfig.getYearMonth());
    logger.info("Welcome to AutoBidder for " + yearMonth);

    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    FlicaService service = new FlicaService(connection);
    service.connect();

    Clock clock = new SystemClock();
    ScheduleWrapperTree tree = new ScheduleWrapperTree(bidConfig);
    RuntimeStats stats = new RuntimeStats(clock, tree);
    TripDatabase trips = new TripDatabase(service, cmdLine.getUseProto(), yearMonth);
    StatusService statusService = new StatusService(stats, trips, bidConfig);
    statusService.start();

    Schedule schedulePojo = getSchedule(service, yearMonth);
    ScheduleWrapper schedule = new ScheduleWrapper(schedulePojo, yearMonth,
        clock,
        bidConfig,
        cmdLine);

    BlockingQueue<Trip> queue = new LinkedBlockingQueue<Trip>();

    SMTPServer smtpServer = new SMTPServer(
        (context) -> {
          return new FlicaMessageHandler(context, yearMonth, queue, stats);
        });
    smtpServer.setPort(SMTP_PORT);
    logger.info("Listening for SMTP on " + SMTP_PORT);
    smtpServer.start();

    long delay = SAP_START.getMillis() - clock.now().getMillis();
    logger.info("Sleeping " + (delay / (1000 * 60)) + " mins until SAP starts");
    Thread.sleep(delay);

    List<FlicaTask> openTimeTasks = getOpentimeTrips(service, yearMonth,
        cmdLine.getRound());
    if (openTimeTasks != null) {
      Map<Integer, FlicaTask> oneDay = getHighestCreditWithNumDays(openTimeTasks, 1);
      Map<Integer, FlicaTask> twoDay = getHighestCreditWithNumDays(openTimeTasks, 2);
      Map<Integer, FlicaTask> threeDay = getHighestCreditWithNumDays(openTimeTasks, 3);
      Map<Integer, FlicaTask> fourDay = getHighestCreditWithNumDays(openTimeTasks, 4);
      evaluate(oneDay, twoDay, threeDay, fourDay, schedule, service, yearMonth);
    }

    DateTime nextScheduleCheck = clock.now().plusMinutes(SCHEDULE_CHECK_INTERVAL_MINUTES);
    DateTime nextOpentimeCheck = clock.now().plusMinutes(OPENTIME_CHECK_INTERVAL_MINUTES);

    while (true) {
      if (queue.peek() != null) {
        // something came in over email.
        processSingleTrip(queue.take(), schedule, service, yearMonth);
      }
      if (clock.now().isAfter(nextScheduleCheck)) {
        nextScheduleCheck = clock.now().plusMinutes(SCHEDULE_CHECK_INTERVAL_MINUTES);
        Schedule newSchedulePojo = getSchedule(service, yearMonth);
        ScheduleWrapper newSchedule = new ScheduleWrapper(
            newSchedulePojo,
            yearMonth,
            clock,
            bidConfig,
            cmdLine);
        if (!schedule.equalsOriginal(newSchedule)) {
          schedulePojo = newSchedulePojo;
          schedule = newSchedule;
          logger.info("Refresh schedule");
        } else {
          logger.info("Schedule unchanged");
        }
      }
      if (clock.now().isAfter(nextOpentimeCheck)) {
        logger.info("Refresh opentime");
        nextOpentimeCheck = clock.now().plusMinutes(OPENTIME_CHECK_INTERVAL_MINUTES);
        openTimeTasks = getOpentimeTrips(service, yearMonth, cmdLine.getRound());
        if (openTimeTasks != null) {
          Map<Integer, FlicaTask> oneDay = getHighestCreditWithNumDays(openTimeTasks, 1);
          Map<Integer, FlicaTask> twoDay = getHighestCreditWithNumDays(openTimeTasks, 2);
          Map<Integer, FlicaTask> threeDay = getHighestCreditWithNumDays(openTimeTasks,
              3);
          Map<Integer, FlicaTask> fourDay = getHighestCreditWithNumDays(openTimeTasks, 4);
          evaluate(oneDay, twoDay, threeDay, fourDay, schedule, service, yearMonth);
        }
      }
      Thread.sleep(1000);
    }
  }

  private void processSingleTrip(Trip trip,
      ScheduleWrapper schedule, FlicaService service, YearMonth yearMonth)
      throws URISyntaxException, IOException {
    if (trip.getFirstSection().date.getMonthOfYear() < yearMonth.getMonthOfYear()) {
      logger.info("Ignoring " + trip.getPairingKey() + " from previous month");
      return;
    }
    Map<Integer, FlicaTask> oneDay = new HashMap<>();
    Map<Integer, FlicaTask> twoDay = new HashMap<>();
    Map<Integer, FlicaTask> threeDay = new HashMap<>();
    Map<Integer, FlicaTask> fourDay = new HashMap<>();
    FlicaTask task = new FlicaTask(trip.getPairingKey(), trip.credit);
    int dayOfMonth = trip.getStartingDayOfMonth();
    int numDays = trip.getDepartureDates().size();
    if (numDays == 1) {
      oneDay.put(dayOfMonth, task);
    } else if (numDays == 2) {
      twoDay.put(dayOfMonth, task);
    } else if (numDays == 3) {
      threeDay.put(dayOfMonth, task);
    } else if (numDays == 4) {
      fourDay.put(dayOfMonth, task);
    } else {
      logger.info("Oddball trip ignored from email: " + trip);
      return;
    }
    evaluate(oneDay, twoDay, threeDay, fourDay, schedule, service, yearMonth);
  }

  private void evaluate(
      Map<Integer, FlicaTask> oneDay,
      Map<Integer, FlicaTask> twoDay,
      Map<Integer, FlicaTask> threeDay,
      Map<Integer, FlicaTask> fourDay,
      ScheduleWrapper schedule,
      FlicaService service,
      YearMonth yearMonth) throws URISyntaxException, IOException {
    Iterator<PairingKey> trips = schedule.getOrderedKeys().iterator();
    PairingKey FIRST = trips.next();
    PairingKey SECOND = trips.next();
    PairingKey THIRD = trips.next();
    PairingKey FOURTH = trips.next();

    improve(IMPROVE_DAYS_OF_MONTH[1], fourDay, schedule, FIRST, service);
    improve(IMPROVE_DAYS_OF_MONTH[2], twoDay, schedule, SECOND, service);
    improve(IMPROVE_DAYS_OF_MONTH[3], twoDay, schedule, THIRD, service);
    improve(IMPROVE_DAYS_OF_MONTH[4], fourDay, schedule, FOURTH, service);

    if (!schedule.isBestDates()) {
      improve(IMPROVE_DAYS_OF_MONTH[5], oneDay, schedule, SECOND, service);
      improve(IMPROVE_DAYS_OF_MONTH[6], threeDay, schedule, THIRD, service);
    }
  }

  private void improve(int dayOfMonth, Map<Integer, FlicaTask> optionMap,
      ScheduleWrapper schedule, PairingKey currentKey, FlicaService service)
      throws URISyntaxException, IOException {
    if (optionMap.containsKey(dayOfMonth)) {
      FlicaTask task = optionMap.get(dayOfMonth);
      Trip currentTrip = schedule.getTripWithoutCheckingForRemoval(currentKey);
      if (currentTrip.getStartingDayOfMonth() != dayOfMonth
          || task.creditTime.isMoreThan(currentTrip.credit)) {
        schedule.swap(currentKey, task, service);
      }
    }
  }

  private List<FlicaTask> getOpentimeTrips(FlicaService service, YearMonth yearMonth,
      int round) throws URISyntaxException, IOException, ParseException {
    String rawOpenTime = service.getOpenTime(DOMICILE, RANK, round, yearMonth);
    OpenTimeParser openTimeParser = new OpenTimeParser(
        yearMonth.getYear(), rawOpenTime);
    List<FlicaTask> tasks = openTimeParser.parse();
    if (!openTimeParser.isPublished()) {
      return null;
    }
    return tasks;
  }

  private final String NO_SCHEDULE_AVAILABLE = "No schedule available.";

  private Schedule getSchedule(FlicaService service, YearMonth yearMonth)
      throws Exception {
    String rawSchedule = null;
    try {
      rawSchedule = service.getSchedule(yearMonth);
      if (rawSchedule.contains(NO_SCHEDULE_AVAILABLE)) {
        throw new ParseException("No schedule available for " + yearMonth);
      }
      ScheduleParser scheduleParser = new ScheduleParser(rawSchedule);
      Proto.Schedule protoSchedule = scheduleParser.parse();
      ScheduleAdapter scheduleAdapter = new ScheduleAdapter();
      return scheduleAdapter.adapt(protoSchedule);
    } catch (Exception e) {
      throw e;
    }
  }

  private Map<Integer, FlicaTask> getHighestCreditWithNumDays(List<FlicaTask> tasks,
      int numDays) {
    Map<Integer, FlicaTask> result = new HashMap<>();
    for (FlicaTask task : tasks) {
      if (task.numDays == numDays) {
        int dayOfMonth = task.pairingDate.getDayOfMonth();
        if (result.containsKey(dayOfMonth)) {
          if (!task.creditTime.isMoreThan(result.get(dayOfMonth).creditTime)) {
            continue;
          }
        }
        result.put(dayOfMonth, task);
      }
    }
    return result;
  }
}
