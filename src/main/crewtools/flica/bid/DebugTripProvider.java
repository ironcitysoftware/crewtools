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

package crewtools.flica.bid;

import java.util.Queue;

import org.joda.time.LocalDate;

import crewtools.flica.pojo.Trip;
import crewtools.test.TripBuilder;
import crewtools.util.Period;

public class DebugTripProvider extends Thread {
  private final Queue<Trip> queue;

  public DebugTripProvider(Queue<Trip> queue) {
    this.queue = queue;
  }

  public void run() {
    Trip trip = new TripBuilder()
        .withLocalDate(LocalDate.parse("2018-7-22"))
        .withLeg("CLT", "FAY", Period.hours(6))
        .withLayover("FAY", Period.hours(15))
        .withLeg("FAY", "CHS", Period.hours(6))
        .withLayover("CHS", Period.hours(12))
        .withLeg("CHS", "BTV", Period.hours(6))
        .withLayover("BTV", Period.hours(13))
        .withLeg("BTV", "CLT", Period.hours(3))
        .build();
    queue.offer(trip);
  }
}
