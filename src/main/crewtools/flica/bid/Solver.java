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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.collect.Sets;

import crewtools.flica.bid.OverlapEvaluator.OverlapEvaluation.Overlap;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Calendar;
import crewtools.util.Clock;

public class Solver {
  private final Logger logger = Logger.getLogger(Solver.class.getName());

  private final Schedule schedule;
  private final Collection<FlicaTask> tasks;
  private final YearMonth yearMonth;
  private final BidConfig bidConfig;
  private final Calendar calendar;
  private final TripDatabase tripDatabase;
  private final int originalScore;
  private final Clock clock;
  private final ScheduleFilter scheduleFilter;

  public Solver(Schedule schedule, Collection<FlicaTask> tasks,
      YearMonth yearMonth, BidConfig bidConfig, TripDatabase tripDatabase, Clock clock) {
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
    this.clock = clock;
    this.scheduleFilter = new ScheduleFilter(schedule, clock);
  }

  public List<Solution> solve() {
    List<Solution> solutions = new ArrayList<>();

    // @formatter:off
    Iterator<Set<PairingKey>> retainedTripsSet = Sets
        .powerSet(schedule.getTripCreditInMonth().keySet())
        .stream()
        .filter(scheduleFilter)
        .iterator();
    // @formatter:on

    int count = 1;
    while (retainedTripsSet.hasNext()) {
      Set<PairingKey> retainedTrips = retainedTripsSet.next();
      logger.fine(
          "Considering schedule combination " + count++ + ": " + retainedTrips);
      ReducedSchedule reducedSchedule = new ReducedSchedule(schedule, retainedTrips, bidConfig);
      enumerateSolutions(solutions, reducedSchedule);
    }
    return solutions;
  }

  private void enumerateSolutions(List<Solution> solutions,
      ReducedSchedule reducedSchedule) {
    OverlapEvaluator evaluator = new OverlapEvaluator(
        reducedSchedule, yearMonth, bidConfig);
    // Find all opentime trips which could possibly be added.
    Set<FlicaTask> candidateTasks = new HashSet<>();
    for (FlicaTask task : tasks) {
      Set<LocalDate> taskDates = getTaskDates(task);
      if (evaluator.evaluate(taskDates).overlap != Overlap.NO_OVERLAP) {
        logger.fine(".. ignoring " + task.pairingName + " due to overlap");
        continue;
      }
      for (LocalDate date : taskDates) {
        if (!calendar.isWithinPeriod(date)) {
          logger.fine(".. ignoring " + task.pairingName + " due to blend");
          continue;
        }
      }
      candidateTasks.add(task);
      // now, these may overlap with each other but not with
      // trips retained on the schedule.
    }

    for (Set<FlicaTask> taskCombination : Sets.powerSet(candidateTasks)) {
      if (taskCombination.isEmpty()) {
        continue;
      }
      ProposedSchedule schedule = new ProposedSchedule(reducedSchedule, taskCombination);
      if (schedule.isValid(evaluator)) {
        Solution solution = new Solution(schedule, tripDatabase, bidConfig);
        boolean workLess = schedule.getNumWorkingDays() < reducedSchedule.getOriginalNumWorkingDays();
        boolean workSame = schedule.getNumWorkingDays() == reducedSchedule.getOriginalNumWorkingDays();
        boolean betterSchedule = solution.getScore() > originalScore;
        // @formatter:off
        logger.fine("workLess: " + workLess
            + " workSame: " + workSame + " betterSchedule: " + betterSchedule);
        // @formatter:on
        if (workLess || (workSame && betterSchedule)) {
          solutions.add(solution);
        }
      }
    }
  }

  Set<LocalDate> getTaskDates(FlicaTask task) {
     Set<LocalDate> dates = new HashSet<>();
     LocalDate startDate = task.pairingDate;
     dates.add(startDate);
     for (int i = 1; i < task.numDays; ++i) {
       dates.add(startDate.plusDays(i));
     }
     return dates;
   }
}
