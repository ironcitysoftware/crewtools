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

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.http.client.ClientProtocolException;
import org.joda.time.DateTime;
import org.joda.time.ReadableInstant;

import crewtools.dashboard.ScheduleProvider;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.Leg;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.Trip;
import crewtools.util.Clock;

public class LegSelector {
  private static final Logger logger = Logger.getLogger(LegSelector.class.getName());

  private Clock clock;

  public LegSelector(Clock clock) {
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

  public Leg getNextLeg(ScheduleProvider scheduleProvider)
      throws ClientProtocolException, IOException, ParseException {
    DateTime now = clock.now();
    Schedule schedule = scheduleProvider.getCurrentMonthSchedule();
    for (Leg leg : schedule) {
      if (!now.isAfter(leg.getArrivalTime())) {
        return leg;
      }
    }
    schedule = scheduleProvider.getNextMonthSchedule();
    if (schedule != null) {
      for (Leg leg : schedule) {
        if (!now.isAfter(leg.getArrivalTime())) {
          return leg;
        }
      }
    }
    return null;
  }

  public Leg getPreviousLeg(ScheduleProvider scheduleProvider)
      throws ClientProtocolException, IOException, ParseException {
    DateTime now = clock.now();
    Schedule schedule = scheduleProvider.getCurrentMonthSchedule();
    Iterator<Leg> iterator = schedule.reverseIterator();
    while (iterator.hasNext()) {
      Leg leg = iterator.next();
      if (!now.isBefore(leg.getDepartureTime())) {
        return leg;
      }
    }
    schedule = scheduleProvider.getPreviousMonthSchedule();
    if (schedule != null) {
      iterator = schedule.reverseIterator();
      while (iterator.hasNext()) {
        Leg leg = iterator.next();
        if (!now.isBefore(leg.getDepartureTime())) {
          return leg;
        }
      }
    }
    return null;
  }
}
