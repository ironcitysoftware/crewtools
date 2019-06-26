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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.LocalDate;

import crewtools.flica.pojo.PairingKey;
import crewtools.util.Period;

public class ProposedSchedule {
  private final Logger logger = Logger.getLogger(ProposedSchedule.class.getName());

  private final ReducedSchedule reducedSchedule;
  private final Set<FlicaTaskWrapper> tasks;
  private final int numWorkingDays;

  public ProposedSchedule(ReducedSchedule reducedSchedule, Set<FlicaTaskWrapper> tasks) {
    this.reducedSchedule = reducedSchedule;
    this.tasks = tasks;
    int numWorkingDays = 0;
    for (FlicaTaskWrapper task : tasks) {
      numWorkingDays += task.getNumDays();
    }
    this.numWorkingDays = numWorkingDays;
  }

  public Set<PairingKey> getAddedKeys() {
    Set<PairingKey> keys = new HashSet<>();
    for (FlicaTaskWrapper task : tasks) {
      keys.add(task.getPairingKey());
    }
    return keys;
  }

  public Transition getTransition() {
    return new Transition(getAddedKeys(), reducedSchedule.getDropKeys());
  }

  public ReducedSchedule getReducedSchedule() {
    return reducedSchedule;
  }

  public int getNumWorkingDays() {
    return reducedSchedule.getNumWorkingDays() + numWorkingDays;
  }

  private static final Period SIXTY_FIVE = Period.hours(65);
  private static final int MAX_CONSECUTIVE = 5;

  // Careful. Called product of power sets.
  public boolean isValid(OverlapEvaluator evaluator) {
    // CHECK: check that we have 65.
    Period credit = reducedSchedule.getCredit();
    for (FlicaTaskWrapper task : tasks) {
      credit = credit.plus(task.getCredit());
    }
    if (credit.isLessThan(SIXTY_FIVE)) {
      logger.fine("Invalid: credit is " + credit);
      return false;
    }

    // CHECK: check that the task combination doesn't overlap.
    int numTaskWorkingDays = 0;
    Set<LocalDate> allDates = new HashSet<>();
    for (FlicaTaskWrapper task : tasks) {
      numTaskWorkingDays += task.getNumDays();
      allDates.addAll(task.getTaskDates());
    }
    if (allDates.size() != numTaskWorkingDays) {
      // Tasks overlap with each other. Forget this combination.
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("Invalid: task overlap (allDates=" + allDates + "; "
            + "numTaskWorkingDays=" + numTaskWorkingDays);
      }
      return false;
    }
    if (exceedsConsecutive(MAX_CONSECUTIVE, allDates)) {
      logger.fine("Invalid: exceeds consecutive");
      return false;
    }

    // CHECK: check that we are not working more in this combination.
    if ((numTaskWorkingDays + reducedSchedule.getNumWorkingDays()) > reducedSchedule.getOriginalNumWorkingDays()) {
      // Working more than the original schedule. Forget this combination.
      logger.fine("Invalid: work more");
      return false;
    }
    return true;
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

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof ProposedSchedule)) {
      return false;
    }
    ProposedSchedule that = (ProposedSchedule) o;
    // @formatter:off
    return Objects.equals(reducedSchedule, that.reducedSchedule)
        && Objects.equals(tasks, that.tasks);
    // @formatter:on
  }

  @Override
  public String toString() {
    return reducedSchedule.toString() + " plus " + tasks;
  }
}
