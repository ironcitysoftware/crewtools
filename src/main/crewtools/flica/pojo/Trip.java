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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto;
import crewtools.flica.Proto.CrewPosition;
import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.parser.ParseUtils;
import crewtools.util.Period;
import crewtools.util.TimeUtils;

/**
 * A trip is a pairing associated with a particular date.
 */
public class Trip implements Comparable<Trip> {
  private final TimeUtils timeUtils = new TimeUtils();
  private final Logger logger = Logger.getLogger(Trip.class.getName());
  
  public Trip(List<Section> sections, Period block, Period credit, Period tafb,
      Set<LocalDate> departureDates,
      Proto.Trip proto) {
    this.sections = sections;
    this.block = block;
    this.credit = credit;
    this.tafb = tafb;
    this.proto = proto;
    this.departureDates = departureDates;
  }

  public String getPairingName() {
    if (proto.hasScheduleType()) {
      return ParseUtils.getFlicaName(proto.getScheduleType());
    } else {
      return proto.getPairingName();
    }
  }
  
  // TODO: define this list.
  private static final Set<ScheduleType> UNDROPPABLE_SCHEDULE_TYPES = ImmutableSet.of(
      ScheduleType.RELOCATION_DAY,
      ScheduleType.PHONE_CALL,
      ScheduleType.MEDICAL_LEAVE,
      ScheduleType.VACATION_START,
      ScheduleType.VACATION_END,
      ScheduleType.VACATION);
  
  public boolean isDroppable() {
    if (proto.hasScheduleType() 
        && UNDROPPABLE_SCHEDULE_TYPES.contains(proto.getScheduleType())) {
      return false;
    }
    for (Section section : sections) {
      if (!section.isDroppable()) {
        return false;
      }
    }
    return true;
  }
  
  public Set<LocalDate> getDepartureDates() {
    return departureDates;
  }
  
  // A section starts on a certain day but may finish on another day.
  public List<Section> sections;
  public Period block;
  public Period credit;
  public Period tafb;
  public Proto.Trip proto;
  public List<String> scoreInfo = new ArrayList<>();
  private Set<LocalDate> departureDates;
  
  public boolean isTwoHundred() {
    return proto.getEquipment().equals(Proto.Equipment.RJ2);
  }

  // This is the first date of departure, not the date of the first show time.
  public Section getFirstSection() {
    return sections.get(0);
  }
  
  public Section getLastSection() {
    return sections.get(sections.size() - 1);
  }
  
  public boolean hasScheduleType() {
    return proto.hasScheduleType();
  }
  
  public int getNumSections() {
    return sections.size();
  }

  public PairingKey getPairingKey() {
    return new PairingKey(getFirstSection().getShowDate(), proto.getPairingName());
  }

  private static final String OPEN = "Open";

  public boolean isOpenForPosition(CrewPosition position) {
    for (Proto.CrewMember crewmember : proto.getCrewList()) {
      if (crewmember.getCrewPosition().equals(position)
          && crewmember.getName().equals(OPEN)) {
        return true;
      }
    }
    return false;
  }

  /** Does not include vacation. */
  public Period getCreditInMonth(YearMonth yearMonth) {
    Period outOfMonthCredit = Period.ZERO;
    for (Section section : sections) {
      if (section.date.getMonthOfYear() != yearMonth.getMonthOfYear()) {
        outOfMonthCredit = outOfMonthCredit.plus(section.credit);
      }
    }
    return credit.minus(outOfMonthCredit);
  }

  public Period getCreditInMonth(Set<Integer> vacationDays, YearMonth yearMonth) {
    Period outOfMonthCredit = Period.ZERO;
    Period vacationCredit = Period.ZERO;
    for (Section section : sections) {
      if (section.date.getMonthOfYear() != yearMonth.getMonthOfYear()) {
        outOfMonthCredit = outOfMonthCredit.plus(section.credit);
      } else if (vacationDays.contains(section.date.getDayOfMonth())) {
        vacationCredit = vacationCredit.plus(section.credit);
      }
    }
    return credit.minus(outOfMonthCredit).minus(vacationCredit);
  }
  
  private static final DateTimeZone EASTERN = DateTimeZone.forID("America/New_York");

  public DateTime getDutyStart() {
    if (!proto.hasScheduleType()) {
      return getFirstSection().getStart();
    } else if (proto.getScheduleType() == ScheduleType.VACATION
        || proto.getScheduleType() == ScheduleType.VACATION_START
        || proto.getScheduleType() == ScheduleType.VACATION_END) {
      // TODO Daylight savings?
      return LocalDate.parse(proto.getStartDate())
          .toDateTime(timeUtils.parseLocalTimeWithColon(proto.getStartTime()), EASTERN);
    } else {
      throw new IllegalStateException("Need to handle nonTrip interval: " + proto);
    }
  }

  public DateTime getDutyEnd() {
    if (!proto.hasScheduleType()) {
      return getLastSection().getEnd();
    } else if (proto.getScheduleType() == ScheduleType.VACATION
        || proto.getScheduleType() == ScheduleType.VACATION_START
        || proto.getScheduleType() == ScheduleType.VACATION_END) {
      // TODO Daylight savings?
      return LocalDate.parse(proto.getEndDate())
          .toDateTime(timeUtils.parseLocalTimeWithColon(proto.getEndTime()), EASTERN);
    } else {
      throw new IllegalStateException("Need to handle nonTrip interval: " + proto);
    }
  }

  public Interval getInterval() {
    return new Interval(getDutyStart(), getDutyEnd());
  }

  @Override
  public String toString() {
    return proto.toString();
  }
  
  @Override
  public boolean equals(Object that) {
    if (that == null) { return false; }
    if (!(that instanceof Trip)) { return false; }
    return ((Trip) that).proto.equals(proto);
  }

  @Override
  public int hashCode() {
    return proto.hashCode();
  }

  @Override
  public int compareTo(Trip that) {
    return new Integer(proto.hashCode()).compareTo(that.proto.hashCode());
  }
}
