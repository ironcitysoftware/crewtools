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

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto;
import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.util.Period;

public class ScheduleBuilder {
  private List<Trip> trips = new ArrayList<>();

  public ScheduleBuilder withTrips(Trip... trips) {
    for (Trip trip : trips) {
      this.trips.add(trip);
    }
    return this;
  }

  // @formatter:off
  public ScheduleBuilder withVacation(int startDay, int endDay) {
    LocalDate startDate = TripBuilder.DEFAULT_YEAR_MONTH.toLocalDate(startDay);
    LocalDate endDate = TripBuilder.DEFAULT_YEAR_MONTH.toLocalDate(endDay);

    Preconditions.checkState(endDate.isAfter(startDate));

    addVacationTrip(
        startDate,
        startDate,
        Period.ZERO,
        Proto.Trip.newBuilder()
            .setPairingName("VAX")
            .setStartDate(startDate.toString())
            .setEndDate(startDate.plusDays(1).toString())
            .setCreditDuration("0000")
            .setScheduleType(ScheduleType.VACATION_START)
            .setStartTime("17:01")
            .setEndTime("00:00").build());

    LocalDate date = startDate.plusDays(1);
    if (date.isBefore(endDate)) {
      addVacationTrip(
          date,
          endDate,
          Period.hours(21),
          Proto.Trip.newBuilder()
              .setPairingName("VAC")
              .setStartDate(date.toString())
              .setEndDate(endDate.toString())
              .setCreditDuration("2100")
              .setScheduleType(ScheduleType.VACATION)
              .setStartTime("00:01")
              .setEndTime("00:00").build());
    }

    addVacationTrip(
        endDate,
        endDate,
        Period.ZERO,
        Proto.Trip.newBuilder()
            .setPairingName("VAS")
            .setStartDate(endDate.toString())
            .setEndDate(endDate.toString())
            .setCreditDuration("0000")
            .setScheduleType(ScheduleType.VACATION_END)
            .setStartTime("00:01")
            .setEndTime("09:59").build());
    return this;
  }

  private void addVacationTrip(LocalDate date, LocalDate end,
      Period credit, Proto.Trip tripProto) {
    Set<LocalDate> dates = new HashSet<>();
    dates.add(date);
    if (!date.equals(end)) {
      date = date.plusDays(1);
      while (date.isBefore(end)) {
        date = date.plusDays(1);
        dates.add(date);
      }
    }
    trips.add(new Trip(ImmutableList.of() /* sections */,
        credit,
        credit,
        Period.ZERO,
        Period.ZERO,
        ImmutableSet.of(date) /* days */,
        tripProto));
  }

  // @formatter:on
  public Schedule build() {
    return build("created by ScheduleBuilder");
  }
  
  public Schedule build(String bidName) {
    YearMonth yearMonth;
    if (!trips.isEmpty() && trips.get(0).isDroppable()) {
      yearMonth = new YearMonth(trips.get(0).getDutyStart());
    } else {
      yearMonth = TripBuilder.DEFAULT_YEAR_MONTH;
    }
    Proto.Schedule.Builder scheduleBuilder = Proto.Schedule.newBuilder();
    scheduleBuilder.setBidName(bidName);
    scheduleBuilder.setYearMonth(yearMonth.toString());
    scheduleBuilder.setRetrievedUtcMs(0);
    Period block = Period.ZERO;
    Period credit = Period.ZERO;
    for (Trip trip : trips) {
      scheduleBuilder.addTrip(trip.proto);
      block = block.plus(trip.block);
      credit = credit.plus(trip.credit);
    }
    return new Schedule(
        trips,
        block,
        credit,
        yearMonth,
        scheduleBuilder.build());
  }
}
