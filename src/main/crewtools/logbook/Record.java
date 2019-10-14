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

package crewtools.logbook;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import com.google.common.base.MoreObjects;

import crewtools.util.Period;

public class Record {
  public final LocalDate date;
  public final String flightNumber;
  public final String shorthandAircraftType;
  public final int shorthandTailNumber;
  public final String departureAirport;
  public final String arrivalAirport;
  public final LocalTime departureTime;
  public final LocalTime arrivalTime;
  public final Period block;

  public final DateTime zonedDepartureTime;
  public final DateTime zonedArrivalTime;

  public Record(
      LocalDate date,
      String flightNumber,
      String shorthandAircraftType,
      int shorthandTailNumber,
      String departureAirport,
      String arrivalAirport,
      LocalTime departureTime,
      LocalTime arrivalTime,
      Period block,
      DateTime zonedDepartureTime,
      DateTime zonedArrivalTime) {
    this.date = date;
    this.flightNumber = flightNumber;
    this.shorthandAircraftType = shorthandAircraftType;
    this.shorthandTailNumber = shorthandTailNumber;
    this.departureAirport = departureAirport;
    this.arrivalAirport = arrivalAirport;
    this.departureTime = departureTime;
    this.arrivalTime = arrivalTime;
    this.block = block;
    this.zonedDepartureTime = zonedDepartureTime;
    this.zonedArrivalTime = zonedArrivalTime;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("date", date)
        .add("flightNumber", flightNumber)
        .add("shorthandAircraftType", shorthandAircraftType)
        .add("shorthandTailNumber", shorthandTailNumber)
        .add("departureAirport", departureAirport)
        .add("arrivalAirport", arrivalAirport)
        .add("departureTime", departureTime)
        .add("arrivalTime", arrivalTime)
        .add("zonedDepartureTime", zonedDepartureTime)
        .add("zonedArrivalTime", zonedArrivalTime)
        .add("block", block)
        .toString();
  }
}
