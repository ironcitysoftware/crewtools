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

package crewtools.flica.adapters;

import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import com.google.common.base.Preconditions;

import crewtools.flica.Proto;
import crewtools.flica.StationZoneProvider;

// Weird to have a wrapper layer, then a pojo layer too.
// This handles date logic.
public class ProtoTimeHelper {

  private final Logger logger = Logger.getLogger(ProtoTimeHelper.class.getName());

  public Period getBlockDuration(Proto.Leg leg) {
    return parseProtoPeriod(leg.getBlockDuration());
  }

  public Period getCreditDuration(Proto.Section section) {
    return parseProtoPeriod(section.getCreditDuration());
  }

  public LocalTime getArrivalLocalTime(Proto.Leg leg) {
    return parseProtoTime(leg.getArrivalLocalTime());
  }

  public LocalTime getLocalDutyEndTime(Proto.Section section) {
    return parseProtoTime(section.getLocalDutyEndTime());
  }

  /** Returns a DateTime relative to the given departureDate */
  public DateTime getDepartureDateTime(Proto.Leg leg, LocalDate legDate) {
    LocalTime departureTime = parseProtoTime(leg.getDepartureLocalTime());
    if (legDate.getDayOfMonth() != leg.getDayOfMonth()
        && legDate.plusDays(1).getDayOfMonth() == leg.getDayOfMonth()) {
      legDate = legDate.plusDays(1);
    } else {
    }

    Preconditions.checkState(legDate.getDayOfMonth() == leg.getDayOfMonth(),
        "legDate.DOM " + legDate.getDayOfMonth() + " != leg.DOM " + leg.getDayOfMonth());
    return getDateTime(
        legDate,
        departureTime,
        leg.getDepartureAirportCode());
  }

  /** Returns a DateTime relative to the given arrivalDate */
  public DateTime getArrivalDateTime(Proto.Leg leg, LocalDate legDate) {
    // July 1, 2017
    if (legDate.getDayOfMonth() != leg.getDayOfMonth()
        && legDate.plusDays(1).getDayOfMonth() == leg.getDayOfMonth()) {
      legDate = legDate.plusDays(1);
    }
    Preconditions.checkState(legDate.getDayOfMonth() == leg.getDayOfMonth());
    LocalTime notReally = parseProtoTime(leg.getBlockDuration());
    LocalTime arrivalTime = parseProtoTime(leg.getArrivalLocalTime());
    return getDateTime(
        arrivalTime.isBefore(notReally) ? legDate.plusDays(1) : legDate,
        arrivalTime,
        leg.getArrivalAirportCode());
  }

  public DateTime getLocalDutyStartDateTime(Proto.Section section, LocalDate legDate) {
    LocalTime localDutyStartTime = parseProtoTime(section.getLocalDutyStartTime());
    if (section.hasLocalDutyStartDate()) {
      // We know the date, don't guess.
      return getDateTime(
          parseProtoDate(section.getLocalDutyStartDate()),
          localDutyStartTime,
          section.getLeg(0).getDepartureAirportCode());
    }
    // Otherwise, make an educated guess.

    // First, make sure the leg date is correct for the first leg.
    if (legDate.getDayOfMonth() != section.getLeg(0).getDayOfMonth()
        && legDate.plusDays(1).getDayOfMonth() == section.getLeg(0).getDayOfMonth()) {
      legDate = legDate.plusDays(1);
    }

    // L7703
    LocalTime firstLegStartTime = parseProtoTime(section.getLeg(0).getDepartureLocalTime());

    // use isAfter because the localDutyStartTime can == the first leg start time
    // in the event of a cancellation.
    return getDateTime(
        localDutyStartTime.isAfter(firstLegStartTime) ? legDate.minusDays(1) : legDate,
        localDutyStartTime,
        section.getLeg(0).getDepartureAirportCode());
  }

  public DateTime getLocalDutyEndDateTime(Proto.Section section, LocalDate legDate) {
    LocalTime startTime = parseProtoTime(section.getLocalDutyStartTime());
    LocalTime endTime = parseProtoTime(section.getLocalDutyEndTime());

    // If the local duty start time included a date, leg date is the date
    // of the end time (start time may be 11pm and end time 5pm next day).
    return getDateTime(
        section.hasLocalDutyStartDate() || endTime.isAfter(startTime) ? legDate : legDate.plusDays(1),
        endTime,
        section.getLeg(section.getLegCount() - 1).getArrivalAirportCode());
  }

  private PeriodFormatter HHMM_PERIOD =
      new PeriodFormatterBuilder().maximumParsedDigits(2).appendHours().appendMinutes().toFormatter();

  public Period parseProtoPeriod(String protoHhMmField) {
    return HHMM_PERIOD.parsePeriod(protoHhMmField);
  }

  private DateTimeFormatter HHMM_LOCALTIME =
      DateTimeFormat.forPattern("HHmm");

  LocalTime parseProtoTime(String protoHhMmField) {
    return LocalTime.parse(protoHhMmField, HHMM_LOCALTIME);
  }

  LocalDate parseProtoDate(String protoDateField) {
    return LocalDate.parse(protoDateField);
  }

  private StationZoneProvider zoneProvider = new StationZoneProvider();

  private DateTime getDateTime(LocalDate date, LocalTime time, String station) {
    return date.toDateTime(time, zoneProvider.getDateTimeZone(station));
  }
}
