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

import java.util.Iterator;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import crewtools.flica.pojo.Leg;
import crewtools.flica.pojo.Schedule;
import crewtools.util.AircraftDatabase;
import crewtools.util.AirportDatabase;
import crewtools.util.Period;

public class Supplement {
  final DateTimeFormatter inputTimeFormat = DateTimeFormat
      .forPattern("HHmm");
  final AircraftDatabase aircraftDatabase;
  final AirportDatabase airportDatabase;

  Iterator<Leg> legs;

  public Supplement(
      AircraftDatabase aircraftDatabase,
      AirportDatabase airportDatabase) {
    this.aircraftDatabase = aircraftDatabase;
    this.airportDatabase = airportDatabase;
  }

  public void useSchedule(Schedule schedule) {
    if (this.legs != null) {
      // There is a leg on the schedule which is not in our logbook transcription.
      Preconditions.checkState(!legs.hasNext(), "Missing record from transcription");
    }
    if (schedule == null) {
      this.legs = null;
    } else {
      this.legs = new LegIterator(schedule);
    }
  }

  public Record buildRecord(List<String> line) {
    Record record;
    if (legs != null) {
      // There is a leg in the logbook transcription which is not on our schedule.
      Preconditions.checkState(legs.hasNext(), "Missing record from schedule");
      record = buildRecordWithFlicaAndPaper(legs.next(), line);
    } else {
      record = buildRecordWithPaperOnly(line);
    }
    validate(record);
    return record;
  }

  private Record buildRecordWithPaperOnly(List<String> line) {
    Iterator<String> it = line.iterator();
    LocalDate date = LocalDate.parse(it.next());
    String flightNumber = it.next();
    String shorthandAircraftType = it.next();
    int shorthandTailNumber = Ints.tryParse(it.next());
    String departureAirport = it.next();
    String arrivalAirport = it.next();
    LocalTime departureTime = inputTimeFormat.parseLocalTime(it.next());
    LocalTime arrivalTime = inputTimeFormat.parseLocalTime(it.next());
    Period block = Period.fromText(it.next());
    DateTime zonedDepartureTime = date.toDateTime(departureTime,
        airportDatabase.getZone(departureAirport));
    DateTime zonedArrivalTime = date.toDateTime(arrivalTime,
        airportDatabase.getZone(arrivalAirport));

    return new Record(
        date,
        flightNumber,
        shorthandAircraftType,
        shorthandTailNumber,
        departureAirport,
        arrivalAirport,
        departureTime,
        arrivalTime,
        block,
        zonedDepartureTime,
        zonedArrivalTime);
  }

  private Record buildRecordWithFlicaAndPaper(Leg leg, List<String> line) {
    Preconditions.checkNotNull(leg);
    Iterator<String> it = line.iterator();

    LocalDate date = LocalDate.parse(it.next());
    Preconditions.checkState(leg.getDate().equals(date),
        line + " but next leg is " + leg.toProtoString());

    String flightNumber = it.next();
    Preconditions.checkState(leg.getFlightNumber() == Ints.tryParse(flightNumber));

    String shorthandAircraftType = it.next();
    int shorthandTailNumber = Ints.tryParse(it.next());

    String departureAirport = it.next();
    if (departureAirport.isEmpty()) {
      departureAirport = leg.getDepartureAirportCode();
    } else {
      Preconditions.checkState(leg.getDepartureAirportCode().equals(departureAirport));
    }

    String arrivalAirport = it.next();
    if (arrivalAirport.isEmpty()) {
      arrivalAirport = leg.getArrivalAirportCode();
    } else {
      Preconditions.checkState(leg.getArrivalAirportCode().equals(arrivalAirport));
    }

    String logbookDepartureTime = it.next();
    LocalTime departureTime;
    if (logbookDepartureTime.isEmpty()) {
      departureTime = leg.getDepartureLocalTime();
    } else {
      departureTime = inputTimeFormat.parseLocalTime(logbookDepartureTime);
      Preconditions
          .checkState(leg.getDepartureLocalTime().equals(departureTime));
    }

    String logbookArrivalTime = it.next();
    LocalTime arrivalTime;
    if (logbookArrivalTime.isEmpty()) {
      arrivalTime = leg.getArrivalLocalTime();
    } else {
      arrivalTime = inputTimeFormat.parseLocalTime(logbookArrivalTime);
      Preconditions.checkNotNull(leg);
      Preconditions.checkState(leg.getArrivalLocalTime().equals(arrivalTime),
          line + " but flica arrival is " + leg.getArrivalTime());
    }

    Period block = Period.fromText(it.next());
    Preconditions.checkState(leg.getBlockDuration().equals(block));

    DateTime zonedDepartureTime = leg.getDepartureTime();
    DateTime zonedArrivalTime = leg.getArrivalTime();

    return new Record(
        date,
        flightNumber,
        shorthandAircraftType,
        shorthandTailNumber,
        departureAirport,
        arrivalAirport,
        departureTime,
        arrivalTime,
        block,
        zonedDepartureTime,
        zonedArrivalTime);
  }

  private void validate(Record record) {
    int minutes = Minutes.minutesBetween(
        record.zonedDepartureTime, record.zonedArrivalTime).getMinutes();
    if (record.block.getTotalMinutes() != minutes) {
      throw new IllegalStateException(
          record + " bad block " + record.block + ", expected " +
              Period.minutes(minutes));
    }
    if (record.shorthandTailNumber > 0) {
      Preconditions.checkState(("RJ" + record.shorthandAircraftType).equals(
          aircraftDatabase.getAircraftType(record.shorthandTailNumber)),
          "Is " + record.shorthandTailNumber + " really a RJ"
              + record.shorthandAircraftType);
    }
  }
}
