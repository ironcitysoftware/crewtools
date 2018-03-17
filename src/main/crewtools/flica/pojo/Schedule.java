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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;

import crewtools.flica.Proto;
import crewtools.util.Period;

// Represents a month and blend.
// TODO combine Schedule and ScheduleWrapper
public class Schedule {
  private final Logger logger = Logger.getLogger(Schedule.class.getName());

  public final List<Trip> trips;
  public final Period blockTime;
  public final Period creditTime;
  public final YearMonth yearMonth;
  public final Proto.Schedule proto;

  public Schedule(List<Trip> trips, Period block, Period credit, YearMonth yearMonth, Proto.Schedule proto) {
    this.trips = trips;
    this.blockTime = block;
    this.creditTime = credit;
    this.yearMonth = yearMonth;
    this.proto = proto;
  }

  /** Returns trips with PairingKeys, that is, actual line flying. */
  public Map<PairingKey, Trip> getTrips() {
    Map<PairingKey, Trip> result = new HashMap<>();
    trips.forEach(trip -> {
      if (!trip.hasScheduleType()) {
        result.put(trip.getPairingKey(), trip);  
      }
    });
    return result;
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
  
  // One cannot swap a trip less than 48 hours in advance.
  private static final Days SWAP_PRE_PERIOD = Days.TWO;

  public List<Trip> getFutureTrips(LocalDate today) {
    List<Trip> result = new ArrayList<>();
    for (Trip trip : trips) {
      if (trip.getFirstSection().getDepartureDate().isAfter(today.plus(SWAP_PRE_PERIOD))) {
        result.add(trip);
      }
    }
    return result;
  }

  public List<Trip> getPastTrips(LocalDate today) {
    List<Trip> result = new ArrayList<>();
    for (Trip trip : trips) {
      if (!trip.getFirstSection().getDepartureDate().isAfter(today.plus(SWAP_PRE_PERIOD))) {
        result.add(trip);
      }
    }
    return result;
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
}
