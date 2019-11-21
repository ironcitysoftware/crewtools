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

package crewtools.crewmobile;

import java.io.File;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import crewtools.crewmobile.Proto.CalendarDataFeed;
import crewtools.crewmobile.Proto.CalendarEntry;
import crewtools.crewmobile.Proto.Day;
import crewtools.crewmobile.Proto.Flight;
import crewtools.crewmobile.Proto.Month;
import crewtools.util.FileUtils;

public class PrintCalendar {
  private static final DateTimeFormatter calendarTimeFormat = DateTimeFormat
      .forPattern("yyyy-MM-dd'T'HH:mm:ss");

  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err.println("printCalendar calendar.txt");
      System.exit(-1);
    }
    CalendarDataFeed calendar = FileUtils.readProto(
        new File(args[0]),
        CalendarDataFeed.newBuilder());
    for (Month month : calendar.getMonthList()) {
      System.out.println("month: " + month.getMonth());
      for (Day day : month.getDayList()) {
        boolean dayPrinted = false;
        for (CalendarEntry entry : day.getCalendarEntryList()) {
          if (entry.hasFlight()) {
            Flight flight = entry.getFlight();
            DateTime arrivalTime = calendarTimeFormat
                .parseDateTime(flight.getSchedArrTime());
            if (arrivalTime.getYear() > 1) {
              if (!dayPrinted) {
                System.out.printf("  day: %s\n", day.getDay());
                dayPrinted = true;
              }
              System.out.printf("    %s %s -> %s\n", flight.getFlightNumber(),
                  flight.getDep(), flight.getArr());
            }
          }
        }
      }
    }
  }
}
