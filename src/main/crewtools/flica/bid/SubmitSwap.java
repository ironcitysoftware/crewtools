/**
 * Copyright 2019 Iron City Software LLC
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

package crewtools.flica.bid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.parser.OpenTimeParser;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.ScheduleParser;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.FileUtils;
import crewtools.util.FlicaConfig;

// Submits a swap request (opentime or SAP)
public class SubmitSwap {
  private final Logger logger = Logger.getLogger(SubmitSwap.class.getName());

  public static void main(String args[]) throws Exception {
    new SubmitSwap().run(args);
  }

  public void run(String args[]) throws Exception {
    BidConfig bidConfig = FileUtils.readBidConfig();
    YearMonth yearMonth = YearMonth.parse(bidConfig.getYearMonth());

    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    FlicaService service = new FlicaService(connection);
    service.connect();

    List<PairingKey> openTimeTrips = getOpenTimeTrips(bidConfig, service, yearMonth);
    Map<String, PairingKey> openTime = new HashMap<>();
    for (PairingKey key : openTimeTrips) {
      openTime.put(key.getPairingName(), key);
    }
    Schedule schedule = getSchedule(service, yearMonth);

    List<PairingKey> addTrips = new ArrayList<>();
    List<PairingKey> dropTrips = new ArrayList<>();

    boolean parsingDrop = true;
    for (String arg : args) {
      if (arg.equals("-")) {
        Preconditions.checkState(parsingDrop);
        parsingDrop = false;
        continue;
      }
      PairingKey key;
      if (parsingDrop) {
        key = getPairingKey(schedule, arg);
      } else {
        key = Preconditions.checkNotNull(openTime.get(arg),
            "Pairing " + arg + " is not in opentime");
      }
      if (parsingDrop) {
        dropTrips.add(key);
      } else {
        addTrips.add(key);
      }
    }

    if (addTrips.isEmpty()) {
      System.err.println("Usage: L1234 L2345 ..drops - L4567 ..adds");
      System.exit(1);
    }

    LocalDate firstDayOfMonth = yearMonth.toLocalDate(1);
    logger.info("ADD : " + addTrips);
    logger.info("DROP: " + dropTrips);
    String result = service.submitSwap(
        bidConfig.getRound(),
        yearMonth,
        firstDayOfMonth,
        addTrips,
    	dropTrips);
    System.out.println(result);
  }

  private List<PairingKey> getOpenTimeTrips(BidConfig bidConfig,
      FlicaService service, YearMonth yearMonth) throws Exception {
    Rank rank = Rank.valueOf(bidConfig.getRank());
    int round = bidConfig.getRound();
    AwardDomicile awardDomicile = AwardDomicile.valueOf(bidConfig.getAwardDomicile());
    String rawOpenTime = service.getOpenTime(
        awardDomicile, rank, round, yearMonth);
    OpenTimeParser openTimeParser =
        new OpenTimeParser(yearMonth.getYear(), rawOpenTime);
    List<FlicaTask> tasks = openTimeParser.parse();
    List<PairingKey> openTimeTrips = new ArrayList<>();
    for (FlicaTask task : tasks) {
      openTimeTrips.add(new PairingKey(task.pairingDate, task.pairingName));
    }
    return openTimeTrips;
  }

  private Schedule getSchedule(FlicaService service, YearMonth yearMonth)
      throws IOException, ParseException {
    String rawSchedule = service.getSchedule(yearMonth);
    ScheduleParser scheduleParser = new ScheduleParser(rawSchedule);
    Proto.Schedule protoSchedule = scheduleParser.parse();
    ScheduleAdapter scheduleAdapter = new ScheduleAdapter();
    return scheduleAdapter.adapt(protoSchedule);
  }

  private PairingKey getPairingKey(Schedule schedule, String pairingName) {
    for (PairingKey key : schedule.getTrips().keySet()) {
      if (key.getPairingName().equals(pairingName)) {
        return key;
      }
    }
    throw new IllegalStateException("Pairing " + pairingName + " is not on current schedule");
  }
}
