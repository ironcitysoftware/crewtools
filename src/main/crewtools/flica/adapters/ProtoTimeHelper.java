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

import com.google.common.base.Preconditions;

import crewtools.flica.Proto;
import crewtools.util.TimeUtils;

// Weird to have a wrapper layer, then a pojo layer too.
// This handles date logic.
public class ProtoTimeHelper {

  private final TimeUtils timeUtils = new TimeUtils();
  private final Logger logger = Logger.getLogger(ProtoTimeHelper.class.getName());

  public LocalTime getArrivalLocalTime(Proto.Leg leg) {
    return timeUtils.parseLocalTime(leg.getArrivalLocalTime());
  }

  public LocalTime getLocalDutyEndTime(Proto.Section section) {
    return timeUtils.parseLocalTime(section.getLocalDutyEndTime());
  }

  /** Returns a DateTime relative to the given departureDate */
  public DateTime getDepartureDateTime(Proto.Leg leg, LocalDate legDate) {
    LocalTime departureTime = timeUtils.parseLocalTime(leg.getDepartureLocalTime());
    if (legDate.getDayOfMonth() != leg.getDayOfMonth()
        && legDate.plusDays(1).getDayOfMonth() == leg.getDayOfMonth()) {
      legDate = legDate.plusDays(1);
    } else {
    }

    Preconditions.checkState(legDate.getDayOfMonth() == leg.getDayOfMonth(),
        "legDate.DOM " + legDate.getDayOfMonth() + " != leg.DOM " + leg.getDayOfMonth());
    return timeUtils.getDateTime(
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
    LocalTime notReally = timeUtils.parseLocalTime(leg.getBlockDuration());
    LocalTime arrivalTime = timeUtils.parseLocalTime(leg.getArrivalLocalTime());
    return timeUtils.getDateTime(
        arrivalTime.isBefore(notReally) ? legDate.plusDays(1) : legDate,
        arrivalTime,
        leg.getArrivalAirportCode());
  }

  public DateTime getLocalDutyStartDateTime(Proto.Section section, LocalDate legDate) {
    LocalTime localDutyStartTime = timeUtils.parseLocalTime(section.getLocalDutyStartTime());
    if (section.hasLocalDutyStartDate()) {
      // We know the date, don't guess.
      return timeUtils.getDateTime(LocalDate.parse(section.getLocalDutyStartDate()),
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
    LocalTime firstLegStartTime = timeUtils.parseLocalTime(section.getLeg(0).getDepartureLocalTime());

    // use isAfter because the localDutyStartTime can == the first leg start time
    // in the event of a cancellation.
    return timeUtils.getDateTime(
        localDutyStartTime.isAfter(firstLegStartTime) ? legDate.minusDays(1) : legDate,
        localDutyStartTime,
        section.getLeg(0).getDepartureAirportCode());
  }

  public DateTime getLocalDutyEndDateTime(Proto.Section section, LocalDate legDate) {
    LocalTime startTime = timeUtils.parseLocalTime(section.getLocalDutyStartTime());
    LocalTime endTime = timeUtils.parseLocalTime(section.getLocalDutyEndTime());

    // If the local duty start time included a date, leg date is the date
    // of the end time (start time may be 11pm and end time 5pm next day).
    return timeUtils.getDateTime(
        section.hasLocalDutyStartDate() || endTime.isAfter(startTime) ? legDate : legDate.plusDays(1),
        endTime,
        section.getLeg(section.getLegCount() - 1).getArrivalAirportCode());
  }
}
