/**
 * Copyright 2019 Iron City Software LLC
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.joda.time.Duration;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import crewtools.flica.Proto;
import crewtools.util.Calendar;
import crewtools.util.Period;

// Represents a month and blend.
public class Schedule {
  private final Logger logger = Logger.getLogger(Schedule.class.getName());

  public final List<Trip> trips;
  public final Period blockTime;
  public final Period creditTime;
  public final YearMonth yearMonth;
  public final Proto.Schedule proto;
  private Set<Interval> nonTripIntervals;

  public final Map<PairingKey, Period> tripCreditsInMonth;
  public final Period creditInMonth;
  public final Period nonTripCreditInMonth;
  private final Map<PairingKey, Trip> tripsByKey;
  private final Map<PairingKey, Integer> numWorkDays;
  private final int totalNumWorkDays;

  public Schedule(List<Trip> trips, Period block, Period credit, YearMonth yearMonth, Proto.Schedule proto) {
    this.trips = trips;
    this.blockTime = block;
    this.creditTime = credit;
    this.yearMonth = yearMonth;
    this.proto = proto;
    this.nonTripIntervals = new HashSet<>();

    Calendar calendar = new Calendar(yearMonth);
    Period totalCreditInMonth = Period.ZERO;
    Period nonTripCreditInMonth = Period.ZERO;
    int totalNumWorkDays = 0;
    ImmutableMap.Builder<PairingKey, Period> tripCreditsInMonth = ImmutableMap.builder();
    ImmutableMap.Builder<PairingKey, Integer> numWorkDays = ImmutableMap.builder();
    for (Trip trip : trips) {
      if (calendar.isWithinPeriod(trip.getEarliestDepartureDate())) {
        Period creditInMonth = trip.getCredit();
        totalCreditInMonth = totalCreditInMonth.plus(creditInMonth);
        if (trip.hasScheduleType()) {
          // vacation, training...
          mergeNonTripInterval(trip.getInterval());
          nonTripCreditInMonth = nonTripCreditInMonth.plus(creditInMonth);
        } else {
          tripCreditsInMonth.put(trip.getPairingKey(), creditInMonth);
          int numDays = 0;
          for (LocalDate date : trip.getDepartureDates()) {
            if (calendar.isWithinPeriod(date)) {
              numDays++;
            }
          }
          totalNumWorkDays += numDays;
          numWorkDays.put(trip.getPairingKey(), numDays);
        }
      }
    }

    this.totalNumWorkDays = totalNumWorkDays;
    this.numWorkDays = numWorkDays.build();
    this.creditInMonth = totalCreditInMonth;
    this.nonTripCreditInMonth = nonTripCreditInMonth;
    this.tripCreditsInMonth = tripCreditsInMonth.build();

    ImmutableMap.Builder<PairingKey, Trip> result = ImmutableMap.builder();
    Ordering.natural().sortedCopy(trips).forEach(trip -> {
      if (!trip.hasScheduleType()) {
        result.put(trip.getPairingKey(), trip);
      }
    });
    this.tripsByKey = result.build();

  }

  /** Returns days in a given month for all trips */
  public Set<LocalDate> getTripDaysInMonth(YearMonth yearMonth) {
    Set<LocalDate> days = new HashSet<>();
    Calendar calendar = new Calendar(yearMonth);
    for (Trip trip : trips) {
      if (calendar.isWithinPeriod(trip.getEarliestDepartureDate())) {
        for (LocalDate date : trip.getDepartureDates()) {
          days.add(date);
        }
      }
    }
    return days;
  }

  public Map<LocalDate, Period> getTripCreditInMonth(YearMonth yearMonth) {
    Map<LocalDate, Period> result = new HashMap<>();
    Calendar calendar = new Calendar(yearMonth);
    for (Trip trip : trips) {
      for (Section section : trip.getSections()) {
        if (calendar.isWithinPeriod(section.date)) {
          result.put(section.date, section.credit);
        }
      }
    }
    return result;
  }

  /**
   * Returns the credit in the month for all trips (but not training, vacation,
   * etc)
   */
  public Map<PairingKey, Period> getTripCreditInMonth() {
    return tripCreditsInMonth;
  }

  /**
   * Returns the credit in the calendar month for all events.
   */
  public Period getCreditInMonth() {
    return creditInMonth;
  }

  public Period getNonTripCreditInMonth() {
    return nonTripCreditInMonth;
  }

  public Set<Interval> getNonTripIntervals() {
    return nonTripIntervals;
  }

  public Map<PairingKey, Integer> getNumWorkDays() {
    return numWorkDays;
  }

  public int getTotalNumWorkDays() {
    return totalNumWorkDays;
  }

  /**
   * Returns trips with PairingKeys, that is, actual line flying,
   * in the natural ordering of Trip (which is first by date).
   */
  public Map<PairingKey, Trip> getTrips() {
    return tripsByKey;
  }

  /**
   * Vacation shows up as abutting trips (therefore abutting intervals). Combine
   * these as they are added into the set of nontrip intervals.
   */
  private void mergeNonTripInterval(Interval interval) {
    Interval abut = null;
    while (true) {
      abut = findAbuttingNonTripIntervalOrNull(interval);
      if (abut == null) {
        break;
      }
      nonTripIntervals.remove(abut);
      if (interval.getStart().minusMinutes(1).equals(abut.getEnd())) {
        interval = new Interval(abut.getStart(), interval.getEnd());
      } else {
        Preconditions
            .checkState(interval.getEnd().plusMinutes(1).equals(abut.getStart()));
        interval = new Interval(interval.getStart(), abut.getEnd());
      }
    }
    nonTripIntervals.add(interval);
  }

  private static final long ONE_MINUTE = Duration.standardMinutes(1).getMillis();

  private Interval findAbuttingNonTripIntervalOrNull(Interval interval) {
    for (Interval existingInterval : nonTripIntervals) {
      if (isAdjacent(interval, existingInterval)) {
        return existingInterval;
      }
    }
    return null;
  }

  private boolean isAdjacent(Interval left, Interval right) {
    // RIGHT.LEFT
    long delta = left.getStartMillis() - right.getEndMillis();
    if (delta >= 0) {
      return delta <= ONE_MINUTE;
    }
    // LEFT.RIGHT
    delta = right.getStartMillis() - left.getEndMillis();
    if (delta >= 0) {
      return delta <= ONE_MINUTE;
    }
    return false;
  }

  public Schedule copyAndModify(List<Trip> adds, List<PairingKey> drops) {
    List<Trip> newTrips = new ArrayList<>(trips);
    Period newBlockTime = new Period(blockTime);
    Period newCreditTime = new Period(creditTime);
    Proto.Schedule.Builder newProto = Proto.Schedule.newBuilder(proto);

    // process drops
    int numDropped = 0;
    for (Trip trip : trips) {
      if (!trip.hasScheduleType() && drops.contains(trip.getPairingKey())) {
        numDropped++;
        newBlockTime = newBlockTime.minus(trip.block);
        newCreditTime = newCreditTime.minus(trip.credit);
        int found = -1;
        for (int i = 0; i < newProto.getTripCount(); i++) {
          Proto.Trip searchTrip = newProto.getTrip(i);
          if (searchTrip.equals(trip.proto)) {
            found = i;
            break;
          }
        }
        Preconditions.checkState(found != -1,
            "Unable to find " + trip.proto + " in " + newProto);
        newProto.removeTrip(found);
        newTrips.remove(trip);
      }
    }
    Preconditions.checkState(numDropped == drops.size(), "Drops: " + drops + ", trips: " + trips);

    // process adds
    for (Trip addedTrip : adds) {
      newTrips.add(addedTrip);
      newBlockTime = newBlockTime.plus(addedTrip.block);
      newCreditTime = newCreditTime.plus(addedTrip.block);
      newProto.addTrip(addedTrip.proto);
    }
    return new Schedule(newTrips, newBlockTime, newCreditTime, yearMonth, newProto.build());
  }

  @Override
  public int hashCode() {
    return proto.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof Schedule)) {
      return false;
    }
    Schedule that = (Schedule) o;
    return proto.equals(that.proto);
  }

  @Override
  public String toString() {
    return proto.getTripList()
        .stream()
        .map(trip -> trip.getPairingName())
        .collect(Collectors.joining(":"));
  }

  public List<Leg> getLegs() {
    List<Leg> legs = new ArrayList<>();
    for (Trip trip : trips) {
      legs.addAll(trip.getLegs());
    }
    return legs;
  }
}
