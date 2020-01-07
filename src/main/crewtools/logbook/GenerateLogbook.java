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
import java.util.List;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.io.Files;

import crewtools.crewmobile.CalendarEntryIterator;
import crewtools.crewmobile.Proto.CalendarDataFeed;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.util.AircraftDatabase;
import crewtools.util.AirportDatabase;
import crewtools.util.FileUtils;

public class GenerateLogbook {
  public static void main(String args[]) throws Exception {
    if (args.length == 0) {
      System.err.println("GenerateLogbook transcription.txt");
      System.exit(-1);
    }
    File input = new File(args[0]);
    GenerateLogbook generateLogbook = new GenerateLogbook(input.getParentFile());
    for (String arg : args) {
      input = new File(arg);
      generateLogbook.run(input);
    }
    generateLogbook.generate();
  }

  private final AircraftDatabase aircraftDatabase;
  private final AirportDatabase airportDatabase;
  private final File inputDirectory;
  private final Supplement supplement;
  private final Transcriber transcriber;
  private final Summary summary;

  public GenerateLogbook(File inputDirectory) throws IOException {
    this.aircraftDatabase = new AircraftDatabase();
    this.airportDatabase = new AirportDatabase();
    this.inputDirectory = inputDirectory;
    this.supplement = new Supplement(aircraftDatabase, airportDatabase);
    this.transcriber = new Transcriber(aircraftDatabase);
    this.summary = new Summary();
  }

  private static final Splitter SPLITTER = Splitter.on(CharMatcher.anyOf(" ,"));
  private static final Splitter EQUAL_SPLITTER = Splitter.on('=');

  public void run(File input) throws Exception {
    for (String line : Files.readLines(input, StandardCharsets.UTF_8)) {
      if (line.equals("quit")) {
        break;
      }
      if (parseDirective(line, supplement)) {
        // If we have both a schedule and calendar, iterate them.
        if (supplement.shouldIterate()) {
          for (Record record : supplement.getRecords()) {
            // context.process(record);
            summary.add(record);
            System.out.println(transcriber.transcribe(record));
          }
        }
        continue;
      }
      // Otherwise, we iterate each line of the transcription.
      Record record = supplement.buildRecord(SPLITTER.splitToList(line));
      summary.add(record);
      System.out.println(transcriber.transcribe(record));
    }
  }

  public void generate() {
    System.out.println(summary);
  }

  private boolean parseDirective(String line, Supplement supplement)
      throws IOException {
    if (line.startsWith("#") || line.isEmpty()) {
      return true;
    }

    if (line.startsWith("supplement:")) {
      modifySupplement(supplement, line.substring("supplement:".length()));
      return true;
    }

    if (line.startsWith("pic")) {
      supplement.beginPic();
      return true;
    }
    return false;
  }

  private void modifySupplement(Supplement supplement, String line) throws IOException {
    if (line.equals("none")) {
      supplement.useSchedule(null);
    }
    for (String token : SPLITTER.split(line)) {
      List<String> parts = EQUAL_SPLITTER.splitToList(token);
      if (parts.get(0).equals("schedule")) {
        crewtools.flica.Proto.Schedule scheduleProto = FileUtils.readProto(
            new File(inputDirectory, parts.get(1)),
            crewtools.flica.Proto.Schedule.newBuilder());
        supplement.useSchedule(new ScheduleAdapter().adapt(scheduleProto));
      } else if (parts.get(0).equals("calendar")) {
        CalendarDataFeed calendar = FileUtils.readProto(
            new File(inputDirectory, parts.get(1)),
            CalendarDataFeed.newBuilder());
        supplement.useCalendar(new CalendarEntryIterator(calendar));
      } else if (parts.get(0).equals("strict_time_parsing")) {
        supplement.setStrictTimeParsing(Boolean.valueOf(parts.get(1)));
      } else if (parts.get(0).equals("utc")) {
        supplement.setUtcTime(Boolean.valueOf(parts.get(1)));
      }
    }
  }
}
