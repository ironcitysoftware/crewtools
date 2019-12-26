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

package crewtools.flica.pojo;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.YearMonth;

import crewtools.flica.Proto;
import crewtools.util.Period;
import crewtools.util.TimeUtils;

public class Leg {
  private final Proto.Leg protoLeg;
  private final YearMonth yearMonth;
  private final DateTime sectionDutyStart;
  private final int tripIndex;
  private final TimeUtils timeUtils = new TimeUtils();

  public Leg(Proto.Leg protoLeg, DateTime sectionDutyStart,
      int tripIndex, YearMonth yearMonth) {
    this.protoLeg = protoLeg;
    this.yearMonth = yearMonth;
    this.sectionDutyStart = sectionDutyStart;
    this.tripIndex = tripIndex;
  }

  public String getGroundDuration() {
    return protoLeg.getGroundDuration();
  }

  public boolean hasLegType() {
    return protoLeg.hasLegType();
  }

  public Proto.LegType getLegType() {
    return protoLeg.getLegType();
  }

  public boolean isDeadhead() {
    return protoLeg.getIsDeadhead();
  }

  public Period getBlockDuration() {
    return Period.fromText(protoLeg.getBlockDuration());
  }

  public int getFlightNumber() {
    return protoLeg.getFlightNumber();
  }

  public String getDepartureAirportCode() {
    return protoLeg.getDepartureAirportCode();
  }

  public String getArrivalAirportCode() {
    return protoLeg.getArrivalAirportCode();
  }

  /**
   * It is possible that arrival and departure happen the next day;
   * (eg show between 23:15 and 23:59)
   */
  public LocalDate getDate() {
    return yearMonth.toLocalDate(protoLeg.getDayOfMonth());
  }

  // TODO verify that this is true 100% of the time. I don't think it is.
  private LocalDate getDepartureDate() {
    LocalDate departureDate = getDate();
    if (tripIndex == 0
        && sectionDutyStart.getHourOfDay() == 23
        && sectionDutyStart.getMinuteOfHour() >= 15) {
      departureDate = departureDate.plusDays(1);
    }
    return departureDate;
  }

  public LocalTime getDepartureLocalTime() {
    return timeUtils.parseLocalTime(protoLeg.getDepartureLocalTime());
  }

  public DateTime getDepartureTime() {
    return timeUtils.getDateTime(
        getDepartureDate(),
        timeUtils.parseLocalTime(protoLeg.getDepartureLocalTime()),
        protoLeg.getDepartureAirportCode());
  }

  public LocalTime getArrivalLocalTime() {
    return timeUtils.parseLocalTime(protoLeg.getArrivalLocalTime());
  }

  public DateTime getArrivalTime() {
    DateTime provisional = timeUtils.getDateTime(
        getDepartureDate(),
        getArrivalLocalTime(),
        protoLeg.getArrivalAirportCode());
    if (provisional.isAfter(getDepartureTime())) {
      return provisional;
    } else {
      return provisional.plusDays(1);
    }
  }

  public String toProtoString() {
    return protoLeg.toString();
  }

  @Override
  public String toString() {
    return String.format("JIA%d %s->%s",
        protoLeg.getFlightNumber(),
        protoLeg.getDepartureAirportCode(),
        protoLeg.getArrivalAirportCode());
  }

  @Override
  public int hashCode() {
    return protoLeg.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == null) {
      return false;
    }
    if (!(object instanceof Leg)) {
      return false;
    }
    Leg that = (Leg) object;
    return protoLeg.equals(that.protoLeg);
  }
}
