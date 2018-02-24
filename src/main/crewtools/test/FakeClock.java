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

package crewtools.test;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import crewtools.util.Clock;
import crewtools.util.SystemClock;

public class FakeClock implements Clock {
  private final LocalDate today;
  
  public FakeClock(LocalDate today) {
    this.today = today;
  }
  
  @Override
  public LocalDate today() {
    return today;
  }
  
  @Override
  public DateTime now() {
    return today.toDateTimeAtStartOfDay();
  }
}
