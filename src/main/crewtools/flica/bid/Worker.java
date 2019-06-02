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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import com.google.common.base.Joiner;

import crewtools.flica.FlicaService;
import crewtools.flica.parser.SwapResponseParser;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;
import crewtools.util.Period;

public class Worker implements Runnable {
  private final Logger logger = Logger.getLogger(Worker.class.getName());

  private final BidConfig bidConfig;
  private final YearMonth yearMonth;
  private final FlicaService service;
  private final Clock clock;
  private final TripDatabase tripDatabase;
  private final List<String> swaps = new ArrayList<>();
  private final Collector collector;
  private Duration opentimeRefreshInterval = Duration.standardMinutes(6);

  public Worker(BidConfig bidConfig, YearMonth yearMonth, Collector collector,
      FlicaService service, Clock clock, TripDatabase tripDatabase) {
    this.bidConfig = bidConfig;
    this.yearMonth = yearMonth;
    this.collector = collector;
    this.service = service;
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

  private static final int MAX_SWAPS_PER_RUN = 10;

  @Override
  public void run() {
    collector.beginWork();
    logger.info("------------------ Worker run -----------------------");
    Schedule schedule = collector.getCurrentSchedule();

    int numTrips = schedule.getTripCreditInMonth().size();
    if (numTrips > 3) {
      logger.info("| Mode: minimize number of trips (currently " + numTrips + ")");
      TripMinimizer minimizer = new TripMinimizer(
          schedule, collector.getCurrentTasks(), yearMonth, bidConfig, tripDatabase);
      Map<Transition, Integer> solutions =
          crewtools.util.Collections.sortByValueDescending(
              minimizer.minimizeNumberOfTrips());
      logger.info("| Found " + solutions.size() + " solutions");
      int count = 0;
      for (Transition transition : solutions.keySet()) {
        if (count++ > MAX_SWAPS_PER_RUN) {
          break;
        }
        swap(transition);
      }
    } else {
      logger.info("| Mode: maximize quality of trips");
      opentimeRefreshInterval = Duration.standardMinutes(10);
      //maximizeQualityOfTrips(schedule);
    }
  }

  /*
  private void submitBetterSwaps(List<TripAndScore> trips, Period neededCredit,
      ScheduleWrapper schedule) {
    logger.info(
        "** Issuing BETTER swaps to replace single trips.");
    for (int i = 0; i < trips.size(); ++i) {
      Trip addTrip = trips.get(i).trip;
      Period credit = addTrip.getCredit();
      TripScore addScore = new TripScore(addTrip, bidConfig);

      logger.info("  Considering trip " + addTrip.getPairingName());
      // Figure out which trips we have to drop.
      OverlapEvaluation eval = schedule.evaluateOverlap(addTrip);
      if (eval.droppable.size() == 1) {
        Trip dropTrip = eval.droppable.iterator().next();
        TripScore dropScore = new TripScore(dropTrip, bidConfig);
        if (addScore.getPoints() > dropScore.getPoints()) {
          Trip retainTrip = getExcept(schedule.getAllDroppable(), dropTrip);
          Period retainCredit = retainTrip.getCredit();
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
            droppableSchedule.put(trip, trip.getCredit());
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
  */

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

  private boolean swap(Transition transition) {
    return swap(transition.getAddKeys(), transition.getDropKeys());
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
      String html = service.submitSwap(bidConfig.getRound(), yearMonth, clock.today(), adds,
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
