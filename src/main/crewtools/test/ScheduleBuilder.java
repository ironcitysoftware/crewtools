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
import java.util.List;

import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableList;

import crewtools.flica.Proto;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.util.Period;

public class ScheduleBuilder {
  public Schedule build(Trip... trips) {
    return build("created by ScheduleBuilder", trips);
  }
  
  public Schedule build(String bidName, Trip... trips) {
    YearMonth yearMonth;
    if (trips.length > 0) {
      yearMonth = new YearMonth(trips[0].getFirstSection().startDuty);
    } else {
      yearMonth = TripBuilder.DEFAULT_YEAR_MONTH;
    }
    Proto.Schedule.Builder scheduleBuilder = Proto.Schedule.newBuilder();
    scheduleBuilder.setBidName(bidName);
    scheduleBuilder.setYearMonth(yearMonth.toString());
    scheduleBuilder.setRetrievedUtcMs(0);
    List<Trip> tripList = new ArrayList<>();
    Period block = Period.ZERO;
    Period credit = Period.ZERO;
    for (Trip trip : trips) {
      tripList.add(trip);
      scheduleBuilder.addTrip(trip.proto);
      block = block.plus(trip.block);
      credit = credit.plus(trip.credit);
    }
    return new Schedule(
        tripList,
        block,
        credit,
        yearMonth,
        scheduleBuilder.build());
  }
}
