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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.base.Preconditions;

import crewtools.flica.Proto.Trip;
import crewtools.flica.pojo.PairingKey;

public class IndividualPairingParser {
  private final Logger logger = Logger.getLogger(IndividualPairingParser.class.getName());

  private final PairingKey key;
  private final String input;
  private final ScheduleParserHelper helper;

  public IndividualPairingParser(PairingKey key, String input) {
    this.key = key;
    this.input = input;
    this.helper = new ScheduleParserHelper();
  }

  public Trip parse() throws ParseException {
    try {
      if (input.contains(PAIRING_NOT_FOUND)) {
        throw new IllegalStateException("Pairing " + key + " not found.");
      }
      return parseInternal();
    } catch (ParseException pe) {
      throw pe;
    } catch (Exception e) {
      throw new ParseException(e);
    }
  }

  private static final String PAIRING_CANCELED = "Pairing Canceled";
  private static final String PAIRING_NOT_FOUND = "Pairing not found";

  protected Trip parseInternal() throws ParseException {
    Document document = Jsoup.parse(input);
    Elements containerCells = document.select("table > tbody > tr > td > table");
    Preconditions.checkState(!containerCells.isEmpty(), document.toString());
    Element table = containerCells.first();
    // JSoup returns all recursive children unless we limit it with >
    Elements rows = table.select("> tbody > tr");
    Iterator<Element> rowsIterator = rows.iterator();
    Element headerRow = rowsIterator.next();
    if (headerRow.toString().contains(PAIRING_CANCELED)) {
      throw new IllegalStateException("pairing cancelled: " + key);
    }
    Trip.Builder trip = Trip.newBuilder();
    int year = key.getPairingDate().getYear();
    String localReportTime = helper.parseHeader(headerRow, trip, year);
    helper.parseTrip(localReportTime, rowsIterator, trip, year);
    return trip.build();
  }
}
