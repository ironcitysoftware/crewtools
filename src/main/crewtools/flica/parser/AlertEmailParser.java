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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joda.time.YearMonth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import crewtools.flica.Proto.Trip;

public class AlertEmailParser {
  private final ScheduleParserHelper helper = new ScheduleParserHelper();  
  private final YearMonth yearMonth;

  public AlertEmailParser(YearMonth yearMonth) {
    this.yearMonth = yearMonth;
  }
  
//  public Trip parse(String text) throws ParseException {
//    Document document = Jsoup.parse(text);
//    // JSoup returns all recursive children unless we limit it with >
//    Elements body = document.select("body");
//    Elements rows = body.select("> table > tbody > tr");
//    Iterator<Element> rowsIterator = rows.iterator();
//    Element headerRow = rowsIterator.next();
//    Trip.Builder trip = Trip.newBuilder();
//    String localReportTime = helper.parseHeader(headerRow, trip, yearMonth.getYear());
//    helper.parseTrip(localReportTime, rowsIterator, trip, yearMonth.getYear());
//    return trip.build();
//  }
  
  public List<Trip> parse(String text) throws ParseException {
    List<Trip> results = new ArrayList<>();
    Document document = Jsoup.parse(text);
    // JSoup returns all recursive children unless we limit it with >
    Elements tables = document.select("body > table");
    for (Element table : tables) {
      Elements rows = table.select("> tbody > tr");
      Iterator<Element> rowsIterator = rows.iterator();
      Element headerRow = rowsIterator.next();
      Trip.Builder trip = Trip.newBuilder();
      String localReportTime = helper.parseHeader(headerRow, trip, yearMonth.getYear());
      helper.parseTrip(localReportTime, rowsIterator, trip, yearMonth.getYear());
      results.add(trip.build());
    }
    return results;
  }

}
