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

import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.joda.time.LocalDate;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.util.Clock;

public class ScheduleFilter implements Predicate<Set<PairingKey>> {

  private final Logger logger = Logger.getLogger(ScheduleFilter.class.getName());

  private final Schedule schedule;
  private final Clock clock;
  private final Set<PairingKey> requiredDrops;

  public ScheduleFilter(Schedule schedule, Clock clock,
      Set<LocalDate> requiredDaysOff, Set<PairingKey> requiredDropsFromConfig) {
    this.schedule = schedule;
    this.clock = clock;
    ImmutableSet.Builder<PairingKey> requiredDrops = ImmutableSet
        .builder();

    // Required drops due to overlap with days off.
    for (PairingKey key : schedule.getTrips().keySet()) {
      Set<LocalDate> workDays = schedule.getTrips().get(key).getDepartureDates();
      if (!Sets.intersection(workDays, requiredDaysOff).isEmpty()) {
        requiredDrops.add(key);
      }
    }

    // Required drops due to config.
    requiredDrops.addAll(requiredDropsFromConfig);

    this.requiredDrops = requiredDrops.build();
  }

  /** Returns true if this is a valid schedule subset. */
  @Override
  public boolean test(Set<PairingKey> tripSet) {
    // The subset must contain all undroppable pairings.
    if (!tripSet.containsAll(schedule.getUndroppable(clock))) {
      return false;
    }
    // The subset must not contain a required drop.
    if (!Sets.intersection(tripSet, requiredDrops).isEmpty()) {
      return false;
    }

    // TODO: in SBB/Opentime mode, consult reserve grid.
    return true;
  }
}
