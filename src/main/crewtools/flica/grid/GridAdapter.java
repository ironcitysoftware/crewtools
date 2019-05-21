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

import java.util.Set;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import crewtools.rpc.Proto.GridObservation;
import crewtools.rpc.Proto.Observation;
import crewtools.util.Clock;

public class GridAdapter {
  private final Clock clock;
  private final YearMonth yearMonth;

  public GridAdapter(Clock clock, YearMonth yearMonth) {
    this.clock = clock;
    this.yearMonth = yearMonth;
  }

  public Set<LocalDate> getGreenDays(GridObservation gridObservation) {
    ImmutableSet.Builder<LocalDate> builder = ImmutableSet.builder();
    for (Observation observation : gridObservation.getObservationList()) {
      Preconditions.checkState(observation.getMonth() == yearMonth.getMonthOfYear());
      LocalDate date = new LocalDate(yearMonth.getYear(), yearMonth.getMonthOfYear(),
          observation.getDay());
      if (clock.today().isAfter(date)) {
        continue;
      }
      int netReserves = observation.getAvailableReserve() - observation.getOpenDutyPeriods();
      if (netReserves > observation.getMinRequired()) {
        builder.add(date);
      }
    }
    return builder.build();
  }
}
