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

import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.base.Splitter;

import crewtools.flica.Proto.Schedule;
import crewtools.flica.Proto.Trip;

public class ScheduleParser {
  private final Logger logger = Logger.getLogger(ScheduleParser.class.getName());

  protected final String input;
  private final ScheduleParserHelper helper;

  public ScheduleParser(String input) {
    this.input = input;
    this.helper = new ScheduleParserHelper();
  }

  public Schedule parse() throws ParseException {
    try {
      return parseInternal();
    } catch (ParseException pe) {
      throw pe;
    } catch (Exception e) {
      throw new ParseException(e);
    }
  }

  protected Schedule parseInternal() throws ParseException {
    Schedule.Builder schedule = Schedule.newBuilder();
    Document document = Jsoup.parse(input);

    YearMonth yearMonth = parseYearMonth(document, input, schedule);
    schedule.setYearMonth(yearMonth.toString());  // 2017-08

    // Contents of schedule
    Elements containerCell = document.select("table[name=table4] > tbody > tr > td");
    // This cell contains alternating header tables and trip tables.
    for (Element table : containerCell.select("> table")) {
      if (table.hasAttr("name") && table.attr("name").equals("headertable")) {
        // JSoup doesn't seem to be able to filter on not attribute value.
        // note: although this is called 'headertable', it contains a steel blue
        // line, not the actual column headers.
        continue;
      }
      // JSoup returns all recursive children unless we limit it with >
      Iterator<Element> rows = table.select("> tbody > tr").iterator();
      Trip.Builder trip = schedule.addTripBuilder();
      String localReportTime = helper.parseHeader(rows.next(), trip, yearMonth.getYear());
      if (ReserveParser.shouldParse(trip)) {
        ReserveParser reserveParser = new ReserveParser(rows, trip, yearMonth.getYear());
        reserveParser.parse();
      } else {
        helper.parseTrip(localReportTime, rows, trip, yearMonth.getYear());
      }
    }
    return schedule.build();
  }

  private static final Pattern BLOCK_DATE_PATTERN = Pattern.compile("'&BlockDate=\\d{2}(\\d{2})'");

  private YearMonth parseYearMonth(Document document, String input, Schedule.Builder builder) {
    // "January Schedule"
    Elements monthNameCell = document.select("table[name=maintable] > tbody > tr > td > h3 > center > b");
    Iterator<String> monthNameIterator = Splitter.on(' ').split(monthNameCell.text()).iterator();
    if (!monthNameIterator.hasNext()) {
      throw new IllegalStateException("Unable to parse month name from maintable");
    }
    DateTimeFormatter formatter =
        DateTimeFormat.forPattern("MMMM").withLocale(Locale.ENGLISH);
    LocalDate month = formatter.parseLocalDate(monthNameIterator.next());

    // '&BlockDate=0717'
    Matcher blockDateMatcher = BLOCK_DATE_PATTERN.matcher(input);
    if (!blockDateMatcher.find()) {
      throw new IllegalStateException("Unable to parse year from input");
    }

    int year = 2000 + Integer.parseInt(blockDateMatcher.group(1));
    return new YearMonth(year, month.getMonthOfYear());
  }
}
