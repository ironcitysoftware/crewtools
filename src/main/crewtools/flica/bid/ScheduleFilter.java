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

import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.util.Clock;

public class ScheduleFilter implements Predicate<Set<PairingKey>> {

  private final Logger logger = Logger.getLogger(ScheduleFilter.class.getName());

  private final Schedule schedule;
  private final Clock clock;

  public ScheduleFilter(Schedule schedule, Clock clock) {
    this.schedule = schedule;
    this.clock = clock;
  }

  @Override
  public boolean test(Set<PairingKey> tripSet) {
    if (!tripSet.containsAll(schedule.getUndroppable(clock))) {
      return false;
    }
    // TODO: in SBB/Opentime mode, consult reserve grid.
    return true;
  }
}
