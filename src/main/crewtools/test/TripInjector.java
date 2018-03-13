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

import java.util.concurrent.BlockingQueue;

import org.joda.time.LocalDate;

import crewtools.flica.pojo.Trip;
import crewtools.util.Period;

/**
 * Autobidder test utility to inject a trip into the queue to verify that the
 * worker will swap into it.
 */
public class TripInjector extends Thread {
  private final BlockingQueue<Trip> queue;

  public TripInjector(BlockingQueue<Trip> queue) {
    this.queue = queue;
  }

  public void run() {
    TripBuilder builder = new TripBuilder()
        .withLocalDate(LocalDate.parse("2018-4-9"))
        .withLeg("CLT", "GSP", Period.hours(10))
        .withLayover("GSP", Period.hours(16))
        .withLeg("GSP", "CLT", Period.hours(10).plus(Period.minutes(30)));
    Trip trip = builder.build();
    System.out.println(trip.proto);;

    try {
      Thread.sleep(1000 * 10);
      queue.put(trip);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }
}
