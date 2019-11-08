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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;

import crewtools.crewmobile.Proto.CalendarEntry;
import crewtools.crewmobile.Proto.Flight;
import crewtools.flica.Proto.LegType;
import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.parser.ParseUtils;
import crewtools.flica.pojo.Leg;
import crewtools.flica.pojo.Schedule;
import crewtools.util.AircraftDatabase;
import crewtools.util.AirportDatabase;
import crewtools.util.Period;

public class Supplement {
  private final Logger logger = Logger.getLogger(Supplement.class.getName());

  private final DateTimeFormatter inputTimeFormat = DateTimeFormat
      .forPattern("HHmm");
  private final AircraftDatabase aircraftDatabase;
  private final AirportDatabase airportDatabase;
  private final Set<String> ignoredMagicFlightNumbers;

  private Iterator<Leg> legs;
  private Iterator<CalendarEntry> calendar;
  private boolean strictTimeParsing;
  private boolean utcTime;
  private boolean isPic;

  public Supplement(
      AircraftDatabase aircraftDatabase,
      AirportDatabase airportDatabase) {
    this.aircraftDatabase = aircraftDatabase;
    this.airportDatabase = airportDatabase;
    this.ignoredMagicFlightNumbers = populateIgnoredMagicFlightNumbers();
    this.strictTimeParsing = true;
    this.utcTime = false;
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

  public void useCalendar(Iterator<CalendarEntry> calendar) {
    this.calendar = calendar;
  }

  public void setStrictTimeParsing(boolean value) {
    this.strictTimeParsing = value;
  }

  public void setUtcTime(boolean value) {
    this.utcTime = value;
  }

  public boolean shouldIterate() {
    return this.legs != null && this.calendar != null;
  }

  public void beginPic() {
    isPic = true;
  }

  public List<Record> getRecords() {
    List<Record> result = new ArrayList<>();
    while (legs.hasNext()) {
      Leg leg = legs.next();
      CalendarEntry entry = calendar.next();
      Flight flight = entry.getFlight();
      logger.fine("Read leg " + leg.getDepartureAirportCode() + "->"
          + leg.getArrivalAirportCode() + ", calendar " + flight.getDep() + "->"
          + flight.getArr());
      if (leg.isDeadhead()) {
        Preconditions.checkState(entry.getFlight().getDH());
        continue;
      }
      while (flight.getDH()) {
        entry = calendar.next();
        flight = entry.getFlight();
      }
      // RLD, for example, have block minutes = 0.
      while (flight.getActBlockMinutes() == 0) {
        Preconditions.checkState(
            ignoredMagicFlightNumbers.contains(flight.getFlightNumber()),
            flight.toString() + " flight number not in " +
                ignoredMagicFlightNumbers);
        entry = calendar.next();
        flight = entry.getFlight();
      }
      logger.fine("Skipped to calendar " + flight.getDep() + "->"
          + flight.getArr());
      assertEquals(leg, flight);
      Record record = buildRecord(leg, flight);
      validate(record);
      result.add(record);
    }
    return result;
  }

  private final DateTimeFormatter calendarTimeFormat = DateTimeFormat
      .forPattern("yyyy-MM-dd'T'HH:mm:ss");

  private void assertEquals(Leg leg, Flight flight) {
    String diff = leg.toString() + " vs " + flight.toString();
    Preconditions.checkState(
        leg.getFlightNumber() == Ints.tryParse(flight.getFlightNumber()),
        "Flight number " + diff);
    Preconditions.checkState(leg.getDepartureAirportCode().equals(
        flight.getDep()), "Departure " + diff);
    Preconditions.checkState(leg.getArrivalAirportCode().equals(
        flight.getArr()), "Arrival " + diff);
    Preconditions.checkState(leg.getDepartureTime().toLocalTime().equals(
        calendarTimeFormat.parseDateTime(flight.getActDepTime()).toLocalTime()),
        "Departure time " + diff);
    Preconditions.checkState(leg.getArrivalTime().toLocalTime().equals(
        calendarTimeFormat.parseDateTime(flight.getActArrTime()).toLocalTime()),
        "Arrival time " + diff);
  }

  public Record buildRecord(Leg leg, Flight flight) {
    DateTime departureUtc = calendarTimeFormat.parseDateTime(flight.getActDepTimeUtc());
    DateTime arrivalUtc = calendarTimeFormat.parseDateTime(flight.getActArrTimeUtc());
    LocalDate date = departureUtc.toLocalDate();
    String flightNumber = new Integer(leg.getFlightNumber()).toString();
    int shorthandTailNumber = Ints.tryParse(flight.getTailNumber());
    String shorthandAircraftType = flight.getEquipmentType().substring(2);
    String departureAirport = flight.getDep();
    String arrivalAirport = flight.getArr();
    Period block = Period.fromTextWithColon(flight.getActBlockTime());
    DateTime zonedDepartureTime = departureUtc.withZone(
        airportDatabase.getZone(departureAirport));
    DateTime zonedArrivalTime = arrivalUtc.withZone(
        airportDatabase.getZone(arrivalAirport));
    LocalTime departureTime = zonedDepartureTime.toLocalTime();
    LocalTime arrivalTime = zonedArrivalTime.toLocalTime();

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
        isPic,
        zonedDepartureTime,
        zonedArrivalTime);
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

    String rawDepartureTime = it.next();
    LocalTime departureTime = null;
    if (!rawDepartureTime.isEmpty() || strictTimeParsing) {
      departureTime = inputTimeFormat.parseLocalTime(rawDepartureTime);
    }

    String rawArrivalTime = it.next();
    LocalTime arrivalTime = null;
    if (!rawArrivalTime.isEmpty() || strictTimeParsing) {
      arrivalTime = inputTimeFormat.parseLocalTime(rawArrivalTime);
    }
    Period block = Period.fromText(it.next());

    if (utcTime) {
      /**
       * The paper times are in UTC, not local. The date, however, is local. Work
       * backwards to determine the local times.
       */
      if (departureTime != null) {
        DateTime guessDepartureTime = date.toDateTime(departureTime,
            DateTimeZone.UTC).withZone(
                airportDatabase.getZone(departureAirport));
        departureTime = guessDepartureTime.toLocalTime();
      }
      if (arrivalTime != null) {
        DateTime guessArrivalTime = date.toDateTime(arrivalTime,
            DateTimeZone.UTC).withZone(
                airportDatabase.getZone(arrivalAirport));
        arrivalTime = guessArrivalTime.toLocalTime();
      }
    }

    DateTime zonedDepartureTime = null;
    if (departureTime != null) {
      zonedDepartureTime = date.toDateTime(departureTime,
          airportDatabase.getZone(departureAirport));
    }

    DateTime zonedArrivalTime = null;
    if (arrivalTime != null) {
      zonedArrivalTime = date.toDateTime(arrivalTime,
          airportDatabase.getZone(arrivalAirport));
      if (zonedDepartureTime != null && zonedArrivalTime.isBefore(zonedDepartureTime)) {
        zonedArrivalTime = zonedArrivalTime.plusDays(1);
      }
    }

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
        isPic,
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
        isPic,
        zonedDepartureTime,
        zonedArrivalTime);
  }

  private void validate(Record record) {
    if (record.zonedDepartureTime != null
        && record.zonedArrivalTime != null) {
      int minutes = Minutes.minutesBetween(
          record.zonedDepartureTime, record.zonedArrivalTime).getMinutes();
      if (record.block.getTotalMinutes() != minutes) {
        throw new IllegalStateException(
            record + " bad block " + record.block + ", expected " +
                Period.minutes(minutes));
      }
    }
    if (record.shorthandTailNumber > 0) {
      Preconditions.checkState(("RJ" + record.shorthandAircraftType).equals(
          aircraftDatabase.getAircraftType(record.shorthandTailNumber)),
          "Is " + record.shorthandTailNumber + " really a RJ"
              + record.shorthandAircraftType);
    }
  }

  private Set<String> populateIgnoredMagicFlightNumbers() {
    ImmutableSet.Builder<String> ignoredFlightNumbers = ImmutableSet.builder();
    for (LegType legType : LegType.values()) {
      String name = ParseUtils.getFlicaName(legType);
      ignoredFlightNumbers.add(name == null ? legType.name() : name);
    }
    for (ScheduleType scheduleType : ScheduleType.values()) {
      String name = ParseUtils.getFlicaName(scheduleType);
      ignoredFlightNumbers.add(name == null ? scheduleType.name() : name);
    }
    return ignoredFlightNumbers.build();
  }
}
