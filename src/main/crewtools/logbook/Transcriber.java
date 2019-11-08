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
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Joiner;

import crewtools.util.AircraftDatabase;

public class Transcriber {
  private static final Joiner JOINER = Joiner.on(',');

  final DateTimeFormatter outputTimeFormat = DateTimeFormat
      .forPattern("HH:mm");

  final AircraftDatabase aircraftDatabase;

  public Transcriber(AircraftDatabase aircraftDatabase) {
    this.aircraftDatabase = aircraftDatabase;
  }

  public String transcribe(Record record) {
    List<String> components = new ArrayList<>();
    DateTime departureTimeUtc = record.zonedDepartureTime != null
        ? record.zonedDepartureTime.withZone(DateTimeZone.UTC)
        : null;
    DateTime arrivalTimeUtc = record.zonedArrivalTime != null
        ? record.zonedArrivalTime.withZone(DateTimeZone.UTC)
        : null;
    LocalDate date = departureTimeUtc != null
        ? departureTimeUtc.toLocalDate()
        : record.date;
    components.add(date.toString());
    components.add("JIA" + record.flightNumber);
    if (record.shorthandAircraftType.isEmpty()) {
      components.add("");
    } else {
      components.add("RJ" + record.shorthandAircraftType);
    }
    if (record.shorthandTailNumber == 0) {
      components.add("");
    } else {
      components.add(aircraftDatabase.getTailNumber(record.shorthandTailNumber));
    }
    components.add(record.departureAirport);
    components.add(record.arrivalAirport);

    if (departureTimeUtc != null) {
      components.add(outputTimeFormat.print(departureTimeUtc.toLocalTime()));
    } else {
      components.add("");
    }

    if (arrivalTimeUtc != null) {
      components.add(outputTimeFormat.print(arrivalTimeUtc.toLocalTime()));
    } else {
      components.add("");
    }

    components.add(record.block.toString());
    return JOINER.join(components);
  }
}
