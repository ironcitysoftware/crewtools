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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.LocalDate;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.CrewPosition;
import crewtools.flica.Proto.DayOfWeek;
import crewtools.flica.Proto.Domicile;
import crewtools.flica.Proto.Equipment;
import crewtools.flica.Proto.Trip;

public class ScheduleParserHelper {
  private final Logger logger = Logger.getLogger(ScheduleParserHelper.class.getName());

  protected void parseTrip(String localReportTime, Iterator<Element> rows, Trip.Builder trip, int year) throws ParseException {
    Map<CrewPosition, Integer> expectedCrewMembers = new HashMap<>();
    parseEquipmentAndRole(rows.next(), trip, expectedCrewMembers);
    Element tripInnerTable = rows.next();
    Elements tripInnerRows = tripInnerTable.select("> td table tr");
    SectionParser sectionParser = new SectionParser(
        localReportTime, tripInnerRows.iterator(), trip, year);
    sectionParser.parse();
    if (rows.hasNext()) {
      parseCrew(rows.next(), trip, expectedCrewMembers);
    }
    checkState(!rows.hasNext(), "Trailing row");
  }

  private static final Splitter TRIP_NAME_DATE_SPLITTER =
      Splitter.on(" :").trimResults().omitEmptyStrings();

  private static final Pattern SINGLE_DAY_OF_WEEK_PATTERN = Pattern.compile("^(ONLY ON|EXCEPT) (SUN|MON|TUE|WED|THU|FRI|SAT)$");
  private static final Pattern MULTI_DAY_OF_WEEK_RANGE_PATTERN = Pattern.compile("^(EXCEPT )?(SUN|MON|TUE|WED|THU|FRI|SAT)-(SUN|MON|TUE|WED|THU|FRI|SAT)$");
  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').trimResults();

  private static final Map<String, DayOfWeek> SHORT_TO_LONG_DAY_OF_WEEK =
      ImmutableMap.<String, DayOfWeek>builder()
          .put("SUN", DayOfWeek.SUNDAY)
          .put("MON", DayOfWeek.MONDAY)
          .put("TUE", DayOfWeek.TUESDAY)
          .put("WED", DayOfWeek.WEDNESDAY)
          .put("THU", DayOfWeek.THURSDAY)
          .put("FRI", DayOfWeek.FRIDAY)
          .put("SAT", DayOfWeek.SATURDAY)
          .build();

  private static final String BASE_REPORT_PREFIX = "BSE REPT: ";
  private static final Pattern BASE_EQUIPMENT_PATTERN = Pattern.compile("^Base/Equip: (CLT|DCA|CVG|DAY|TYS|TRG|NHF)/(CRJ)$");
  private static final Pattern CREW_MEMBERS = Pattern.compile("((FO|CA|FA)(\\d{2}))+?");
  private static final String OPERATES_PREFIX = "Operates: ";
  private static final String EXCEPT_PREFIX = "EXCEPT ON ";

  protected String parseHeader(Element row, Trip.Builder trip, int year) throws ParseException {
    Iterator<String> cells = expandCells(row).iterator();

    List<String> nameDate = TRIP_NAME_DATE_SPLITTER.splitToList(cells.next());
    if (nameDate.size() == 0) {
      throw new ParseException("Expected two nameDate segments at " + row);
    }
    String pairingName = nameDate.get(0);
    logger.fine("Parsing pairing " + pairingName);
    // TODO: this is broken because we have to guess at the year.
    LocalDate localDate = parseTripLocalDateWithYearHint(nameDate.get(1), year);
    trip.setPairingName(pairingName);
    trip.setStartDate(localDate.toString());

    String dayOfWeek = cells.next();
    if (!dayOfWeek.isEmpty()) {
      parseDayOfWeek(dayOfWeek, trip);
    }

    String baseReportTime = cells.next();
    if (!baseReportTime.isEmpty()) {
      checkState(baseReportTime.startsWith(BASE_REPORT_PREFIX),
          "unmatched base report time prefix " + baseReportTime);
      baseReportTime = baseReportTime.substring(BASE_REPORT_PREFIX.length());
    }

    // Operation range (Oct 4-Oct 11)
    if (cells.hasNext()) {
      String operatesText = cells.next();
      if (!operatesText.isEmpty()) {
        checkState(operatesText.startsWith(OPERATES_PREFIX),
            "unmatched operates prefix " + operatesText);
        trip.setOperates(operatesText.substring(OPERATES_PREFIX.length()));
      }
    }

    return baseReportTime;
  }
  
  void parseDayOfWeek(String dayOfWeekText, Trip.Builder trip) throws ParseException {
    if (dayOfWeekText.equals("EVERY DAY")) {
      addDayOfWeekRange(
          false /* except */,
          DayOfWeek.SUNDAY,
          DayOfWeek.SATURDAY,
          trip);
      return;
    }
    
    Matcher singleDayOfWeekMatcher = SINGLE_DAY_OF_WEEK_PATTERN.matcher(dayOfWeekText);
    if (singleDayOfWeekMatcher.matches()) {
      addSingleDayOfWeek(
          singleDayOfWeekMatcher.group(1),
          lookupShortDayOfWeek(singleDayOfWeekMatcher.group(2)),
          trip);
      return;
    }

    Matcher multiDayOfWeekRangeMatcher = MULTI_DAY_OF_WEEK_RANGE_PATTERN.matcher(dayOfWeekText);
    if (multiDayOfWeekRangeMatcher.matches()) {
      // EXCEPT MON-WED
      // "MON-WED"
      checkState(multiDayOfWeekRangeMatcher.group(1) == null
          || multiDayOfWeekRangeMatcher.group(1).equals("EXCEPT "),
          "unexpected token [" + multiDayOfWeekRangeMatcher.group(1) + "]");
      addDayOfWeekRange(
          "EXCEPT ".equals(multiDayOfWeekRangeMatcher.group(1)),
          lookupShortDayOfWeek(multiDayOfWeekRangeMatcher.group(2)),
          lookupShortDayOfWeek(multiDayOfWeekRangeMatcher.group(3)),
          trip);
      return;
    } else {
      // "MON WED"
      boolean except = false;
      if (dayOfWeekText.startsWith("EXCEPT ")) {
        except = true;
        dayOfWeekText = dayOfWeekText.substring("EXCEPT ".length());
      }
      List<DayOfWeek> daysSpecified = new ArrayList<>();
      for (String dayText : SPACE_SPLITTER.split(dayOfWeekText)) {
        daysSpecified.add(lookupShortDayOfWeek(dayText));
      }
      addDayOfWeekList(except, daysSpecified, trip);
    }
  }
  
  private DayOfWeek lookupShortDayOfWeek(String text) throws ParseException {
    checkState(text != null, "provided text was null");
    checkState(SHORT_TO_LONG_DAY_OF_WEEK.containsKey(text), "Unparseable " + text);
    return SHORT_TO_LONG_DAY_OF_WEEK.get(text);
  }

  private void addSingleDayOfWeek(String onlyOnOrExcept, DayOfWeek dayOfWeek, Trip.Builder trip) throws ParseException {
    if (onlyOnOrExcept.equals("ONLY ON")) {
      trip.addDayOfWeek(dayOfWeek);
    } else {
      checkState(onlyOnOrExcept.equals("EXCEPT"), "unrecognized verb: " + onlyOnOrExcept);
      addExcept(ImmutableList.of(dayOfWeek), trip);
    }
  }
  
  private void addDayOfWeekRange(boolean except, DayOfWeek start, DayOfWeek end, Trip.Builder trip) throws ParseException {
    checkState(start.ordinal() < end.ordinal(), "Assumed " + start + " is before " + end);
    List<DayOfWeek> daysSpecified = new ArrayList<>();
    for (int i = start.ordinal(); i <= end.ordinal(); ++i) {
      daysSpecified.add(DayOfWeek.values()[i]);
    }
    addDayOfWeekList(except, daysSpecified, trip);
  }
  
  private void addDayOfWeekList(boolean except, List<DayOfWeek> daysOfWeek, Trip.Builder trip) throws ParseException {
    if (except) {
      addExcept(daysOfWeek, trip);
    } else {
      trip.addAllDayOfWeek(daysOfWeek);
    }
  }
  
  private void addExcept(List<DayOfWeek> daysOfWeek, Trip.Builder trip) {
    for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
      if (!daysOfWeek.contains(dayOfWeek)) {
        trip.addDayOfWeek(dayOfWeek);
      }
    }
  }

  private void parseEquipmentAndRole(Element tr, Trip.Builder trip,
      Map<CrewPosition, Integer> expectedCrewMembers) throws ParseException {
    logger.fine(tr.toString());
    Iterator<Element> tds = tr.select("td").iterator();

    String baseEquipment = tds.next().text();
    Matcher baseEquipmentMatcher = BASE_EQUIPMENT_PATTERN.matcher(baseEquipment);
    checkState(baseEquipmentMatcher.matches(), "unmatched base equipment " + baseEquipment);
    trip.setBase(Domicile.valueOf(baseEquipmentMatcher.group(1)));
    trip.setEquipment(Equipment.valueOf(baseEquipmentMatcher.group(2)));

    String crewMember = tds.next().text();
    Matcher crewMemberMatcher = CREW_MEMBERS.matcher(crewMember);
    while (crewMemberMatcher.find()) {
      expectedCrewMembers.put(CrewPosition.valueOf(
          crewMemberMatcher.group(2)),
          Integer.parseInt(crewMemberMatcher.group(3)));
    }

    // EXCEPT ON Oct 24, Oct 25
    // EXCEPT ON Feb 21. Feb 25
    // See pojo.OperationDateExpander
    if (tds.hasNext()) {
      String exceptText = tds.next().text();
      if (!exceptText.isEmpty()) {
        checkState(exceptText.startsWith(EXCEPT_PREFIX),
            "unmatched except prefix " + exceptText);
        exceptText = exceptText.substring(EXCEPT_PREFIX.length());
        trip.setOperatesExcept(exceptText);
      }
    }
  }

  private void parseCrew(Element tr, Trip.Builder trip,
      Map<CrewPosition, Integer> expectedCrewMembers) throws ParseException {
    Iterator<Element> crewRows = tr.select("tr table tr").iterator();
    List<String> headers = expandCells(crewRows.next());
    checkState(headers.get(0).equals("Crew:"), "invalid crew header: " + headers);
    int numFirstOfficers = 0;
    int numFlightAttendants = 0;
    int numCaptains = 0;
    while (crewRows.hasNext()) {
      Element row = crewRows.next();
      Iterator<String> crewMemberText = expandCells(row).iterator();
      while (crewMemberText.hasNext()) {
        String expectedEmptyCell = crewMemberText.next();
        checkState(expectedEmptyCell.isEmpty(), "Expected empty: " + expectedEmptyCell);
        CrewMember.Builder crew = trip.addCrewBuilder();
        CrewPosition crewPosition = CrewPosition.valueOf(crewMemberText.next());
        crew.setCrewPosition(crewPosition);
        String crewEmployeeId = crewMemberText.next();
        if (!crewEmployeeId.isEmpty()) {
          crew.setEmployeeId(Integer.parseInt(crewEmployeeId));
        }
        crew.setName(crewMemberText.next());
        switch (crewPosition) {
          case CA: numCaptains++; break;
          case FO: numFirstOfficers++; break;
          case FA: numFlightAttendants++; break;
        }
      }
    }
    // Saw a trip which listed the same FO twice.
    if (numCaptains != expectedCrewMembers.getOrDefault(CrewPosition.CA, 0)) {
      logger.warning(trip.getPairingName() + ": expected " + expectedCrewMembers + ", numCaptains was " + numCaptains);
    }
    if (numFirstOfficers != expectedCrewMembers.getOrDefault(CrewPosition.FO, 0)) {
      logger.warning(
          trip.getPairingName() + ": expected " + expectedCrewMembers + ", numFirstOfficers was " + numFirstOfficers);
    }
    if (numFlightAttendants != expectedCrewMembers.getOrDefault(CrewPosition.FA, 0)) {
      logger.warning(trip.getPairingName() + ": expected " + expectedCrewMembers + ", numFlightAttendants was "
          + numFirstOfficers);
    }
  }
}
