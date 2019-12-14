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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.bid.FlicaTaskWrapper;
import crewtools.flica.bid.OverlapEvaluator;
import crewtools.flica.bid.OverlapEvaluator.OverlapEvaluation;
import crewtools.flica.bid.ReducedSchedule;
import crewtools.flica.bid.TripDatabase;
import crewtools.flica.bid.TripScore;
import crewtools.flica.grid.GridEvaluator.GridEvaluation;
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
import crewtools.util.Notifier;
import crewtools.util.Period;

public class Processor extends Thread implements Observer {
  private final Logger logger = Logger.getLogger(Processor.class.getName());

  private static final Period MIN_CREDIT = Period.hours(65);

  private final Clock clock;
  private final FlicaService flicaService;
  private final BidConfig bidConfig;
  private final AwardDomicile fromDomicile;
  private final AwardDomicile toDomicile;
  private final YearMonth yearMonth;
  private final TripDatabase tripDatabase;
  private final Notifier notifier;
  private final CountDownLatch initLatch;

  private AtomicBoolean shouldExit = new AtomicBoolean(false);
  private Proto.Schedule protoSchedule = null;
  private GridObservation toGrid = null;
  private GridObservation fromGrid = null;
  private ScheduleAdapter scheduleAdapter = new ScheduleAdapter();
  private Map<PairingKey, PairingKey> submittedSwaps = new HashMap<>();
  private long lastInfoProcessedMillis = 0;
  private long informationTimeMillis = 0;
  private Object blockUntilNewInformation = new Object();

  public Processor(Clock clock, YearMonth yearMonth, FlicaService flicaService,
      AwardDomicile fromDomicile, AwardDomicile toDomicile, BidConfig bidConfig,
      TripDatabase tripDatabase)
      throws IOException, URISyntaxException, ParseException, AddressException {
    this.clock = clock;
    this.yearMonth = yearMonth;
    this.flicaService = flicaService;
    this.fromDomicile = fromDomicile;
    this.toDomicile = toDomicile;
    this.bidConfig = bidConfig;
    this.tripDatabase = tripDatabase;
    this.notifier = new Notifier(
        bidConfig.getNotificationFromAddress(),
        bidConfig.getNotificationToAddress());
    this.initLatch = new CountDownLatch(3);
    this.setName("Processor");
    this.setDaemon(false);
  }

  @Override
  public void observe(GridObservation observation) {
    AwardDomicile domicile = AwardDomicile.valueOf(observation.getDomicile());
    synchronized (this) {
      if (domicile.equals(toDomicile)) {
        toGrid = observation;
        informationTimeMillis = System.currentTimeMillis();
        blockUntilNewInformation.notify();
        initLatch.countDown();
      } else if (domicile.equals(fromDomicile)) {
        fromGrid = observation;
        informationTimeMillis = System.currentTimeMillis();
        blockUntilNewInformation.notify();
        initLatch.countDown();
      } else {
        throw new IllegalStateException("Unexpected grid from domicile "
            + domicile + " which was not " + toDomicile + " or " + fromDomicile);
      }
    }
  }

  @Override
  public void observe(Proto.Schedule schedule) {
    synchronized(this) {
      this.protoSchedule = schedule;
      informationTimeMillis = System.currentTimeMillis();
      blockUntilNewInformation.notify();
      initLatch.countDown();
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
      // if we received a notify() while processing, don't wait.
      boolean needsProcessing;
      synchronized (this) {
        needsProcessing = informationTimeMillis > lastInfoProcessedMillis;
      }
      if (!needsProcessing) {
        try {
          blockUntilNewInformation.wait();
          // This isn't quite right either.
        } catch (InterruptedException e) {
          logger.log(Level.WARNING, "Error awaiting new info", e);
        }
      }
      synchronized (this) {
        lastInfoProcessedMillis = informationTimeMillis;
      }

      try {
        initLatch.await();
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, "Error awaiting init latch", e);
      }

      GridObservation fromGridCopy;
      GridObservation toGridCopy;
      Proto.Schedule protoScheduleCopy;
      synchronized (this) {
        if (protoSchedule == null) {
          logger.info("Waiting for schedule retrieval");
          continue;
        }
        if (fromGrid == null) {
          logger.info("Waiting for from grid retrieval");
          continue;
        }
        if (toGrid == null) {
          logger.info("Waiting for to grid retrieval");
          continue;
        }
        protoScheduleCopy = protoSchedule.toBuilder().build();
        fromGridCopy = fromGrid.toBuilder().build();
        toGridCopy = toGrid.toBuilder().build();
      }
      process(fromGridCopy, toGridCopy, scheduleAdapter.adapt(protoScheduleCopy));
    }
    logger.info("Shutting down as our work is done.");
  }

  private void process(GridObservation fromGrid, GridObservation toGrid,
      Schedule schedule) {
    logger.info("Processing...");
    LocalDate changeHorizon = clock.today().plusDays(1);
    schedule.getTrips().forEach((key, trip) -> {
      if (key.getPairingName().charAt(0) != toDomicile.getAwardId()
          && trip.isDroppable() // it is an actual trip.
          && trip.getEarliestDepartureDate().isAfter(changeHorizon)) {
        try {
          attemptTripDrop(schedule, fromGrid, toGrid, trip);
        } catch (URISyntaxException | IOException | ParseException e) {
          logger.log(Level.WARNING, "Error", e);
        }
      }
    });
  }

  private void attemptTripDrop(
      Schedule schedule,
      GridObservation fromGrid,
      GridObservation toGrid,
      Trip dropTrip) throws URISyntaxException, IOException, ParseException {
    logger.info("Attempting to drop trip " + dropTrip.getPairingName());
    Set<Trip> callScheduling = new HashSet<>();
    Map<Trip, TripScore> eligibleAdds = getEligibleAdds(schedule, dropTrip,
        callScheduling);
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
      if (callScheduling.contains(addTrip)) {
        logger.info("CALL SCHEDULING: drop " + dropKey + ", add " + addKey);
        notifier.notify("CALL SCHEDULING",
            "Drop " + dropKey + ", add " + addKey + "\n");
      } else {
        notifier.notify("TRY FLICA", "SWAP: drop " + dropKey + ", add " + addKey + "\n");
        if (false) {
          flicaService.submitSwap(FlicaService.BID_FIRST_COME, yearMonth,
              clock.today(), ImmutableList.of(addKey), ImmutableList.of(dropKey));
        } else {
          logger.info("SWAP: drop " + dropKey + ", add " + addKey);
        }
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

  private Map<Trip, TripScore> getEligibleAdds(
      Schedule schedule, Trip dropTrip, Set<Trip> callScheduling)
      throws URISyntaxException, IOException, ParseException {
    Period creditWithoutDropTrip = schedule
        .getCreditInMonth().minus(dropTrip.getCredit());

    // Constructs a reduced schedule.
    Set<PairingKey> retainedKeys = new HashSet<>(schedule.getTrips().keySet());
    retainedKeys.remove(dropTrip.getPairingKey());
    ReducedSchedule reducedSchedule = new ReducedSchedule(
        schedule, retainedKeys, bidConfig);

    GridEvaluator gridEvaluator = new GridEvaluator(yearMonth, fromGrid, toGrid);

    Map<Trip, TripScore> result = new HashMap<>();
    for (FlicaTask bareTask : getOpentimeTrips()) {
      FlicaTaskWrapper task = new FlicaTaskWrapper(bareTask);
      Trip addTrip = tripDatabase.getTrip(task.getPairingKey());

      if (task.isTwoHundred()) {
        continue;
      }

      OverlapEvaluator evaluator = new OverlapEvaluator(
          reducedSchedule, bidConfig);
      OverlapEvaluation eval = evaluator.evaluate(addTrip);
      switch (eval.overlap) {
        case UNDROPPABLE:
          logger.info("Considered " + addTrip.getPairingName()
              + " but it overlaps something undroppable");
          continue;
        case DAY_OFF:
          logger.info("Considered " + addTrip.getPairingName()
              + " but it overlaps a day off");
          continue;
        case RETAINED_TRIP:
          if (eval.overlappedTrips.size() == 1 &&
              eval.overlappedTrips.iterator().next().equals(dropTrip)) {
            // overlaps dropped trip. OK to consider.
            break;
          } else {
            Preconditions.checkState(!eval.overlappedTrips.isEmpty());
            if (!eval.overlappedTrips.contains(dropTrip)) {
              // overlaps another trip. Wait until that trip is considered.
              continue;
            }
            Set<String> overlappedTrips = new HashSet<>();
            eval.overlappedTrips.forEach(t -> overlappedTrips.add(t.getPairingName()));
            logger.warning("TODO: implement overlap of non-drop trips "
                + overlappedTrips + " by " + addTrip.getPairingName());
            continue;
          }
        case NO_OVERLAP:
          break;
      }

      // Checks credit.
      Period newCredit = creditWithoutDropTrip.plus(addTrip.getCredit());
      if (newCredit.isLessThan(MIN_CREDIT)) {
        logger.info("Considered " + addTrip.getPairingName()
            + " but the new credit would be less than min credit at " + newCredit);
        continue;
      }

      // Checks reserve grid.
      GridEvaluation gridEval = gridEvaluator.evaluate(
          dropTrip.getDepartureDates(),
          addTrip.getDepartureDates());
      if (gridEval.swappable) {
        result.put(addTrip, new TripScore(addTrip, bidConfig));
      }
      if (gridEval.requiresCrewScheduling) {
        callScheduling.add(addTrip);
      }
    }
    return Collections.sortByValueDescending(result);
  }
}
