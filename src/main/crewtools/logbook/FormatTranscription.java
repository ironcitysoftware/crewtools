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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;

import crewtools.util.Period;

public class FormatTranscription {
  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err.println("FormatTranscription transcription.txt");
      System.exit(-1);
    }
    new FormatTranscription().run(args[0]);
  }

  private final AircraftDatabase aircraftDatabase;
  private final AirportDatabase airportDatabase;

  public FormatTranscription() throws IOException {
    this.aircraftDatabase = new AircraftDatabase();
    this.airportDatabase = new AirportDatabase();
  }

  private static final Splitter SPLITTER = Splitter.on(',');
  private static final Joiner JOINER = Joiner.on(',');


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

    private final DateTimeFormatter inputTimeFormat = DateTimeFormat
        .forPattern("HHmm");
    private final DateTimeFormatter outputTimeFormat = DateTimeFormat
        .forPattern("HH:mm");

    public Record(Iterator<String> it) {
      this.date = LocalDate.parse(it.next());
      this.flightNumber = it.next();
      this.shorthandAircraftType = it.next();
      this.shorthandTailNumber = Ints.tryParse(it.next());
      this.departureAirport = it.next();
      this.arrivalAirport = it.next();
      this.departureTime = inputTimeFormat.parseLocalTime(it.next());
      this.arrivalTime = inputTimeFormat.parseLocalTime(it.next());
      this.block = Period.fromText(it.next());
      this.zonedDepartureTime = date.toDateTime(departureTime, airportDatabase.getZone(departureAirport));
      this.zonedArrivalTime = date.toDateTime(arrivalTime, airportDatabase.getZone(arrivalAirport));
    }

    public void validate() {
      int minutes = Minutes.minutesBetween(zonedDepartureTime, zonedArrivalTime).getMinutes();
      if (block.getTotalMinutes() != minutes) {
        throw new IllegalStateException(
            toString() + " bad block " + block.getTotalMinutes());
      }
      Preconditions.checkState(("RJ" + shorthandAircraftType).equals(
          aircraftDatabase.getAircraftType(shorthandTailNumber)));
    }

    public String transcribe() {
      List<String> components = new ArrayList<>();
      DateTime departureTimeUtc = zonedDepartureTime.withZone(DateTimeZone.UTC);
      DateTime arrivalTimeUtc = zonedArrivalTime.withZone(DateTimeZone.UTC);
      components.add("" + departureTimeUtc.toLocalDate());
      components.add("JIA" + flightNumber);
      components.add("RJ" + shorthandAircraftType);
      components.add(aircraftDatabase.getTailNumber(shorthandTailNumber));
      components.add(departureAirport);
      components.add(arrivalAirport);
      components.add(outputTimeFormat.print(departureTimeUtc.toLocalTime()));
      components.add(outputTimeFormat.print(arrivalTimeUtc.toLocalTime()));
      components.add(block.toString());
      return JOINER.join(components);
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
          .add("block", block)
          .toString();
    }
  }

  private Map<LocalDate, Period> dailyBlockTime = new TreeMap<>();
  private Period totalBlock = Period.ZERO;

  public void run(String transcriptionFile) throws Exception {
    for (String line : Files.readLines(new File(transcriptionFile),
        StandardCharsets.UTF_8)) {
      Record record = new Record(SPLITTER.split(line).iterator());
      record.validate();
      System.out.println(record.transcribe());
      if (!dailyBlockTime.containsKey(record.date)) {
        dailyBlockTime.put(record.date, Period.ZERO);
      }
      dailyBlockTime.put(record.date, dailyBlockTime.get(record.date).plus(record.block));
      totalBlock = totalBlock.plus(record.block);
    }
    for (LocalDate date : dailyBlockTime.keySet()) {
      System.out.println(date + "," + dailyBlockTime.get(date));
    }
    System.out.println(totalBlock.toString());
  }
}
