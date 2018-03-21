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

package crewtools.flica.bid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.bid.ScheduleWrapper.OverlapEvaluation;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.test.FakeClock;
import crewtools.test.ScheduleBuilder;
import crewtools.test.TripBuilder;
import crewtools.util.Clock;
import crewtools.util.Period;

public class ScheduleWrapperTest {
  private static final Clock FAKE_CLOCK = new FakeClock(TripBuilder.DEFAULT_DAY);

  // @formatter:off
  @Test
  public void testTripOverlapsBothVacationAndAnotherTrip() {
    Trip trip = new TripBuilder()
        .withDayOfMonth(9)
        .withLeg("CLT", "GSP", Period.hours(12))
        .withLayover("GSP", Period.hours(12))
        .withLeg("GSP", "CLT", Period.hours(10))
        .build();

    Schedule schedule = new ScheduleBuilder()
        .withVacation(1, 7)
        .withTrips(trip)
        .build();

    ScheduleWrapper wrapper = new ScheduleWrapper(
        schedule, TripBuilder.DEFAULT_YEAR_MONTH, FAKE_CLOCK);

    Trip betterTrip = new TripBuilder()
        .withDayOfMonth(7)
        // Test vacation is hard-coded to end at 10am. (so sue me).
        .withHourOfDay(10)
        .withLeg("CLT", "GSP", Period.hours(12))
        .withLayover("GSP", Period.hours(12))
        .withLeg("GSP", "CLT", Period.hours(10))
        .withLayover("CLT", Period.hours(12))
        .withLeg("CLT", "GSP", Period.hours(12))
        .build();

    OverlapEvaluation overlap = wrapper.evaluateOverlap(betterTrip);
    assertFalse(overlap.overlapsUndroppable);
    assertFalse(overlap.noOverlap);
    assertEquals(ImmutableSet.of(trip), overlap.droppable);
  }

  @Test
  public void testTripOverlapsOnlyVacation() {
    Trip trip = new TripBuilder()
        .withDayOfMonth(9)
        .withLeg("CLT", "GSP", Period.hours(12))
        .withLayover("GSP", Period.hours(12))
        .withLeg("GSP", "CLT", Period.hours(10))
        .build();

    Schedule schedule = new ScheduleBuilder()
        .withVacation(1, 7)
        .withTrips(trip)
        .build();

    ScheduleWrapper wrapper = new ScheduleWrapper(
        schedule, TripBuilder.DEFAULT_YEAR_MONTH, FAKE_CLOCK);

    Trip betterTrip = new TripBuilder()
        .withDayOfMonth(2)
        // Test vacation is hard-coded to end at 10am. (so sue me).
        .withHourOfDay(10)
        .withLeg("CLT", "GSP", Period.hours(12))
        .withLayover("GSP", Period.hours(12))
        .withLeg("GSP", "CLT", Period.hours(10))
        .withLayover("CLT", Period.hours(12))
        .withLeg("CLT", "GSP", Period.hours(12))
        .build();

    OverlapEvaluation overlap = wrapper.evaluateOverlap(betterTrip);
    assertTrue(overlap.overlapsUndroppable);
    assertFalse(overlap.noOverlap);
    assertTrue(overlap.droppable.isEmpty());
  }

  @Test
  public void testTripOverlapsAnotherTrip() {
    Trip trip = new TripBuilder()
        .withDayOfMonth(9)
        .withLeg("CLT", "GSP", Period.hours(12))
        .withLayover("GSP", Period.hours(12))
        .withLeg("GSP", "CLT", Period.hours(10))
        .build();

    Schedule schedule = new ScheduleBuilder()
        .withVacation(1, 7)
        .withTrips(trip)
        .build();

    ScheduleWrapper wrapper = new ScheduleWrapper(
        schedule, TripBuilder.DEFAULT_YEAR_MONTH, FAKE_CLOCK);

    Trip betterTrip = new TripBuilder()
        .withDayOfMonth(9)
        .withLeg("CLT", "GSP", Period.hours(12))
        .withLayover("GSP", Period.hours(12))
        .withLeg("GSP", "CLT", Period.hours(10))
        .withLayover("CLT", Period.hours(12))
        .withLeg("CLT", "GSP", Period.hours(12))
        .build();

    OverlapEvaluation overlap = wrapper.evaluateOverlap(betterTrip);
    assertFalse(overlap.overlapsUndroppable);
    assertFalse(overlap.noOverlap);
    assertEquals(ImmutableSet.of(trip), overlap.droppable);
  }

  @Test
  public void testIdentifyBaggageTrips() {
    Trip trip1 = new TripBuilder()
        .withName("baggage1")
        .withDayOfMonth(1)
        .withLeg("CLT", "GSP", Period.hours(1))
        .build();

    Trip trip2 = new TripBuilder()
        .withName("keeper1")
        .withDayOfMonth(9)
        .withLeg("CLT", "GSP", Period.hours(11))
        .withLayover("GSP", Period.hours(12))
        .withLeg("GSP", "CLT", Period.hours(11))
        .withLayover("CLT", Period.hours(12))
        .withLeg("CLT", "GSP", Period.hours(10))
        .build();

    Trip trip3 = new TripBuilder()
        .withName("keeper2")
        .withDayOfMonth(14)
        .withLeg("CLT", "GSP", Period.hours(6))
        .withLayover("GSP", Period.hours(12))
        .withLeg("GSP", "CLT", Period.hours(6))
        .build();

    Trip trip4 = new TripBuilder()
        .withName("baggage2")
        .withDayOfMonth(19)
        .withLeg("CLT", "GSP", Period.hours(2))
        .build();
    
    Schedule schedule = new ScheduleBuilder()
        .withTrips(trip1, trip2, trip3, trip4)
        .withVacation(25, 27)
        .build();

    ScheduleWrapper wrapper = new ScheduleWrapper(
        schedule, TripBuilder.DEFAULT_YEAR_MONTH, FAKE_CLOCK);

    assertEquals(ImmutableList.of(trip4.getPairingKey(), trip1.getPairingKey()),
        wrapper.identifyBaggageTrips(schedule));
  }
  // @formatter:on
}
