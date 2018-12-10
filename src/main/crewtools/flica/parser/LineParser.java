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

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.Proto.ThinLine;
import crewtools.flica.Proto.ThinLineList;
import crewtools.flica.Proto.ThinPairing;

public class LineParser {
  private final Logger logger = Logger.getLogger(LineParser.class.getName());
  private static final String LINE_NAME_HEADER_TEXT = "Line #";
  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').omitEmptyStrings();
  private static final String NBSP = "\u00a0";
  private static final String CARRY_IN_DAY = "CI";
  private static final String DASH = "-";

  protected final String input;

  public LineParser(String input) {
    this.input = input;
  }

  public ThinLineList parse() throws ParseException {
    try {
      return parseInternal();
    } catch (ParseException pe) {
      throw pe;
    } catch (Exception e) {
      throw new ParseException(e);
    }
  }

  protected ThinLineList parseInternal() throws ParseException {
    ThinLineList.Builder thinLineList = ThinLineList.newBuilder();
    Document document = Jsoup.parse(input);

    YearMonth yearMonth = parseYearMonth(document);
    thinLineList.setYearMonth(yearMonth.toString());  // eg 2017-08
    int numDaysInMonth = yearMonth.toLocalDate(1).dayOfMonth().getMaximumValue();

    if (yearMonth.getMonthOfYear() == 1) {
      // January 31 is included in February.
      numDaysInMonth--;
    } else if (yearMonth.getMonthOfYear() == 2) {
      // January 31 is included in February.
      // March 1 is included in February.
      // TODO: what about leap years?
      numDaysInMonth += 2;
    } else if (yearMonth.getMonthOfYear() == 3) {
      // March 1 is included in February.
      numDaysInMonth--;
    }

    int numColumns = numDaysInMonth + 1;  // one column for each day, plus name of line.

    // Contents of schedule
    Elements rows = document.select("table[name=Main] > tbody > tr > td"
        + " > table[name=DaysBar] > tbody > tr > td "
        + " > table[name=DaysBar] > tbody > tr");
    for (Element row : rows) {
      Elements tds = row.select("> td");
      if (tds.size() != numColumns) {
        logger.finest("Skipping row [column count]: " + row.toString());
        continue;
      }
      if (tds.get(0).text().equals(LINE_NAME_HEADER_TEXT)) {
        logger.finest("Skipping row [header text]: " + row.toString());
        continue;
      }
      ThinLine.Builder thinLine = thinLineList.addThinLineBuilder();
      parseLine(numColumns, yearMonth, tds, thinLine);
    }
    return thinLineList.build();
  }

  private static final Set<Integer> MONTHS_WHERE_FIRST_COLUMN_IS_FIRST_DAY = ImmutableSet
      .of(1, 4, 5, 6, 7, 8, 9, 10, 11, 12);

  /** column is 1 .. numColumns */
  /** TODO, capture header text row and properly map columns to days */
  /** Except, I'm not sure what this is used for and how much effort to put into it */
  private LocalDate getColumnDate(YearMonth yearMonth, int column) {
    int monthOfYear = yearMonth.getMonthOfYear();
    if (MONTHS_WHERE_FIRST_COLUMN_IS_FIRST_DAY.contains(monthOfYear)) {
      return yearMonth.toLocalDate(column);
    }

    // exceptions follow.
    if (yearMonth.getMonthOfYear() == 2) {
      if (column == 1) {
        return yearMonth.minusMonths(1).toLocalDate(31);
      } else if (column == 30) {
        return yearMonth.plusMonths(1).toLocalDate(1);
      } else {
        /* 2 .. 29 */
        return yearMonth.toLocalDate(column - 1);
      }
    } else if (yearMonth.getMonthOfYear() == 3) {
      return yearMonth.toLocalDate(column + 1);
    } else {
      throw new IllegalStateException(
          "TODO Verify that column 1 is the first of the month");
    }
  }

  private void parseLine(int numColumns, YearMonth yearMonth, Elements tds,
      ThinLine.Builder builder) throws ParseException {
    String lineName = tds.get(0).text();
    builder.setLineName(lineName);
    logger.fine(lineName);
    ThinPairing.Builder currentPairing = null;
    boolean isFirstDayOfPairing = false;
    for (int dayOfMonth = 1; dayOfMonth < numColumns; ++dayOfMonth) {
      LocalDate cellDate = getColumnDate(yearMonth, dayOfMonth);
      Element cell = tds.get(dayOfMonth);
      if (cell.text().equals(NBSP)) {
        currentPairing = null;
        continue;
      }

      List<String> components = SPACE_SPLITTER.omitEmptyStrings().trimResults()
          .splitToList(cell.text());
      logger.fine(components.toString());
      // "-, GSP" normally. "-" if this is a day of rest in the line.
      // also 0500, SCR or -, LCR or -, R2 or just 'R'
      if (components.size() != 1 && components.size() != 2) {
        throw new ParseException("Expected 1 or 2 components in [" + cell.text() + "]");
      }

      Elements as = cell.select("> a");
      if (!as.isEmpty()) {
        Element a = as.get(0);
        currentPairing = builder.addThinPairingBuilder();
        currentPairing.setPairingName(a.text());
        currentPairing.setDate(cellDate.toString());
        isFirstDayOfPairing = true;
      } else {
        if (components.size() == 2
            && components.get(0).equals(DASH)
            && components.get(1).equals("LCR")) {
          // Long call reserve has -, LCR for every day.
          if (currentPairing == null) {
            currentPairing = builder.addThinPairingBuilder();
            currentPairing.setDate(cellDate.toString());
          }
          currentPairing.addScheduleType(ScheduleType.LONG_CALL_RESERVE);
          continue;
        } else if ((components.size() == 2
            && components.get(0).equals(DASH)
            && components.get(1).equals("R2"))
            || (components.size() == 1 && components.get(0).equals("R"))) {
          // Seriously? What is the difference beweeen "R" and "-, R2"
          // Short call reserve has -, R2 on subsequent days.
          if (currentPairing == null) {
            currentPairing = builder.addThinPairingBuilder();
            currentPairing.setDate(cellDate.toString());
          }
          currentPairing.addScheduleType(ScheduleType.SHORT_CALL_RESERVE);
          continue;
        } else if (components.size() == 2 && components.get(1).equals("SCR")) {
          // Short call reserve has 0500 SCR on the first day.
          Preconditions.checkState(currentPairing == null);
          currentPairing = builder.addThinPairingBuilder();
          currentPairing.setDate(cellDate.toString());
          currentPairing.addScheduleType(ScheduleType.SHORT_CALL_RESERVE);
          currentPairing.setLocalReserveStartTime(components.get(0));
          continue;
        }
        isFirstDayOfPairing = false;
      }

      if (!isFirstDayOfPairing && !components.get(0).equals(DASH)) {
        throw new ParseException("Expected - as first component but got: " + components);
      }
      logger.fine("Considering adding overnight: " + components);
      if (components.size() > 1) {
        String airportCodeOrCarryIn = components.get(1);
        if (airportCodeOrCarryIn.equals(CARRY_IN_DAY)) {
          builder.addCarryInDay(cellDate.toString());
        } else {
          Preconditions.checkNotNull(currentPairing, components);
          currentPairing.addOvernightAirportCode(components.get(1));
        }
      }
    }
  }

  private YearMonth parseYearMonth(Document document) throws ParseException {
    // "FLICA.Net - CLT CRJ FO Lines - Sep 2017"
    Elements yearMonthCell = document.select("html > head > title");
    List<String> yearMonthComponents = Splitter.on(" - ").splitToList(yearMonthCell.text());
    if (yearMonthComponents.size() != 3) {
      throw new ParseException("Unrecognized yearMonth components: " + yearMonthComponents);
    }
    DateTimeFormatter formatter =
        DateTimeFormat.forPattern("MMM yyyy").withLocale(Locale.ENGLISH);
    LocalDate yearMonth = formatter.parseLocalDate(yearMonthComponents.get(2));
    return new YearMonth(yearMonth.getYear(), yearMonth.getMonthOfYear());
  }
}
