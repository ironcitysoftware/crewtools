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
import java.util.Collections;
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

import com.google.common.collect.Sets;

import crewtools.flica.bid.OverlapEvaluator.OverlapEvaluation.Overlap;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Calendar;
import crewtools.util.Period;

public class TripMinimizer {
  private final Logger logger = Logger.getLogger(TripMinimizer.class.getName());

  private final Schedule schedule;
  private final Collection<FlicaTask> tasks;
  private final YearMonth yearMonth;
  private final BidConfig bidConfig;
  private final Calendar calendar;
  private final TripDatabase tripDatabase;
  private final int originalScore;

  public TripMinimizer(Schedule schedule, Collection<FlicaTask> tasks,
      YearMonth yearMonth, BidConfig bidConfig, TripDatabase tripDatabase) {
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
  }

  public Map<Transition, Integer> minimizeNumberOfTrips() {
    Map<Transition, Integer> solutions = new HashMap<>();

    Set<Set<PairingKey>> retainedTripsSet = Sets.powerSet(
        schedule.getTripCreditInMonth().keySet());

    for (Set<PairingKey> retainedTrips : retainedTripsSet) {
      ReducedSchedule reducedSchedule = new ReducedSchedule(schedule, retainedTrips, bidConfig);
      minimizeNumberOfTrips(solutions, reducedSchedule);
    }

    return solutions;
  }

  private void minimizeNumberOfTrips(
      Map<Transition, Integer> solutions,
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
      if (isValid(evaluator, reducedSchedule, taskCombination)) {
        Set<PairingKey> addKeys = getPairingKeys(taskCombination);
        Transition transition = new Transition(addKeys, reducedSchedule.getDropKeys());
        scoreAndMaybeRecordSolution(solutions, transition, reducedSchedule);
      }
    }
  }

  private void scoreAndMaybeRecordSolution(
      Map<Transition, Integer> solutions, Transition transition,
      ReducedSchedule reducedSchedule) {
    int score = reducedSchedule.getScore();
    int numWorkingDays = reducedSchedule.getNumWorkingDays();
    for (PairingKey addKey : transition.getAddKeys()) {
      try {
        Trip trip = tripDatabase.getTrip(addKey);
        TripScore tripScore = new TripScore(trip, bidConfig);
        score += tripScore.getPoints();
        numWorkingDays += trip.getDepartureDates().size();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Error getting trip " + addKey, e);
        return;
      }
    }
    // secret sauce!
    boolean workLess = reducedSchedule.getOriginalNumWorkingDays() < numWorkingDays;
    boolean workSame = reducedSchedule.getOriginalNumWorkingDays() == numWorkingDays;
    boolean betterSchedule = score > originalScore;
    if (workLess || (workSame && betterSchedule)) {
      solutions.put(transition, score);
    }
  }

  private Set<PairingKey> getPairingKeys(Set<FlicaTask> tasks) {
    Set<PairingKey> keys = new HashSet<>();
    for (FlicaTask task : tasks) {
      keys.add(new PairingKey(task.pairingDate, task.pairingName));
    }
    return keys;
  }

  private static final Period SIXTY_FIVE = Period.hours(65);
  private static final int MAX_CONSECUTIVE = 5;

  // Careful.  Called product of power sets.
  private boolean isValid(OverlapEvaluator evaluator,
      ReducedSchedule reducedSchedule, Set<FlicaTask> tasks) {
    // CHECK: check that we have 65.
    Period credit = reducedSchedule.getCredit();
    for (FlicaTask task : tasks) {
      credit = credit.plus(task.creditTime);
    }
    if (credit.isLessThan(SIXTY_FIVE)) {
      return false;
    }

    // CHECK: check that the task combination doesn't overlap.
    int numTaskWorkingDays = 0;
    Set<LocalDate> allDates = new HashSet<>();
    for (FlicaTask task : tasks) {
      Set<LocalDate> taskDates = getTaskDates(task);
      numTaskWorkingDays += taskDates.size();
      allDates.addAll(taskDates);
    }
    if (allDates.size() != numTaskWorkingDays) {
      // Tasks overlap with each other.  Forget this combination.
      return false;
    }
    if (exceedsConsecutive(MAX_CONSECUTIVE, allDates)) {
      return false;
    }

    // CHECK: check that we are not working more in this combination.
    if ((numTaskWorkingDays + reducedSchedule.getNumWorkingDays())
        > reducedSchedule.getOriginalNumWorkingDays()) {
      // Working more than the original schedule.  Forget this combination.
      return false;
    }
    return true;
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

  boolean exceedsConsecutive(int limit, Set<LocalDate> dates) {
    if (dates.size() <= limit) {
      return false;
    }
    List<LocalDate> orderedDates = new ArrayList<>(dates);
    Collections.sort(orderedDates);
    Iterator<LocalDate> it = orderedDates.iterator();
    LocalDate currentDate = it.next();
    LocalDate endDate = orderedDates.get(orderedDates.size() - 1);
    int numConsecutive = 0;
    while (!currentDate.isAfter(endDate)) {
      if (++numConsecutive > limit) {
        return true;
      }
      currentDate = currentDate.plusDays(1);
      if (dates.contains(currentDate)) {
        continue;
      } else {
        if (!it.hasNext()) {
          return false;
        }
        currentDate = it.next();
        numConsecutive = 0;
      }
    }
    return false;
  }
}
