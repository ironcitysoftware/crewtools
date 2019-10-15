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

package crewtools.flica.bid;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

import crewtools.flica.bid.OverlapEvaluator.OverlapEvaluation;
import crewtools.flica.bid.OverlapEvaluator.OverlapEvaluation.Overlap;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;

public class OverlapEvaluatorTest {
  @Test
  public void testRequiredDaysOff() throws Exception {
    Trip proposedTrip = mock(Trip.class);
    when(proposedTrip.getInterval()).thenReturn(new Interval(0, 1));
    when(proposedTrip.getDepartureDates()).thenReturn(ImmutableSet.of(
        LocalDate.parse("2019-11-01")));

    ReducedSchedule reducedSchedule = mock(ReducedSchedule.class);
    when(reducedSchedule.getNonTripIntervals()).thenReturn(ImmutableSet.of());

    OverlapEvaluator evaluator = new OverlapEvaluator(
        reducedSchedule,
        YearMonth.parse("2019-11"),
        BidConfig.newBuilder().addRequiredDayOff(1).build());
    assertEquals(Overlap.DAY_OFF, evaluator.evaluate(proposedTrip).overlap);
  }

  @Test
  public void testAbutsRetainedTrip() throws Exception {
    Trip retainedTrip = mock(Trip.class);
    when(retainedTrip.getDepartureDates()).thenReturn(ImmutableSet.of(
        LocalDate.parse("2019-11-03"),
        LocalDate.parse("2019-11-04")));

    Trip proposedTrip = mock(Trip.class);
    when(proposedTrip.getInterval()).thenReturn(new Interval(0, 1));
    when(proposedTrip.getDepartureDates()).thenReturn(ImmutableSet.of(
        LocalDate.parse("2019-11-01")));

    ReducedSchedule reducedSchedule = mock(ReducedSchedule.class);
    when(reducedSchedule.getNonTripIntervals()).thenReturn(ImmutableSet.of());
    when(reducedSchedule.getRetainedTrips()).thenReturn(ImmutableSet.of(retainedTrip));

    OverlapEvaluator evaluator = new OverlapEvaluator(
        reducedSchedule,
        YearMonth.parse("2019-11"),
        BidConfig.newBuilder().setMinimumNumberOfDaysBetweenTrips(2).build());
    OverlapEvaluation result = evaluator.evaluate(proposedTrip);
    assertEquals(Overlap.RETAINED_TRIP, result.overlap);
    assertEquals(ImmutableSet.of(retainedTrip), result.overlappedTrips);
  }

  @Test
  public void testMaxDaysInARow() throws Exception {
    Trip retainedTrip = mock(Trip.class);
    when(retainedTrip.getDepartureDates()).thenReturn(ImmutableSet.of(
        LocalDate.parse("2019-11-03"),
        LocalDate.parse("2019-11-04"),
        LocalDate.parse("2019-11-05"),
        LocalDate.parse("2019-11-06")));

    Trip proposedTrip = mock(Trip.class);
    when(proposedTrip.getInterval()).thenReturn(new Interval(0, 1));
    when(proposedTrip.getDepartureDates()).thenReturn(ImmutableSet.of(
        LocalDate.parse("2019-11-01"),
        LocalDate.parse("2019-11-02")));

    ReducedSchedule reducedSchedule = mock(ReducedSchedule.class);
    when(reducedSchedule.getNonTripIntervals()).thenReturn(ImmutableSet.of());
    when(reducedSchedule.getRetainedTrips()).thenReturn(ImmutableSet.of(retainedTrip));

    OverlapEvaluator evaluator = new OverlapEvaluator(
        reducedSchedule,
        YearMonth.parse("2019-11"),
        BidConfig.newBuilder().build());
    OverlapEvaluation result = evaluator.evaluate(proposedTrip);
    assertEquals(Overlap.RETAINED_TRIP, result.overlap);
    assertEquals(ImmutableSet.of(retainedTrip), result.overlappedTrips);
  }
}
