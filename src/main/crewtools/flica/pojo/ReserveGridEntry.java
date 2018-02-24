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

package crewtools.flica.pojo;

import com.google.common.base.MoreObjects;

public class ReserveGridEntry {
  public final int availableReserves;
  public final int openDutyPeriods;
  public final int minimumRequired;
  public final boolean isCritical;
  
  public ReserveGridEntry(int availableReserves,
      int openDutyPeriods, int minimumRequired, boolean isCritical) {
    this.availableReserves = availableReserves;
    this.openDutyPeriods = openDutyPeriods;
    this.minimumRequired = minimumRequired;
    this.isCritical = isCritical;
  }
  
  public boolean isGreen() {
    return getNetReserves() > minimumRequired;
  }
  
  public int getNetReserves() {
    return availableReserves - openDutyPeriods;
  }
  
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("availableReserves", availableReserves)
        .add("openDutyPeriods", openDutyPeriods)
        .add("minumumRequired",  minimumRequired)
        .toString();
  }
}
