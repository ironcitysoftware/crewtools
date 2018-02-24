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

package crewtools.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto;
import crewtools.flica.Proto.Leg;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.Trip;
import crewtools.util.Period;

public class TripBuilder {
  public static final YearMonth DEFAULT_YEAR_MONTH = YearMonth.parse("2018-01");
  public static final LocalDate DEFAULT_DAY = DEFAULT_YEAR_MONTH.toLocalDate(1);
  public static final int DEFAULT_START_HOUR_OF_DAY = 5;
  
  private static class Event {
    private Event(EventType eventType) {
      this.eventType = eventType;
    }
    enum EventType {
      LEG,
      LAYOVER,
      DAY_OF_MONTH,
      NAME;
    }
    EventType eventType;
    String airportCode;
    String toAirportCode;
    Period period;
    int dayOfMonth;
    String name;
  }
  
  private final List<Event> events = new ArrayList<>();

  public TripBuilder withDayOfMonth(int dayOfMonth) {
    Event dom = new Event(Event.EventType.DAY_OF_MONTH);
    dom.dayOfMonth = dayOfMonth;
    events.add(dom);
    return this;
  }
  
  public TripBuilder withName(String name) {
    Event event = new Event(Event.EventType.NAME);
    event.name = name;
    events.add(event);
    return this;
  }
  
  public TripBuilder withLeg(String fromAirportCode, String toAirportCode, Period block) {
    Event leg = new Event(Event.EventType.LEG);
    leg.airportCode = fromAirportCode;
    leg.toAirportCode = toAirportCode;
    leg.period = block;
    events.add(leg);
    return this;
  }
  
  public TripBuilder withLayover(String airportCode, Period period) {
    Event layover = new Event(Event.EventType.LAYOVER);
    layover.airportCode = airportCode;
    layover.period = period;
    events.add(layover);
    return this;
  }
  
  public Trip build() {
    String tripName = null;
    Period sectionBlock = Period.ZERO;
    Period tripBlock = Period.ZERO;
    List<Section> sections = new ArrayList<>();
    LocalDate currentDate = DEFAULT_DAY;
    LocalDate startDate = null;
    Set<LocalDate> departureDates = new HashSet<>();
    int currentHourOfDay = DEFAULT_START_HOUR_OF_DAY;
    Proto.Trip.Builder tripBuilder = Proto.Trip.newBuilder();
    Proto.Section.Builder currentSection = null; 
    DateTime sectionStart = null;
    DateTime sectionEnd = null;
    for (Event event : events) {
      switch (event.eventType) {
      case DAY_OF_MONTH:
        currentDate = DEFAULT_YEAR_MONTH.toLocalDate(event.dayOfMonth);
        continue;
      case LEG:
        if (sections.isEmpty()) {
          startDate = currentDate;
        }
        if (currentSection == null) {
          currentSection = tripBuilder.addSectionBuilder();
          departureDates.add(currentDate);
        }
        if (currentSection.getLegCount() == 0) {
          currentSection.setLocalDutyStartDate(currentDate.toString());
          currentSection.setLocalDutyStartTime(String.format("%02d00", currentHourOfDay));
          sectionStart = currentDate.toDateTimeAtStartOfDay().withHourOfDay(currentHourOfDay);
        }
        Proto.Leg.Builder leg = currentSection.addLegBuilder();
        leg.setDayOfMonth(currentDate.getDayOfMonth());
        leg.setBlockDuration(event.period.toHhMmString());
        leg.setDepartureLocalTime(String.format("%02d00", currentHourOfDay));
        leg.setDepartureAirportCode(event.airportCode);
        leg.setArrivalAirportCode(event.toAirportCode);
        currentHourOfDay += event.period.getHours();
        // TODO minutes
        Preconditions.checkState(currentHourOfDay < 24);
        leg.setArrivalLocalTime(String.format("%02d00", currentHourOfDay));
        sectionEnd = sectionStart.withHourOfDay(currentHourOfDay);
        sectionBlock = sectionBlock.plus(event.period);
        tripBlock = tripBlock.plus(event.period);
        continue;
      case LAYOVER:
        Preconditions.checkNotNull(currentSection);
        currentSection.setLayoverAirportCode(event.airportCode);
        currentSection.setLayoverDuration(event.period.toHhMmString());
        Leg.Builder lastLeg = currentSection.getLegBuilder(currentSection.getLegCount() - 1);
        currentSection.setLocalDutyEndTime(lastLeg.getArrivalLocalTime());
        sections.add(new Section(currentSection.build(),
            currentDate,
            sectionBlock, sectionBlock,
            sectionStart, sectionEnd));
        currentSection = null;
        sectionBlock = Period.ZERO;
        currentDate = currentDate.plusDays(1);
        currentHourOfDay = DEFAULT_START_HOUR_OF_DAY;
        continue;
      case NAME:
        tripName = event.name;
        continue;
      }
    }
    tripBuilder.setStartDate(startDate.toString());
    if (tripName == null) {
      tripName = "L" + startDate.toString();
    }
    tripBuilder.setPairingName(tripName);
    return new Trip(sections,
        tripBlock,  // block
        tripBlock,  // credit
        tripBlock,  // tafb
        departureDates,
        tripBuilder.build());
  }
}
