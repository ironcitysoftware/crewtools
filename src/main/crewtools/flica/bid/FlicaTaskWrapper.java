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

import org.joda.time.LocalDate;

import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.util.Period;

public class FlicaTaskWrapper {
  private FlicaTask flicaTask;
  private Set<LocalDate> dates;
  private PairingKey key;

  public FlicaTaskWrapper(FlicaTask flicaTask) {
    this.flicaTask = flicaTask;
    this.dates = getTaskDates(flicaTask);
    this.key = new PairingKey(flicaTask.pairingDate, flicaTask.pairingName);
  }

  public boolean isTwoHundred() {
    return flicaTask.pairingName.substring(1, 2).equals("2");
  }

  public FlicaTask getFlicaTask() {
    return flicaTask;
  }

  public Set<LocalDate> getTaskDates() {
    return dates;
  }

  public String getPairingName() {
    return flicaTask.pairingName;
  }

  public Period getCredit() {
    return flicaTask.creditTime;
  }

  public PairingKey getPairingKey() {
    return key;
  }

  public int getNumDays() {
    return flicaTask.numDays;
  }

  private Set<LocalDate> getTaskDates(FlicaTask task) {
    Set<LocalDate> dates = new HashSet<>();
    LocalDate startDate = task.pairingDate;
    dates.add(startDate);
    for (int i = 1; i < task.numDays; ++i) {
      dates.add(startDate.plusDays(i));
    }
    return dates;
  }

  @Override
  public int hashCode() {
    return flicaTask.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof FlicaTaskWrapper)) {
      return false;
    }
    FlicaTaskWrapper that = (FlicaTaskWrapper) o;
    return this.flicaTask.equals(that.flicaTask);
  }

  @Override
  public String toString() {
    return flicaTask.pairingName;
  }
}
