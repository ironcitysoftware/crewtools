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

package crewtools.flica.parser;

import static crewtools.flica.parser.ParseUtils.checkState;
import static crewtools.flica.parser.ParseUtils.expandCells;
import static crewtools.flica.parser.ParseUtils.parseTripLocalDateWithYearHint;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;
import org.jsoup.nodes.Element;

import com.google.common.collect.ImmutableList;

import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.Proto.Trip;
import crewtools.flica.Proto.TripOrBuilder;

public class ReserveParser {
  private final Iterator<Element> rows;
  private final Trip.Builder trip;
  private final int year;

  public ReserveParser(Iterator<Element> rows, Trip.Builder trip, int year) {
    this.rows = rows;
    this.trip = trip;
    this.year = year;
  }

  private static final List<String> EXPECTED_HEADERS = ImmutableList.of(
      "Activity", "Start Date", "Start Time", "End Date", "End Time", "Credit");

  private static final Map<String, ScheduleType> SCHEDULE_TYPES =
      ParseUtils.getEnumValueMap(ScheduleType.class);

  public static boolean shouldParse(TripOrBuilder trip) {
    if (trip.hasScheduleType()) {
      return true;
    }
    if (SCHEDULE_TYPES.containsKey(trip.getPairingName())) {
      return true;
    }
    return false;
  }

  public void parse() throws ParseException {
    verifyHeaders(rows.next());
    Iterator<String> cells = expandCells(rows.next()).iterator();
    trip.setScheduleType(SCHEDULE_TYPES.get(cells.next()));
    LocalDate localStartDate = parseTripLocalDateWithYearHint(cells.next(), year);
    trip.setStartDate(localStartDate.toString());
    trip.setStartTime(cells.next());
    LocalDate localEndDate = parseTripLocalDateWithYearHint(cells.next(), year);
    trip.setEndDate(localEndDate.toString());
    trip.setEndTime(cells.next());
    trip.setCreditDuration(cells.next());
    if (cells.hasNext()) {
      throw new ParseException("Unexpected trailing reserve cell: " + cells.next());
    }
    if (rows.hasNext()) {
      throw new ParseException("Unexpected trailing reserve rows: " + rows.next());
    }
  }

  private void verifyHeaders(Element headerRow) throws ParseException {
    checkState(EXPECTED_HEADERS.equals(expandCells(headerRow)),
        "" + expandCells(headerRow));
  }
}
