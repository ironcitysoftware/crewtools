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

import org.joda.time.LocalDate;

import com.google.common.collect.ImmutableSet;

import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Trip;
import crewtools.test.TripBuilder;
import crewtools.util.Period;

public class DebugInjector {
  private final Collector collector;
  private final TripDatabase tripDatabase;

  public DebugInjector(Collector collector, TripDatabase tripDatabase) {
    this.collector = collector;
    this.tripDatabase = tripDatabase;
  }

  public void offer() {
    Trip trip = new TripBuilder()
        .withLocalDate(LocalDate.parse("2019-7-15"))
        .withName("L1234")
        .withLeg("CLT", "FAY", Period.fromText("1554"))
        .withLayover("FAY", Period.hours(15))
        .withLeg("FAY", "CHS", Period.hours(1))
        .withLayover("CHS", Period.hours(12))
        .withLeg("CHS", "BTV", Period.hours(1))
        .withLayover("BTV", Period.hours(13))
        .withLeg("BTV", "CLT", Period.hours(1))
        .build();
    tripDatabase.addTrip(trip);

    FlicaTask task = new FlicaTask(
        PairingKey.parse("2019-7-15:L1234"),
        Period.fromText("1854"),
        4);
    collector.offer(ImmutableSet.of(task));
  }
}
