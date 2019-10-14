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
import static crewtools.flica.parser.ParseUtils.parseLocalTime;
import static crewtools.flica.parser.ParseUtils.parseTripLocalDateWithYearHint;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.LocalDate;
import org.jsoup.nodes.Element;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

import crewtools.flica.Proto.DayOfWeek;
import crewtools.flica.Proto.Equipment;
import crewtools.flica.Proto.Leg;
import crewtools.flica.Proto.LegType;
import crewtools.flica.Proto.Section;
import crewtools.flica.Proto.Trip;
import crewtools.flica.parser.SectionHeaders.Header;

public class SectionParser {
  private final Logger logger = Logger.getLogger(SectionParser.class.getName());

  private final String localReportTimeAndMaybeDate;  // May be blank, time, or time/date
  private final Iterator<Element> rows;
  private final Trip.Builder trip;
  private final int year;
  private SectionHeaders headers;

  private static final Map<String, LegType> LEG_TYPES =
      ParseUtils.getEnumValueMap(LegType.class);

  public SectionParser(String localReportTimeAndMaybeDate, Iterator<Element> rows, Trip.Builder trip, int year) {
    this.localReportTimeAndMaybeDate = localReportTimeAndMaybeDate;
    this.rows = rows;
    this.trip = trip;
    this.year = year;
    this.headers = new SectionHeaders();
  }

  private static final Map<String, DayOfWeek> SHORT_TO_LONG_DAY_OF_WEEK =
      ImmutableMap.<String, DayOfWeek>builder()
          .put("SU", DayOfWeek.SUNDAY)
          .put("MO", DayOfWeek.MONDAY)
          .put("TU", DayOfWeek.TUESDAY)
          .put("WE", DayOfWeek.WEDNESDAY)
          .put("TH", DayOfWeek.THURSDAY)
          .put("FR", DayOfWeek.FRIDAY)
          .put("SA", DayOfWeek.SATURDAY)
          .build();

  private static final Pattern BASE_REPORT_TIME_PATTERN = Pattern.compile("^(\\d{4})L$");

  public void parse() throws ParseException {
    initializeHeaders(rows.next());
    Section.Builder currentSection = null;

    String localReportTime = null;
    while (rows.hasNext()) {
      List<String> cells = expandCells(rows.next());
      // Flavors:
      //   end-of-section: D-END (NR 900) REPT hotel info
      //   end-of-trip-1:  D-END T.A.F.B
      //   end-of-trip-2:  Total:
      String secondColumn = cells.get(headers.indexOf(Header.DAY_OF_MONTH));
      if (secondColumn.startsWith("D-END:")) {
        if (!secondColumn.contains("T.A.F.B.:")) {
          logger.fine("SectionEnd: " + cells);
          localReportTime = parseDayEndAndHotelInfo(cells, currentSection);
          currentSection = null;
        } else {
          logger.fine("TripEnd1: " + cells);
          parseFirstLineTripTotals(cells, currentSection);
        }
      } else if (cells.get(headers.indexOf(Header.TRIP_TOTAL_LABEL)).equals("Total:")) {
        // this is the end of a trip
        logger.fine("TripEnd2: " + cells);
        parseSecondLineTripTotals(cells, currentSection);
      } else {
        // Starting a new or continuing an existing section.
        if (currentSection == null) {
          // Starting a new section.
          currentSection = trip.addSectionBuilder();
          if (trip.getSectionCount() == 1) {
            populateLocalDutyStartTimeAndMaybeDate(currentSection, this.localReportTimeAndMaybeDate, year);
          } else {
            checkState(localReportTime != null, "localReportTime has not been set");
            currentSection.setLocalDutyStartTime(localReportTime);
          }
        }
        logger.fine("Cells: " + cells);
        try {
          parseLeg(cells, currentSection, currentSection.addLegBuilder());
        } catch (Exception e) {
          System.out.println("ERROR LINE: " + cells);
          throw new IllegalStateException(e);
        }
      }
    }
  }

  private void populateLocalDutyStartTimeAndMaybeDate(Section.Builder section, String headerReportTime, int year) throws ParseException {
    List<String> parts = SPACE_SPLITTER.splitToList(headerReportTime);
    String timePortion;
    if (parts.size() == 2) {
      // TODO handle when base report date 30JUN is in base report
      LocalDate baseReportLocalDate = parseTripLocalDateWithYearHint(parts.get(0), year);
      section.setLocalDutyStartDate(baseReportLocalDate.toString());
      timePortion = parts.get(1);
    } else {
      timePortion = parts.get(0);
    }
    Matcher baseReportTimeMatcher = BASE_REPORT_TIME_PATTERN.matcher(timePortion);
    checkState(baseReportTimeMatcher.matches(), "unmatched base report time " + timePortion);
    section.setLocalDutyStartTime(baseReportTimeMatcher.group(1));
  }

  private void initializeHeaders(Element headerRow) throws ParseException {
    checkState(headerRow.attr("class").equals("main"), "unexpected headerRow class");
    List<String> headerCells = expandCells(headerRow);
    for (int i = 0; i < headerCells.size(); ++i) {
      String header = headerCells.get(i);
      if (i > 0 && header.isEmpty()) {
        String lastHeader = headerCells.get(i - 1);
        if (lastHeader.equals("OA") || lastHeader.equals("EQP")) {
          // Ugh special case the label which isn't listed as header.
          headers.add(Header.TRIP_TOTAL_LABEL);
          continue;
        }
      }
      headers.add(header);
    }
  }

  private static final Pattern CITY_PAIR_PATTERN = Pattern.compile("^([A-Z0-9]{3})-([A-Z0-9]{3})$");
  private static final Splitter SLASH_SPLITTER = Splitter.on('/').omitEmptyStrings().trimResults();
  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').omitEmptyStrings().trimResults();

  private void parseLeg(List<String> cells, Section.Builder section, Leg.Builder leg) throws ParseException {
    String dayOfWeek = cells.get(headers.indexOf(Header.DAY_OF_WEEK));
    leg.setDayOfWeek(SHORT_TO_LONG_DAY_OF_WEEK.get(dayOfWeek));

    String dayOfMonth = cells.get(headers.indexOf(Header.DAY_OF_MONTH));
    leg.setDayOfMonth(Integer.parseInt(dayOfMonth));

    if (cells.get(headers.indexOf(Header.IS_DEADHEAD)).equals("DH")) {
      leg.setIsDeadhead(true);
    }

    String changePlanesAfterThisFlight = cells.get(headers.indexOf(Header.CHANGE_PLANES));
    if (changePlanesAfterThisFlight.equals("*")) {
      leg.setIsEquipmentSwapUponCompletion(true);
    }

    String flightNumber = cells.get(headers.indexOf(Header.FLIGHT_NUMBER));
    if (LEG_TYPES.containsKey(flightNumber)) {
      leg.setLegType(LEG_TYPES.get(flightNumber));
    } else {
      if (flightNumber.startsWith("`")) {
        flightNumber = flightNumber.substring(1);
      }
      leg.setFlightNumber(Integer.parseInt(flightNumber));
    }

    String cityPair = cells.get(headers.indexOf(Header.CITY_PAIR));
    Matcher cityPairMatcher = CITY_PAIR_PATTERN.matcher(cityPair);
    checkState(cityPairMatcher.matches(), "unmatched city pair: " + cityPair);
    leg.setDepartureAirportCode(cityPairMatcher.group(1));
    leg.setArrivalAirportCode(cityPairMatcher.group(2));

    leg.setDepartureLocalTime(cells.get(headers.indexOf(Header.DEPARTURE_LOCAL_TIME)));
    leg.setArrivalLocalTime(cells.get(headers.indexOf(Header.ARRIVAL_LOCAL_TIME)));
    leg.setBlockDuration(cells.get(headers.indexOf(Header.BLOCK_DURATION)));

    String groundDuration = cells.get(headers.indexOf(Header.GROUND_DURATION));
    if (!groundDuration.isEmpty()) {
      leg.setGroundDuration(groundDuration);
    }

    String otherAirline = cells.get(headers.indexOf(Header.OTHER_AIRLINE));
    if (!otherAirline.isEmpty()) {
      leg.setOtherAirline(otherAirline);
    }

    if (headers.hasHeader(Header.EQUIPMENT)) {
      String equipment = cells.get(headers.indexOf(Header.EQUIPMENT));
      if (!equipment.isEmpty()) {
        leg.setEquipment(Equipment.valueOf(equipment));
      }
    }

    int sectionBlockDurationIndex = headers.indexOf(Header.SECTION_BLOCK_DURATION);
    String sectionBlockDuration = cells.get(sectionBlockDurationIndex);
    if (!sectionBlockDuration.isEmpty()) {
      // May 13 2017 has a hotel right in the middle, prefixed with "COD"
      if (!cells.get(sectionBlockDurationIndex - 1).equals("COD")) {
        section.setBlockDuration(sectionBlockDuration);
        parseSectionTotals(section, cells);
      }
    }
  }

  private void parseSectionTotals(Section.Builder section, List<String> cells) throws ParseException {
    String sectionDeadheadDuration = cells.get(headers.indexOf(Header.SECTION_DEADHEAD_DURATION));
    if (!sectionDeadheadDuration.isEmpty()) {
      section.setDeadheadDuration(sectionDeadheadDuration);
    }
    section.setCreditDuration(cells.get(headers.indexOf(Header.SECTION_CREDIT_DURATION)));

    String dutyTimesText = cells.get(headers.indexOf(Header.DUTY_TIMES));
    List<String> dutyTimes = SLASH_SPLITTER.splitToList(dutyTimesText);
    checkState(dutyTimes.size() == 2, "unexpected duty time format: " + dutyTimesText);
    section.setDutyDuration(dutyTimes.get(0));
    section.setFlightDutyDuration(dutyTimes.get(1));

    String layoverListText = cells.get(headers.indexOf(Header.LAYOVER));
    List<String> layoverList = SPACE_SPLITTER.splitToList(layoverListText);
    if (!layoverList.isEmpty()) {
      checkState(layoverList.size() == 2, "unexpected layover format: " + layoverList);
      section.setLayoverAirportCode(layoverList.get(0));
      section.setLayoverDuration(layoverList.get(1));
    }
  }

  private String parseDayEndAndHotelInfo(List<String> cells, Section.Builder section) throws ParseException {
    // TODO: use header indices.
    Iterator<String> timeComponents = SPACE_SPLITTER.split(cells.get(1)).iterator();
    checkState(timeComponents.next().equals("D-END:"), "Missing D-END label: " + cells);
    section.setLocalDutyEndTime(parseLocalTime(timeComponents.next()));

    // TODO: what is (NR 900)?
    String next = timeComponents.next();
    if (next.equals("(NR")) {
      checkState(timeComponents.next().equals("900)"), "Expected NR 900.");
      next = timeComponents.next();
    }

    checkState(next.equals("REPT:"), "Missing REPT label: " + cells);
    String nextDayReportTime = parseLocalTime(timeComponents.next());
    checkState(!timeComponents.hasNext(), "Too few time components: " + cells);

    String hotelName = cells.get(10);
    if (!hotelName.isEmpty()) {
      section.setHotelName(hotelName);
    }
    String hotelPhoneNumber = cells.get(15);
    if (!hotelPhoneNumber.isEmpty()) {
      section.setHotelPhoneNumber(hotelPhoneNumber);
    }
    return nextDayReportTime;
  }

  private void parseFirstLineTripTotals(List<String> row, Section.Builder section) throws ParseException {
    Iterator<String> components = SPACE_SPLITTER.split(row.get(1)).iterator();

    // D-END
    checkState(components.next().equals("D-END:"), "Missing D-END label: " + row);
    section.setLocalDutyEndTime(parseLocalTime(components.next()));

    // Time Away From Base
    checkState(components.next().equals("T.A.F.B.:"), "Missing TAFB label: " + row);
    trip.setTimeAwayFromBaseDuration(components.next());

    if (components.hasNext()) {
      // either DHD or TRIP RIG
      String next = components.next();
      if (next.equals("DHD:")) {
        trip.setDeadheadDuration(components.next());
      } else {
        checkState(next.equals("TRIP"), "Expected DHD or 'TRIP' RIG: " + row + "[" + next + "]");
        next = components.next();
        checkState(next.equals("RIG:"), "Expected DHD or TRIP 'RIG:' " + row + "[" + next + "]");
        trip.setTripRigDuration(components.next());
      }
    }

    // We're done.
    checkState(!components.hasNext(), "Trailing trip-end-line-1 data: " + row);
  }

  private void parseSecondLineTripTotals(List<String> row, Section.Builder section) throws ParseException {
    checkState(row.get(headers.indexOf(Header.TRIP_TOTAL_LABEL)).equals("Total:"), "Missing total label: " + row);
    trip.setBlockDuration(row.get(headers.indexOf(Header.SECTION_BLOCK_DURATION)));
    trip.setDeadheadDuration(row.get(headers.indexOf(Header.SECTION_DEADHEAD_DURATION)));
    trip.setCreditDuration(row.get(headers.indexOf(Header.SECTION_CREDIT_DURATION)));

    String dutyTimesText = row.get(headers.indexOf(Header.DUTY_TIMES));
    List<String> dutyTimes = SLASH_SPLITTER.splitToList(dutyTimesText);
    checkState(dutyTimes.size() == 2, "Unexpected duty times format (trip-end-line-2): " + row);
    trip.setDutyDuration(dutyTimes.get(0));
    trip.setFlightDutyDuration(dutyTimes.get(1));
  }
}
