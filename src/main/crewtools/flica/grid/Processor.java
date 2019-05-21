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

package crewtools.flica.grid;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.bid.ScheduleWrapper;
import crewtools.flica.bid.ScheduleWrapper.OverlapEvaluation;
import crewtools.flica.bid.TripDatabase;
import crewtools.flica.bid.TripScore;
import crewtools.flica.parser.OpenTimeParser;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.rpc.Proto.GridObservation;
import crewtools.util.Clock;
import crewtools.util.Collections;
import crewtools.util.Period;

public class Processor extends Thread implements Observer {
  private final Logger logger = Logger.getLogger(Processor.class.getName());

  private final Clock clock;
  private final FlicaService flicaService;
  private final BidConfig bidConfig;
  private final GridAdapter gridAdapter;
  private final AwardDomicile toDomicile;
  private final YearMonth yearMonth;
  private final TripDatabase tripDatabase;

  private AtomicBoolean shouldExit = new AtomicBoolean(false);
  private Proto.Schedule protoSchedule = null;
  private Set<LocalDate> greenDays = null;
  private final BlockingQueue<Set<LocalDate>> observedGreenDayQueue =
      new LinkedBlockingQueue<>();
  private ScheduleAdapter scheduleAdapter = new ScheduleAdapter();
  private Map<PairingKey, PairingKey> submittedSwaps = new HashMap<>();

  public Processor(Clock clock, YearMonth yearMonth, FlicaService flicaService,
      AwardDomicile toDomicile, BidConfig bidConfig, TripDatabase tripDatabase) throws IOException, URISyntaxException, ParseException {
    this.gridAdapter = new GridAdapter(clock, yearMonth);
    this.clock = clock;
    this.yearMonth = yearMonth;
    this.flicaService = flicaService;
    this.toDomicile = toDomicile;
    this.bidConfig = bidConfig;
    this.tripDatabase = tripDatabase;
    this.setName("Processor");
    this.setDaemon(false);
  }

  @Override
  public void observe(GridObservation observation) {
    Set<LocalDate> observedGreenDays = gridAdapter.getGreenDays(observation);
    observedGreenDayQueue.add(observedGreenDays);
  }

  @Override
  public void observe(Proto.Schedule schedule) {
    synchronized(this) {
      this.protoSchedule = schedule;
    }
    shouldExit.set(areAllDroppableTripsInDomicile(schedule));
  }

  private boolean areAllDroppableTripsInDomicile(Proto.Schedule protoSchedule) {
    Schedule schedule = scheduleAdapter.adapt(protoSchedule);
    for (Trip trip : schedule.getTrips().values()) {
      if (trip.getPairingName().charAt(0) != toDomicile.getAwardId()
          && trip.isDroppable()) {
        return false;
      }
    }
    return true;
  }

  public void run() {
    while (!shouldExit.get()) {
      try {
        Set<LocalDate> observedGreenDays = observedGreenDayQueue.take();
        if (shouldExit.get()) {
          break;
        }
        if (greenDays != null && greenDays.equals(observedGreenDays)) {
          // no changes
          continue;
        }
        greenDays = observedGreenDays;
        Proto.Schedule protoScheduleCopy;
        synchronized (this) {
          if (protoSchedule == null) {
            // no schedule yet
            continue;
          }
          protoScheduleCopy = protoSchedule.toBuilder().build();
        }
        process(greenDays, scheduleAdapter.adapt(protoScheduleCopy));
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, "Error", e);
      }
    }
    logger.info("Shutting down as our work is done.");
  }

  private void process(final Set<LocalDate> greenDays, Schedule schedule) {
    logger.info("Processing reserve grid change");
    ScheduleWrapper scheduleWrapper = new ScheduleWrapper(
        schedule, yearMonth, clock, bidConfig);
    schedule.getTrips().forEach((key, trip) -> {
      if (key.getPairingName().charAt(0) != toDomicile.getAwardId()
          && greenDays.containsAll(trip.getDepartureDates())
          && scheduleWrapper.getAllDroppable().contains(trip)) {
        try {
          attemptTripDrop(scheduleWrapper, trip);
        } catch (URISyntaxException | IOException | ParseException e) {
          logger.log(Level.WARNING, "Error", e);
        }
      }
    });
  }

  private void attemptTripDrop(ScheduleWrapper scheduleWrapper, Trip dropTrip) throws URISyntaxException, IOException, ParseException {
    logger.info("Attempting to drop trip " + dropTrip.getPairingName());
    Map<Trip, TripScore> eligibleAdds = getEligibleAdds(scheduleWrapper, dropTrip);
    if (eligibleAdds.isEmpty()) {
      return;
    }
    PairingKey dropKey = dropTrip.getPairingKey();
    TripScore dropScore = new TripScore(dropTrip, bidConfig);
    for (Trip addTrip : eligibleAdds.keySet()) {
      TripScore addScore = eligibleAdds.get(addTrip);
      PairingKey addKey = addTrip.getPairingKey();
      if (submittedSwaps.containsKey(addKey)
          && submittedSwaps.get(addKey).equals(dropKey)) {
        continue;
      }
      if (addScore.getPoints() < dropScore.getPoints()) {
        logger.info(String.format("WARNING!  %s (%d) < %s (%d) but swapping anyway",
            addTrip.getPairingName(),
            addScore.getPoints(),
            dropTrip.getPairingName(),
            dropScore.getPoints()));
      }
      if (false) {
        flicaService.submitSwap(FlicaService.BID_FIRST_COME, yearMonth,
            clock.today(), ImmutableList.of(addKey), ImmutableList.of(dropKey));
      } else {
        logger.info("SWAP: drop " + dropKey + ", add " + addKey);
      }
      submittedSwaps.put(addKey, dropKey);
    }
  }

  private List<FlicaTask> getOpentimeTrips() throws URISyntaxException, IOException, ParseException {
    String rawOpenTime = flicaService.getOpenTime(
        toDomicile, Rank.CAPTAIN, FlicaService.BID_FIRST_COME, yearMonth);
    OpenTimeParser openTimeParser = new OpenTimeParser(
        yearMonth.getYear(), rawOpenTime);
    return openTimeParser.parse();
  }

  private Map<Trip, TripScore> getEligibleAdds(ScheduleWrapper scheduleWrapper, Trip dropTrip) throws URISyntaxException, IOException, ParseException {
    Period creditWithoutDropTrip = scheduleWrapper.getSchedule()
        .getCreditInMonth().minus(dropTrip.getCreditInMonth(yearMonth));
    Map<Trip, TripScore> result = new HashMap<>();
    for (FlicaTask task : getOpentimeTrips()) {
      PairingKey key = new PairingKey(task.pairingDate, task.pairingName);
      Trip addTrip = tripDatabase.getTrip(key);
      OverlapEvaluation eval = scheduleWrapper.evaluateOverlap(addTrip);
      if (eval.overlapsUndroppable) {
        logger.info("Considered " + key + " but it overlaps something undroppable");
        continue;
      }
      if (!eval.noOverlap && !eval.droppable.equals(ImmutableList.of(dropTrip))) {
        logger.info("Considered " + key + " but it overlaps something other than drop trip");
        continue;
      }
      Period newCredit = creditWithoutDropTrip.plus(addTrip.getCreditInMonth(yearMonth));
      if (newCredit.isLessThan(Period.hours(65))) {
        logger.info("Considered " + key + " but the new credit would be " + newCredit);
        continue;
      }
      result.put(addTrip, new TripScore(addTrip, bidConfig));
    }
    return Collections.sortByValueDescending(result);
  }
}
