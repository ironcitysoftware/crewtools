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

package crewtools.flica.bid;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import crewtools.flica.FlicaService;
import crewtools.flica.bid.ScheduleWrapper.OverlapEvaluation;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.SwapResponseParser;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;
import crewtools.util.Period;

public class Worker implements Runnable {
  private final Logger logger = Logger.getLogger(Worker.class.getName());

  private final BidConfig bidConfig;
  private final YearMonth yearMonth;
  private final List<FlicaTask> tasks;
  private final ScheduleWrapperTree tree;
  private final FlicaService service;
  private final int round;
  private final Clock clock;
  private final TripDatabase tripDatabase;
  private final List<String> swaps = new ArrayList<>();
  private Duration opentimeRefreshInterval = Duration.standardMinutes(6);

  public Worker(BidConfig bidConfig, YearMonth yearMonth, ScheduleWrapperTree tree,
      List<FlicaTask> tasks, FlicaService service, int round, Clock clock,
      TripDatabase tripDatabase) {
    this.bidConfig = bidConfig;
    this.yearMonth = yearMonth;
    this.tree = tree;
    this.tasks = tasks;
    this.service = service;
    this.round = round;
    this.clock = clock;
    this.tripDatabase = tripDatabase;
  }

  private class TripAndScore implements Comparable<TripAndScore> {
    private Trip trip;
    private TripScore score;

    public TripAndScore(Trip trip) {
      this.trip = trip;
      this.score = new TripScore(trip, bidConfig);
    }

    @Override
    public int compareTo(TripAndScore that) {
      return -(new Integer(score.getPoints())
          .compareTo(new Integer(that.score.getPoints())));
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || (!(o instanceof TripAndScore))) {
        return false;
      }
      TripAndScore that = (TripAndScore) o;
      return trip.equals(that.trip);
    }

    @Override
    public int hashCode() {
      return trip.hashCode();
    }
  }

  public Duration getOpentimeRefreshInterval() {
    return opentimeRefreshInterval;
  }

  boolean firstRun = true;

  @Override
  public void run() {
    logger.info("------------------ Worker run -----------------------");
    ScheduleWrapper schedule = tree.getRoot();

    logger.info("Considering " + tasks.size() + " tasks in opentime");
    // Drop trips that overlap vaca and rlds
    int numRemoved = 0;
    ListIterator<FlicaTask> it = tasks.listIterator();
    while (it.hasNext()) {
      FlicaTask task = it.next();
      OverlapEvaluation eval = schedule.evaluateOverlap(task);
      if (eval.overlapsUndroppable) {
        it.remove();
        numRemoved++;
      }
    }
    logger.info("Removed " + numRemoved + " tasks due to overlap");

    // Order the available trips by score
    List<TripAndScore> trips = new ArrayList<>();
    tasks.forEach(task -> {
      PairingKey key = new PairingKey(task.pairingDate, task.pairingName);
      try {
        Trip trip = tripDatabase.getTrip(key);
        trips.add(new TripAndScore(trip));
      } catch (URISyntaxException | IOException | ParseException e) {
        logger.log(Level.WARNING, "FAIL to parse trip " + key, e);
      }
    });
    Collections.sort(trips);
    logger.info("Scored, sorted trips from opentime:");
    for (TripAndScore tas : trips) {
      logger.info("    "
          + tas.trip.getPairingName()
          + " / " + tas.score.getPoints()
          + " / " + tas.trip.getCreditInMonth(yearMonth));
    }

    // What credit do we need again?
    Period neededCredit = Period.hours(65)
        .minus(schedule.getSchedule().getNonTripCreditInMonth());
    // .minus(schedule.getNonDroppableTripCredit());
    logger.info("Minimum total trip credit is " + neededCredit);

    if (schedule.getAllDroppable().size() == 3) {
      int numSwapsIssued = submitSwapsToDropExtraTrips(trips, neededCredit, schedule);
      if (numSwapsIssued < 5 && schedule.getAllDroppable().size() == 3) {
        submitPanicSwaps(trips, neededCredit, schedule);
      }
    } else if (schedule.getAllDroppable().size() == 2) {
      submitBetterSwaps(trips, neededCredit, schedule);
      opentimeRefreshInterval = Duration.standardMinutes(10);
    }

    tasks.clear();
  }

  private int submitSwapsToDropExtraTrips(List<TripAndScore> trips, Period neededCredit,
      ScheduleWrapper schedule) {
    logger.info(
        "** Issuing swaps to consolidate 3 trips to 2, regardless of best score **");
    int numSwapsIssued = 0;
    List<PairingKey> droppableTrips = new ArrayList<>();
    schedule.getAllDroppable().forEach(t -> droppableTrips.add(t.getPairingKey()));
    logger.info("We intend to drop the following trips: " + droppableTrips);

    for (int i = 0; i < trips.size() - 1; ++i) {
      Trip a = trips.get(i).trip;
      Trip b = trips.get(i + 1).trip;
      Period sum = a.getCreditInMonth(yearMonth).plus(b.getCreditInMonth(yearMonth));
      if (sum.isLessThan(neededCredit)) {
        logger.info("Skipping " + a.getPairingName() + " and " + b.getPairingName()
            + " due to minCredit (" + sum + " < " + neededCredit + ")");
        continue;
      }
      List<PairingKey> adds = ImmutableList.of(a.getPairingKey(), b.getPairingKey());
      if (swap(adds, droppableTrips)) {
        numSwapsIssued++;
      }
    }
    return numSwapsIssued;
  }

  private void submitPanicSwaps(List<TripAndScore> trips, Period neededCredit,
      ScheduleWrapper schedule) {
    logger.info(
        "** Issuing PANIC swaps to consolidate 2 trips to 1, regardless of best score **");

    // Get a sorted list of droppable trips by credit, this will be needed later.
    Map<Trip, Period> droppableSchedule = new HashMap<>();
    for (Trip trip : schedule.getAllDroppable()) {
      droppableSchedule.put(trip, trip.getCreditInMonth(yearMonth));
    }
    droppableSchedule = crewtools.util.Collections
        .sortByValueAscending(droppableSchedule);
    List<Trip> droppableTrips = new ArrayList<>(droppableSchedule.keySet());
    logger.info("  Droppable trips, sorted:");
    for (Trip trip : droppableTrips) {
      logger.info("    " + trip.getPairingKey() + " / " + droppableSchedule.get(trip));
    }

    for (int i = 0; i < trips.size(); ++i) {
      Trip trip = trips.get(i).trip;
      Period credit = trip.getCreditInMonth(yearMonth);

      logger.info("  Considering trip " + trip.getPairingName());
      // Figure out which trips we have to drop.
      OverlapEvaluation eval = schedule.evaluateOverlap(trip);
      switch (eval.droppable.size()) {
        case 1: {
          // We must drop one trip specifically, but we want to drop another, too.
          // Pick the lowest-credit other trip to drop.
          Trip mustDrop = eval.droppable.iterator().next();
          Trip otherDrop = droppableTrips.get(0).equals(mustDrop)
              ? droppableTrips.get(1)
              : droppableTrips.get(0);
          logger.info("    Overlap 1.  MustDrop=" + mustDrop.getPairingName()
              + ", otherDrop=" + otherDrop.getPairingName());
          Period retainCredit = getCreditExcept(droppableSchedule,
              ImmutableList.of(mustDrop, otherDrop));
          Period newCredit = credit.plus(retainCredit);
          if (newCredit.isLessThan(neededCredit)) {
            logger.info("    Credit of retained trip " + retainCredit
                + " plus this trip " + credit + " = " + newCredit + " < " + neededCredit);
            continue;
          } else {
            List<PairingKey> drops = new ArrayList<>();
            drops.add(mustDrop.getPairingKey());
            drops.add(otherDrop.getPairingKey());
            swap(ImmutableList.of(trip.getPairingKey()), drops);
          }
          break;
        }
        case 2: {
          // We must drop two trips specifically.
          Period retainCredit = getCreditExcept(droppableSchedule, eval.droppable);
          Period newCredit = credit.plus(retainCredit);
          if (newCredit.isLessThan(neededCredit)) {
            logger.info("  Overlaps 2, but credit of retained trip " + retainCredit
                + " plus this trip " + credit + " = " + newCredit + " < " + neededCredit);
            continue;
          } else {
            List<PairingKey> drops = new ArrayList<>();
            eval.droppable.forEach(t -> drops.add(t.getPairingKey()));
            swap(ImmutableList.of(trip.getPairingKey()), drops);
          }
          break;
        }
        case 3: {
          // Any of the schedule trips are droppable; keep the one highest credit.
          Trip retain = droppableTrips.get(droppableTrips.size() - 1);
          Period retainCredit = droppableSchedule.get(retain);
          Period newCredit = credit.plus(retainCredit);
          if (newCredit.isLessThan(neededCredit)) {
            logger.info("  No overlap, but credit of retained trip " + retainCredit
                + " plus this trip " + credit + " = " + newCredit + " < " + neededCredit);
            continue;
          } else {
            swap(ImmutableList.of(trip.getPairingKey()),
                ImmutableList.of(
                    droppableTrips.get(0).getPairingKey(),
                    droppableTrips.get(1).getPairingKey()));
          }
          break;
        }
        default:
          logger.warning("ERROR: unexpected eval size for " + trip);
          continue;
      }
    }
  }

  private void submitBetterSwaps(List<TripAndScore> trips, Period neededCredit,
      ScheduleWrapper schedule) {
    logger.info(
        "** Issuing BETTER swaps to replace single trips.");
    for (int i = 0; i < trips.size(); ++i) {
      Trip addTrip = trips.get(i).trip;
      Period credit = addTrip.getCreditInMonth(yearMonth);
      TripScore addScore = new TripScore(addTrip, bidConfig);

      logger.info("  Considering trip " + addTrip.getPairingName());
      // Figure out which trips we have to drop.
      OverlapEvaluation eval = schedule.evaluateOverlap(addTrip);
      if (eval.droppable.size() == 1) {
        Trip dropTrip = eval.droppable.iterator().next();
        TripScore dropScore = new TripScore(dropTrip, bidConfig);
        if (addScore.getPoints() > dropScore.getPoints()) {
          Trip retainTrip = getExcept(schedule.getAllDroppable(), dropTrip);
          Period retainCredit = retainTrip.getCreditInMonth(yearMonth);
          Period newCredit = credit.plus(retainCredit);
          if (newCredit.isLessThan(neededCredit)) {
            logger.info("    Better trip, but credit of retained trip " + retainCredit
                + " plus this trip " + credit + " = " + newCredit + " < " + neededCredit);
            continue;
          } else {
            swap(ImmutableList.of(addTrip.getPairingKey()),
                ImmutableList.of(dropTrip.getPairingKey()));
          }
        }
      } else {
        // Doesn't overlap with any existing trips. See if it scores
        // better than the lowest-scoring trip we have.
        List<TripAndScore> scores = new ArrayList<>();
        for (Trip trip : eval.droppable) {
          scores.add(new TripAndScore(trip));
        }
        Collections.sort(scores);
        if (addScore.getPoints() > scores.get(0).score.getPoints()) {
          Trip dropTrip = scores.get(0).trip;
          Map<Trip, Period> droppableSchedule = new HashMap<>();
          for (Trip trip : schedule.getAllDroppable()) {
            droppableSchedule.put(trip, trip.getCreditInMonth(yearMonth));
          }
          droppableSchedule = crewtools.util.Collections
              .sortByValueAscending(droppableSchedule);
          Period retainCredit = getCreditExcept(droppableSchedule,
              ImmutableList.of(dropTrip));
          Period newCredit = credit.plus(retainCredit);
          if (newCredit.isLessThan(neededCredit)) {
            logger.info("    Better trip, but credit of retained trips " + retainCredit
                + " plus this trip " + credit + " = " + newCredit + " < " + neededCredit);
            continue;
          } else {
            swap(ImmutableList.of(addTrip.getPairingKey()),
                ImmutableList.of(dropTrip.getPairingKey()));
          }
        }
      }
    }
  }

  private Period getCreditExcept(Map<Trip, Period> credits, Collection<Trip> excepts) {
    Period period = Period.ZERO;
    for (Trip trip : credits.keySet()) {
      if (!excepts.contains(trip)) {
        period = period.plus(credits.get(trip));
      }
    }
    return period;
  }

  private Trip getExcept(Collection<Trip> trips, Trip except) {
    for (Trip trip : trips) {
      if (trip.equals(except)) {
        continue;
      }
      return trip;
    }
    throw new IllegalStateException("Whoa there partner");
  }

  private boolean swap(List<PairingKey> adds, List<PairingKey> drops) {
    String addStr = Joiner.on(",").join(adds);
    String dropStr = Joiner.on(",").join(drops);
    String key = dropStr + "->" + addStr;
    if (swaps.contains(key)) {
      return false;
    }
    swaps.add(key);
    logger.info("SWAP!!!! DROP " + drops + " for " + adds);
    try {
      String html = service.submitSwap(round, yearMonth, clock.today(), adds,
          drops);
      logger.info("Result from SWAP: " + html);
      SwapResponseParser swapResponseParser = new SwapResponseParser(html);
      if (swapResponseParser.parse() == SwapResponseParser.Status.DUPLICATE) {
        logger.info("Ignoring duplicate swap request");
        return false;
      }
      return true;
    } catch (IOException | URISyntaxException ioe) {
      logger.log(Level.INFO, "Error swapping", ioe);
    }
    return false;
  }
}
