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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;

import crewtools.crewmobile.CalendarEntryIterator;
import crewtools.crewmobile.Proto.CalendarDataFeed;
import crewtools.crewmobile.Proto.CalendarEntry;
import crewtools.crewmobile.Proto.Flight;
import crewtools.flica.Proto.LegType;
import crewtools.flica.Proto.Schedule;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.parser.ParseUtils;
import crewtools.flica.pojo.Leg;
import crewtools.flica.pojo.Trip;
import crewtools.util.FileUtils;
import crewtools.util.Period;;

public class ComposeLogbook {
  private final Logger logger = Logger.getLogger(ComposeLogbook.class.getName());

  public static void main(String args[]) throws Exception {
    if (args.length != 2) {
      System.err.println("ComposeLogbook calendar.txt schedule.txt");
      System.exit(-1);
    }
    new ComposeLogbook().run(args[0], args[1]);
  }

  private final DateTimeFormatter timeFormat = DateTimeFormat
      .forPattern("yyyy-MM-dd'T'HH:mm:ss");

  private final DateTimeFormatter hhmm = DateTimeFormat.forPattern("HH:mm");

  // If the block time in the feed is zero, the flight number will be one
  // of these possibilities.
  private final Set<String> ignoredFlightNumbers;

  private final AircraftDatabase aircraftDatabase;

  public ComposeLogbook() throws IOException {
    this.aircraftDatabase = new AircraftDatabase();
    ImmutableSet.Builder<String> ignoredFlightNumbers = ImmutableSet.builder();
    for (LegType legType : LegType.values()) {
      String name = ParseUtils.getFlicaName(legType);
      ignoredFlightNumbers.add(name == null ? legType.name() : name);
    }
    this.ignoredFlightNumbers = ignoredFlightNumbers.build();
  }

  public void run(String calendarFile, String scheduleFile) throws Exception {
    CalendarDataFeed calendar = FileUtils.readProto(calendarFile, CalendarDataFeed.newBuilder());
    CalendarEntryIterator it = new CalendarEntryIterator(calendar);

    Schedule scheduleProto = FileUtils.readProto(scheduleFile, Schedule.newBuilder());
    crewtools.flica.pojo.Schedule schedule = new ScheduleAdapter().adapt(scheduleProto);
    for (Trip trip : schedule.trips) {
      for (Leg leg : trip.getLegs()) {
        CalendarEntry entry = it.next();
        Flight flight = entry.getFlight();
        logger.fine("Read leg " + leg.getDepartureAirportCode() + "->"
            + leg.getArrivalAirportCode() + ", calendar " + flight.getDep() + "->"
            + flight.getArr());
        if (leg.isDeadhead()) {
          Preconditions.checkState(entry.getFlight().getDH());
          continue;
        }
        // RLD, for example, have block minutes = 0.
        while (flight.getActBlockMinutes() == 0) {
          Preconditions.checkState(
              ignoredFlightNumbers.contains(flight.getFlightNumber()),
              flight.toString());
          entry = it.next();
          flight = entry.getFlight();
        }
        logger.fine("Skipped to calendar " + flight.getDep() + "->"
            + flight.getArr());
        assertEquals(leg, flight);
        output(leg, flight);
      }
    }
    System.out.println("Month block: " + monthBlockTime);
    for (LocalDate date : dailyBlockTime.keySet()) {
      System.out.println(date + "," + dailyBlockTime.get(date));
    }
  }

  private final Joiner joiner = Joiner.on(',');
  private Period monthBlockTime = Period.ZERO;
  private Map<LocalDate, Period> dailyBlockTime = new TreeMap<>();

  private void output(Leg leg, Flight flight) {
    DateTime departure = timeFormat.parseDateTime(flight.getActDepTimeUtc());
    DateTime arrival = timeFormat.parseDateTime(flight.getActArrTimeUtc());
    List<String> components = new ArrayList<>();
    LocalDate date = departure.toLocalDate();
    int shorthandTailNumber = Ints.tryParse(flight.getTailNumber());
    String tailNumber = aircraftDatabase.getTailNumber(shorthandTailNumber);
    String aircraftType = aircraftDatabase.getAircraftType(shorthandTailNumber);
    Preconditions.checkState(aircraftType.equals(flight.getEquipmentType()));
    components.add("" + date);
    components.add("JIA" + leg.getFlightNumber());
    components.add(flight.getEquipmentType());
    components.add(tailNumber);
    components.add(flight.getDep());
    components.add(flight.getArr());
    components.add(hhmm.print(departure));
    components.add(hhmm.print(arrival));
    components.add(flight.getActBlockTime());
    Period block = Period.fromTextWithColon(flight.getActBlockTime());
    if (!dailyBlockTime.containsKey(date)) {
      dailyBlockTime.put(date, Period.ZERO);
    }
    dailyBlockTime.put(date, dailyBlockTime.get(date).plus(block));
    monthBlockTime = monthBlockTime.plus(block);
    System.out.println(joiner.join(components));
  }

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
        timeFormat.parseDateTime(flight.getActDepTime()).toLocalTime()),
        "Departure time " + diff);
    Preconditions.checkState(leg.getArrivalTime().toLocalTime().equals(
        timeFormat.parseDateTime(flight.getActArrTime()).toLocalTime()),
        "Arrival time " + diff);
  }
}
