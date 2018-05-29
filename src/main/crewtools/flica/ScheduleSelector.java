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

package crewtools.flica;

import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.ReadableInstant;
import org.joda.time.YearMonth;

import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.parser.ScheduleParser;
import crewtools.flica.pojo.Leg;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.Trip;
import crewtools.util.Clock;
import crewtools.util.FlicaConfig;
import crewtools.util.SystemClock;

public class ScheduleSelector {
  private static final Logger logger = Logger.getLogger(ScheduleSelector.class.getName());

  private Clock clock;

  public static void main(String args[]) throws Exception {
    doIt(new YearMonth(2018, 5));
  }

  public static void doIt(YearMonth yearMonth) throws Exception {
    FlicaConnection connection = new FlicaConnection(new FlicaConfig());
    FlicaService service = new FlicaService(connection);

    ScheduleSelector selector = new ScheduleSelector(new SystemClock());
    Schedule thisMonth = getSchedule(service, yearMonth);
    Leg currentLeg = selector.getCurrentLeg(thisMonth);
    if (currentLeg != null) {
      System.out.println("Current leg: " + currentLeg);
    }
    Schedule nextMonth = getSchedule(service, yearMonth.plusMonths(1));
    Leg nextLeg = selector.getNextLeg(thisMonth, nextMonth);
    if (nextLeg != null) {
      System.out.println("Next leg: " + nextLeg);
    }
  }

  private static Schedule getSchedule(FlicaService service, YearMonth yearMonth)
      throws Exception {
    String rawSchedule = service.getSchedule(yearMonth);
    ScheduleParser scheduleParser = new ScheduleParser(rawSchedule);
    Proto.Schedule protoSchedule = scheduleParser.parse();
    ScheduleAdapter scheduleAdapter = new ScheduleAdapter();
    return scheduleAdapter.adapt(protoSchedule);
  }

  public ScheduleSelector(Clock clock) {
    this.clock = clock;
  }

  public Leg getCurrentLeg(Schedule schedule) {
    Trip currentTrip = getCurrentTrip(schedule);
    if (currentTrip != null) {
      Section currentSection = getCurrentSection(currentTrip);
      if (currentSection != null) {
        Leg currentLeg = getCurrentLeg(currentSection);
        if (currentLeg != null) {
          return currentLeg;
        }
      }
    }
    return null;
  }

  private boolean isNowWithin(ReadableInstant start, ReadableInstant end) {
    DateTime now = clock.now();
    return !start.isAfter(now) && !end.isBefore(now);
  }

  public Trip getCurrentTrip(Schedule schedule) {
    Map<PairingKey, Trip> trips = schedule.getTrips();
    for (Trip trip : trips.values()) {
      if (isNowWithin(trip.getDutyStart(), trip.getDutyEnd())) {
        return trip;
      }
    }
    return null;
  }

  public Section getCurrentSection(Trip trip) {
    for (Section section : trip.getSections()) {
      if (isNowWithin(section.getStart(), section.getEnd())) {
        return section;
      }
    }
    return null;
  }

  public Leg getCurrentLeg(Section section) {
    for (Leg leg : section.getLegs()) {
      if (isNowWithin(leg.getDepartureTime(), leg.getArrivalTime())) {
        return leg;
      }
    }
    return null;
  }

  public Leg getNextLeg(Schedule schedule, Schedule nextMonthSchedule) {
    DateTime now = clock.now();
    for (Leg leg : schedule) {
      if (!now.isAfter(leg.getArrivalTime())) {
        return leg;
      }
    }
    if (nextMonthSchedule != null) {
      for (Leg leg : nextMonthSchedule) {
        if (!now.isAfter(leg.getArrivalTime())) {
          return leg;
        }
      }
    }
    return null;
  }
}
