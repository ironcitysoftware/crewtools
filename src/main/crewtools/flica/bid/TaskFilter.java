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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.joda.time.LocalDate;

import crewtools.rpc.Proto.BidConfig;

public class TaskFilter implements Predicate<Set<FlicaTaskWrapper>> {

  private final Logger logger = Logger.getLogger(TaskFilter.class.getName());
  private final BidConfig bidConfig;

  public TaskFilter(BidConfig bidConfig) {
    this.bidConfig = bidConfig;
  }

  /** Returns true if this is a valid task subset. */
  @Override
  public boolean test(Set<FlicaTaskWrapper> taskSet) {
    // Only add filters which apply to the entire set here.
    // Individual task filters belong in Solver.

    // We cannot straight drop.
    if (taskSet.isEmpty()) {
      return false;
    }

    // Unlikely to pick up this many adds at once, and,
    // we don't want them, even if we can get them.
    if (taskSet.size() > bidConfig.getMaximumNumberOfAddsPerSwap()) {
      return false;
    }

    // The tasks must not overlap with each other.
    Set<LocalDate> allTaskDates = new HashSet<>();
    for (FlicaTaskWrapper task : taskSet) {
      for (LocalDate taskDate : task.getTaskDates()) {
        if (!allTaskDates.add(taskDate)) {
          return false;
        }
      }
    }

    return true;
  }
}
