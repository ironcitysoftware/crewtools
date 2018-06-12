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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

import crewtools.flica.Proto;
import crewtools.util.NestedIterator;
import crewtools.util.Period;
import crewtools.util.ReverseIterator;

// Represents a month and blend.
// TODO combine Schedule and ScheduleWrapper
public class Schedule implements Iterable<Leg> {
  private final Logger logger = Logger.getLogger(Schedule.class.getName());

  public final List<Trip> trips;
  public final Period blockTime;
  public final Period creditTime;
  public final YearMonth yearMonth;
  public final Proto.Schedule proto;

  public final Map<PairingKey, Period> tripCreditsInMonth;
  public final Period creditInMonth;
  public final Period nonTripCreditInMonth;

  public Schedule(List<Trip> trips, Period block, Period credit, YearMonth yearMonth, Proto.Schedule proto) {
    this.trips = trips;
    this.blockTime = block;
    this.creditTime = credit;
    this.yearMonth = yearMonth;
    this.proto = proto;

    Period totalCreditInMonth = Period.ZERO;
    Period nonTripCreditInMonth = Period.ZERO;
    ImmutableMap.Builder<PairingKey, Period> tripCreditsInMonth = ImmutableMap.builder();
    for (Trip trip : trips) {
      Period creditInMonth = trip.getCreditInMonth(yearMonth);
      totalCreditInMonth = totalCreditInMonth.plus(creditInMonth);
      if (trip.hasScheduleType()) {
        // vacation, training...
        nonTripCreditInMonth = nonTripCreditInMonth.plus(creditInMonth);
      } else {
        tripCreditsInMonth.put(trip.getPairingKey(), creditInMonth);
      }
    }

    this.creditInMonth = totalCreditInMonth;
    this.nonTripCreditInMonth = nonTripCreditInMonth;
    this.tripCreditsInMonth = tripCreditsInMonth.build();
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

  /**
   * Returns trips with PairingKeys, that is, actual line flying,
   * in the natural ordering of Trip (which is first by date).
   */
  public Map<PairingKey, Trip> getTrips() {
    ImmutableMap.Builder<PairingKey, Trip> result = ImmutableMap.builder();
    Ordering.natural().sortedCopy(trips).forEach(trip -> {
      if (!trip.hasScheduleType()) {
        result.put(trip.getPairingKey(), trip);  
      }
    });
    return result.build();
  }

  public Schedule copyAndModify(List<Trip> adds, List<PairingKey> drops) {
    List<Trip> newTrips = new ArrayList<>(trips);
    Period newBlockTime = new Period(blockTime);
    Period newCreditTime = new Period(creditTime);
    Proto.Schedule.Builder newProto = Proto.Schedule.newBuilder(proto);

    // process drops
    int numDropped = 0;
    for (Trip trip : trips) {
      if (trip.getNumSections() == 0) {
        logger.info("copyAndModify a trip with no sections? " + trip.proto);
        continue;
      }
      if (drops.contains(trip.getPairingKey())) {
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

  @Override
  public Iterator<Leg> iterator() {
    return new NestedIterator<Leg, Trip>(trips.iterator());
  }

  public Iterator<Leg> reverseIterator() {
    return new NestedIterator<Leg, Trip>(new ReverseIterator<Trip>(trips)) {
      @Override public Iterator<Leg> getIterator(Trip trip) {
        return trip.reverseIterator();
      }
    };
  }
}
