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
import java.util.Map;
import java.util.TreeMap;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.io.Files;

import crewtools.crewmobile.CalendarEntryIterator;
import crewtools.crewmobile.Proto.CalendarDataFeed;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.util.AircraftDatabase;
import crewtools.util.AirportDatabase;
import crewtools.util.FileUtils;
import crewtools.util.Period;

public class GenerateLogbook {
  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err.println("GenerateLogbook transcription.txt");
      System.exit(-1);
    }
    File input = new File(args[0]);
    new GenerateLogbook(input.getParentFile()).run(input);
  }

  private final AircraftDatabase aircraftDatabase;
  private final AirportDatabase airportDatabase;
  private final File inputDirectory;

  public GenerateLogbook(File inputDirectory) throws IOException {
    this.aircraftDatabase = new AircraftDatabase();
    this.airportDatabase = new AirportDatabase();
    this.inputDirectory = inputDirectory;
  }

  private static final Splitter SPLITTER = Splitter.on(CharMatcher.anyOf(" ,"));
  private static final Splitter EQUAL_SPLITTER = Splitter.on('=');

  private class Context {
    private YearMonth currentYearMonth;
    private Map<LocalDate, Period> dailyBlockTime = new TreeMap<>();
    private Period monthBlock;
    private Period totalBlock;

    public Context(Supplement supplement) {
      this.currentYearMonth = null;
      this.monthBlock = Period.ZERO;
      this.totalBlock = Period.ZERO;
    }

    public void process(Record record) {
      YearMonth yearMonth = new YearMonth(record.date.getYear(),
          record.date.getMonthOfYear());
      if (currentYearMonth == null) {
        currentYearMonth = yearMonth;
      } else {
        if (!yearMonth.equals(currentYearMonth)) {
          System.out.println(currentYearMonth + " block: " + monthBlock
              + "; total block: " + totalBlock);
          monthBlock = Period.ZERO;
          currentYearMonth = yearMonth;
        }
      }
      monthBlock = monthBlock.plus(record.block);
      if (!dailyBlockTime.containsKey(record.date)) {
        dailyBlockTime.put(record.date, Period.ZERO);
      }
      dailyBlockTime.put(record.date, dailyBlockTime.get(record.date).plus(record.block));
      totalBlock = totalBlock.plus(record.block);
    }

    public void resetTotalBlock() {
      System.out.println("Total SIC: " + totalBlock);
      totalBlock = Period.ZERO;
    }

    public void close() {
      // for (LocalDate date : dailyBlockTime.keySet()) {
      // System.out.println(date + "," + dailyBlockTime.get(date));
      // }
      if (currentYearMonth != null) {
        System.out.println(currentYearMonth + " block: " + monthBlock);
      }
      System.out.println(totalBlock.toString());
    }
  }

  public void run(File input) throws Exception {
    Supplement supplement = new Supplement(aircraftDatabase, airportDatabase);
    Transcriber transcriber = new Transcriber(aircraftDatabase);
    Context context = new Context(supplement);

    for (String line : Files.readLines(input, StandardCharsets.UTF_8)) {
      if (parseDirective(line, supplement, context)) {
        // If we have both a schedule and calendar, iterate them.
        if (supplement.shouldIterate()) {
          for (Record record : supplement.getRecords()) {
            context.process(record);
            System.out.println(transcriber.transcribe(record));
          }
        }
        continue;
      }
      // Otherwise, we iterate each line of the transcription.
      Record record = supplement.buildRecord(SPLITTER.splitToList(line));
      context.process(record);
      System.out.println(transcriber.transcribe(record));
    }

    context.close();
  }

  private boolean parseDirective(String line, Supplement supplement, Context context)
      throws IOException {
    if (line.startsWith("#")) {
      return true;
    }

    if (line.startsWith("supplement:")) {
      modifySupplement(supplement, line.substring("supplement:".length()));
      return true;
    }

    if (line.startsWith("pic")) {
      context.resetTotalBlock();
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
      }
    }
  }
}
