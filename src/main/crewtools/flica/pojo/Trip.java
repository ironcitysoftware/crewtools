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
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import crewtools.flica.Proto;
import crewtools.flica.Proto.CrewPosition;
import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.parser.ParseUtils;
import crewtools.util.Calendar;
import crewtools.util.Period;
import crewtools.util.TimeUtils;

/**
 * A trip is a pairing or event associated with a particular set of date.
 * This includes training, vacation, pairing, etc.
 */
public class Trip implements Comparable<Trip> {
  private final TimeUtils timeUtils = new TimeUtils();
  private final Logger logger = Logger.getLogger(Trip.class.getName());

  public Trip(List<Section> sections, Period block, Period credit,
      Period tafb, Period duty,
      Set<LocalDate> departureDates,
      Proto.Trip proto) {
    Preconditions.checkArgument(!departureDates.isEmpty());
    this.sections = sections;
    this.block = block;
    this.credit = credit;
    this.tafb = tafb;
    this.duty = duty;
    this.proto = proto;
    this.departureDates = departureDates;
    this.earliestDepartureDate = Ordering.natural().min(departureDates);
  }

  public String getPairingName() {
    if (proto.hasScheduleType()) {
      return ParseUtils.getFlicaName(proto.getScheduleType());
    } else {
      return proto.getPairingName();
    }
  }

  public int getStartingDayOfMonth() {
    return earliestDepartureDate.getDayOfMonth();
  }

  public LocalDate getEarliestDepartureDate() {
    return earliestDepartureDate;
  }

  // TODO: define this list.
  private static final Set<ScheduleType> UNDROPPABLE_SCHEDULE_TYPES = ImmutableSet.of(
      ScheduleType.RELOCATION_DAY,
      ScheduleType.PHONE_CALL,
      ScheduleType.MEDICAL_LEAVE,
      ScheduleType.SICK,
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

  public List<Section> getSections() {
    return sections;
  }

  // A section starts on a certain day but may finish on another day.
  private List<Section> sections;
  public Period block;
  public Period credit;
  public Period tafb;
  private Period duty;
  public Proto.Trip proto;
  public List<String> scoreInfo = new ArrayList<>();
  private Set<LocalDate> departureDates;
  private LocalDate earliestDepartureDate;

  public boolean isTwoHundred() {
    if (proto.getEquipment().equals(Proto.Equipment.RJ2)) {
      return true;
    }
    for (Section section : sections) {
      if (section.isEquipmentTwoHundred()) {
        return true;
      }
    }
    return false;
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

  public Period getDuty() {
    return duty;
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

  public boolean containsCrewmember(List<Integer> employeeIds) {
    for (Proto.CrewMember crewmember : proto.getCrewList()) {
      if (employeeIds.contains(crewmember.getEmployeeId())) {
        return true;
      }
    }
    return false;
  }

  public boolean spansDaysOfMonth(List<Integer> daysOfMonthList) {
    for (LocalDate date : getDepartureDates()) {
      if (daysOfMonthList.contains(date.getDayOfMonth())) {
        return true;
      }
    }
    return false;
  }

  /** Does not include vacation. */
  public Map<LocalDate, Period> getDailyCreditInMonth(YearMonth yearMonth) {
    ImmutableMap.Builder<LocalDate, Period> result = ImmutableMap.builder();
    for (Section section : sections) {
      result.put(section.date, section.credit);
    }
    return result.build();
  }

  public Period getCredit() {
    return credit;
  }

  /** Carry-in credit will override any trip credit for the specified day. */
  public Period getCreditInMonth(List<Integer> vacationDays, YearMonth yearMonth,
      Map<LocalDate, Period> carryInCredit) {
    Calendar calendar = new Calendar(yearMonth);
    Period credit = Period.ZERO;

    LocalDate priorDay = earliestDepartureDate.minusDays(1);
    // We may need a day off between the carry ins and this trip.
    boolean needFirstDayOff = false;
    if (carryInCredit.size() + sections.size() > 5
        && calendar.isWithinPeriod(priorDay)
        && carryInCredit.containsKey(priorDay)) {
      needFirstDayOff = true;
    }

    for (Section section : sections) {
      if (needFirstDayOff && section.date.equals(earliestDepartureDate)) {
        // This day will probably get dropped in the blend.
        continue;
      } else if (!calendar.isWithinPeriod(section.date)) {
        // This day of the trip is in a different bid period.
        continue;
      } else if (vacationDays.contains(section.date.getDayOfMonth())) {
        // This day of the trip overlaps with vacation.
        continue;
      } else if (carryInCredit.containsKey(section.date)) {
        // Blend; use the scheduled trip credit to be conservative.
        credit = credit.plus(carryInCredit.get(section.date));
      } else {
        credit = credit.plus(section.credit);
      }
    }
    return credit;
  }

  private static final DateTimeZone EASTERN = DateTimeZone.forID("America/New_York");

  public DateTime getDutyStart() {
    if (!proto.hasScheduleType()) {
      return getFirstSection().getStart();
    } else if (proto.getScheduleType() == ScheduleType.VACATION
        || proto.getScheduleType() == ScheduleType.VACATION_START
        || proto.getScheduleType() == ScheduleType.VACATION_END
        || proto.getScheduleType() == ScheduleType.RELOCATION_DAY
        || proto.getScheduleType() == ScheduleType.SICK
        || proto.getScheduleType() == ScheduleType.LONG_CALL_RESERVE) {
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
        || proto.getScheduleType() == ScheduleType.VACATION_END
        || proto.getScheduleType() == ScheduleType.RELOCATION_DAY
        || proto.getScheduleType() == ScheduleType.SICK
        || proto.getScheduleType() == ScheduleType.LONG_CALL_RESERVE) {
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
    int result = earliestDepartureDate.compareTo(that.earliestDepartureDate);
    if (result != 0) {
      return result;
    }
    return new Integer(proto.hashCode()).compareTo(that.proto.hashCode());
  }

  public List<Leg> getLegs() {
    List<Leg> legs = new ArrayList<>();
    for (Section section : sections) {
      legs.addAll(section.getLegs());
    }
    return legs;
  }
}
