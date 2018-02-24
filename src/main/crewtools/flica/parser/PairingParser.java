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
import java.util.logging.Logger;

import org.joda.time.YearMonth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.Trip;

public class PairingParser {
  private final Logger logger = Logger.getLogger(PairingParser.class.getName());

  private final String input;
  private final YearMonth yearMonth;
  private final boolean parseCanceled;
  private final ScheduleParserHelper helper;

  public PairingParser(String input, YearMonth yearMonth, boolean parseCanceled) {
    this.input = input;
    this.yearMonth = yearMonth;
    this.parseCanceled = parseCanceled;
    this.helper = new ScheduleParserHelper();
  }

  public PairingList parse() throws ParseException {
    try {
      return parseInternal();
    } catch (ParseException pe) {
      throw pe;
    } catch (Exception e) {
      throw new ParseException(e);
    }
  }

  private static final String PAIRING_CANCELED = "Pairing Canceled";

  protected PairingList parseInternal() throws ParseException {
    PairingList.Builder pairingList = PairingList.newBuilder();
    pairingList.setYearMonth(yearMonth.toString());
    Document document = Jsoup.parse(input);
    Elements containerCells = document.select("table[name=pagination] > tbody > tr > td > table");
    for (Element table : containerCells) {
      if (table.hasAttr("name") && table.attr("name").equals("header")) {
        // JSoup doesn't seem to be able to filter on not attribute value.
        // note: although this is called 'header', it contains a steel blue
        // line, not the actual column headers.
        continue;
      }
      // JSoup returns all recursive children unless we limit it with >
      Elements rows = table.select("> tbody > tr");
      if (rows.size() == 1 && rows.select("> td").text().isEmpty()) {
        // The full pairing list has an empty table at the end.
        logger.info("Ignoring trailing empty table");
        continue;
      }
      Iterator<Element> rowsIterator = rows.iterator();
      Element headerRow = rowsIterator.next();
      if (headerRow.toString().contains(PAIRING_CANCELED)) {
        if (!parseCanceled) {
          logger.fine("Skipping canceled pairing");
          continue;
        } else {
          headerRow = rowsIterator.next();
        }
      }
      Trip.Builder trip = pairingList.addTripBuilder();
      String localReportTime = helper.parseHeader(headerRow, trip, yearMonth.getYear());
      helper.parseTrip(localReportTime, rowsIterator, trip, yearMonth.getYear());
    }
    return pairingList.build();
  }
}
