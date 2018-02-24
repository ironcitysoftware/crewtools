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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;

import crewtools.flica.Proto;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.util.Period;

// converts a proto schedule to a pojo schedule
public class ScheduleAdapter extends PairingAdapter {
  private final Logger logger = Logger.getLogger(ScheduleAdapter.class.getName());

  public Schedule adapt(Proto.Schedule protoSchedule) {
    Preconditions.checkState(protoSchedule.hasYearMonth(),
        "Sorry, there needs to be a year_month field on schedule.");
    List<Trip> trips = new ArrayList<>();
    Period tripBlock = Period.ZERO;
    Period tripCredit = Period.ZERO;
    
    List<Proto.Trip> newTrips = new ArrayList<>();; 
    for (Proto.Trip protoTrip : protoSchedule.getTripList()) {
      // KRW TODO.  Schedule trips should not have multiple pairing keys.
      protoTrip = Proto.Trip.newBuilder(protoTrip).clearOperates().build();
      newTrips.add(protoTrip);
      
      Trip trip = adaptTrip(protoTrip);
      Preconditions.checkState(trip.getPairingKey() != null, "unexpected multiple pairing keys");
      trips.add(trip);
      tripBlock = tripBlock.plus(trip.block);
      tripCredit = tripCredit.plus(trip.credit);
    }
    // KRW
    protoSchedule = Proto.Schedule.newBuilder(protoSchedule).clearTrip().addAllTrip(newTrips).build();
    
    return new Schedule(
        trips,
        tripBlock,
        tripCredit,
        YearMonth.parse(protoSchedule.getYearMonth()),
        protoSchedule);
  }
}
