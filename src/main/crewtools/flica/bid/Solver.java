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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

import crewtools.flica.bid.OverlapEvaluator.OverlapEvaluation.Overlap;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Calendar;
import crewtools.util.Clock;
import crewtools.util.Collections;

public class Solver {
  private final Logger logger = Logger.getLogger(Solver.class.getName());

  public static final int MAX_POWERSET_INPUT_SIZE = 30;

  private final Schedule schedule;
  private final Collection<FlicaTaskWrapper> tasks;
  private final YearMonth yearMonth;
  private final BidConfig bidConfig;
  private final Calendar calendar;
  private final TripDatabase tripDatabase;
  private final int originalScore;
  private final ScheduleFilter scheduleFilter;

  public Solver(Schedule schedule, Collection<FlicaTaskWrapper> tasks,
      YearMonth yearMonth, BidConfig bidConfig, TripDatabase tripDatabase,
      Clock clock) {
    this.schedule = schedule;
    this.tasks = tasks;
    this.yearMonth = yearMonth;
    this.bidConfig = bidConfig;
    this.calendar = new Calendar(yearMonth);
    this.tripDatabase = tripDatabase;
    int originalScore = 0;
    for (Trip trip : schedule.getTrips().values()) {
      originalScore += new TripScore(trip, bidConfig).getPoints();
    }
    this.originalScore = originalScore;
    this.scheduleFilter = new ScheduleFilter(schedule, clock);
  }

  public List<Solution> solve() throws ParseException, IOException, URISyntaxException {
    List<Solution> solutions = new ArrayList<>();

    Iterator<Set<PairingKey>> retainedTripsSet = Sets
        .powerSet(schedule.getTripCreditInMonth().keySet())
        .stream()
        .filter(scheduleFilter)
        .iterator();

    int count = 1;
    while (retainedTripsSet.hasNext()) {
      Set<PairingKey> retainedTrips = retainedTripsSet.next();
      if (logger.isLoggable(Level.FINE)) {
        logger.fine(
            "Considering schedule combination " + count + ": " + retainedTrips);
      }
      count++;
      ReducedSchedule reducedSchedule = new ReducedSchedule(schedule, retainedTrips, bidConfig);
      enumerateSolutions(solutions, reducedSchedule);
    }
    logger.info("Considered " + count + " schedule combinations x "
        + tasks.size() + " tasks");
    return solutions;
  }

  private void enumerateSolutions(List<Solution> solutions,
      ReducedSchedule reducedSchedule)
      throws ParseException, IOException, URISyntaxException {
    OverlapEvaluator evaluator = new OverlapEvaluator(
        reducedSchedule, yearMonth, bidConfig);
    // Find all opentime trips which could possibly be added.
    Set<FlicaTaskWrapper> candidateTasks = new HashSet<>();
    Set<LocalDate> dates = new HashSet<>();
    for (FlicaTaskWrapper task : tasks) {
      if (!bidConfig.getEnableAllowTwoHundredTrips() && task.isTwoHundred()) {
        logger.fine(".. ignoring 200 trip " + task.getPairingName());
        continue;
      }
      if (!checkTaskDates(task.getPairingName(), dates, task.getTaskDates())) {
        continue;
      }
      if (evaluator.evaluate(task.getTaskDates()).overlap != Overlap.NO_OVERLAP) {
        logger.fine(".. ignoring " + task.getPairingName() + " due to overlap");
        continue;
      }

      if (bidConfig.getEnableMultipleAdds()) {
        candidateTasks.add(task);
        // now, these may overlap with each other but not with
        // trips retained on the schedule.
      } else {
        evaluateTasks(solutions, reducedSchedule, ImmutableSet.of(task), evaluator);
      }
    }

    if (!bidConfig.getEnableMultipleAdds()) {
      return;
    }

    // PowerSet requires <= 30 elements.
    if (candidateTasks.size() > MAX_POWERSET_INPUT_SIZE) {
      logger.info("WARNING: Paring down candidate set due to too many inputs");
      candidateTasks = selectBestCandidates(candidateTasks);
    }

    for (Set<FlicaTaskWrapper> taskCombination : Sets.powerSet(candidateTasks)) {
      if (taskCombination.isEmpty()) {
        continue;
      }
      evaluateTasks(solutions, reducedSchedule, taskCombination, evaluator);
    }
  }

  private void evaluateTasks(List<Solution> solutions,
      ReducedSchedule reducedSchedule,
      Set<FlicaTaskWrapper> taskCombinations,
      OverlapEvaluator evaluator) {
    ProposedSchedule schedule = new ProposedSchedule(reducedSchedule, taskCombinations);
    if (schedule.isValid(evaluator)) {
      Solution solution = new Solution(schedule, tripDatabase, bidConfig);
      int numAdds = solution.getProposedSchedule().getAddedKeys().size();
      int numDrops = solution.getProposedSchedule().getReducedSchedule().getDropKeys()
          .size();
      if (numAdds > numDrops) {
        // Avoid a 4-day turning into two 2-days.
        return;
      }

      boolean workLess = schedule.getNumWorkingDays() < reducedSchedule
          .getOriginalNumWorkingDays();
      boolean workSame = schedule.getNumWorkingDays() == reducedSchedule
          .getOriginalNumWorkingDays();
      boolean betterSchedule = solution.getScore() > originalScore;
      logger.fine(schedule.getTransition()
          + " workLess: " + workLess
          + " workSame: " + workSame
          + " betterSchedule: " + betterSchedule
          + " (" + solution.getScore() + " vs " + originalScore + ")");
      if (workLess || (workSame && betterSchedule)) {
        solutions.add(solution);
      }
    }
  }

  private boolean checkTaskDates(String pairingName, Set<LocalDate> existingDates,
      Set<LocalDate> proposedDates) {
    for (LocalDate date : proposedDates) {
      if (!existingDates.add(date)) {
        logger.fine(".. ignoring " + pairingName + " due to taks overlap");
        return false;
      }
      if (!calendar.isWithinPeriod(date)) {
        logger.fine(".. ignoring " + pairingName + " due to blend");
        return false;
      }
    }
    return true;
  }

  Set<FlicaTaskWrapper> selectBestCandidates(Set<FlicaTaskWrapper> candidates)
      throws URISyntaxException, IOException, ParseException {
    logger.info("Whittling down " + candidates.size() + " candidates based on score.");
    Map<Trip, FlicaTaskWrapper> trips = new HashMap<>();
    Map<Trip, TripScore> scores = new HashMap<>();
    for (FlicaTaskWrapper task : candidates) {
      Trip trip = tripDatabase.getTrip(task.getPairingKey());
      trips.put(trip, task);
      scores.put(trip, new TripScore(trip, bidConfig));
    }
    scores = Collections.sortByValueDescending(scores);
    Iterator<Trip> it = Iterators.limit(scores.keySet().iterator(),
        MAX_POWERSET_INPUT_SIZE);
    Set<FlicaTaskWrapper> result = new HashSet<>();
    while (it.hasNext()) {
      result.add(trips.get(it.next()));
    }
    return result;
  }
}
