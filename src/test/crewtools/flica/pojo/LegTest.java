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

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import crewtools.flica.Proto;
import crewtools.flica.parser.ParseException;

public class LegTest {
  @Test
  public void testSameTimezoneLateTrip() throws ParseException {
    Proto.Leg protoLeg = Proto.Leg.newBuilder()
        .setDayOfMonth(1)
        .setDepartureAirportCode("CLT")
        .setDepartureLocalTime("0015")
        .setArrivalAirportCode("EWN")
        .setArrivalLocalTime("0115")
        .build();
    DateTime duty = new DateTime(2018, 5, 1, 23, 30, 0);
    Leg leg = new Leg(protoLeg, duty, 0);

    assertEquals(new LocalDate(2018, 5, 1), leg.getDate());
    assertEquals(new DateTime(2018, 5, 2, 0, 15, 0), leg.getDepartureTime());
    assertEquals(new DateTime(2018, 5, 2, 1, 15, 0), leg.getArrivalTime());
  }
}
