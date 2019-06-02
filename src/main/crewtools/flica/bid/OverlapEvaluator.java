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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import crewtools.flica.bid.OverlapEvaluator.OverlapEvaluation.Overlap;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;

public class OverlapEvaluator {
  public static class OverlapEvaluation {
    public enum Overlap {
      UNDROPPABLE,
      DAY_OFF,
      RETAINED_TRIP,
      NO_OVERLAP
    }
    public OverlapEvaluation(Overlap overlap) {
      this.overlap = overlap;
    }

    public final Overlap overlap;
    //public final boolean noOverlap;
    //public Collection<Trip> droppable;
  }

  private final Set<LocalDate> requiredDaysOff;
  private final Set<Interval> nonTripIntervals;
  private final ReducedSchedule alteredSchedule;

  private static final LocalTime LOCALTIME_END_OF_DAY = LocalTime.parse("23:59");

  public OverlapEvaluator(ReducedSchedule alteredSchedule,
      YearMonth yearMonth, BidConfig bidConfig) {
    ImmutableSet.Builder<LocalDate> builder = ImmutableSet.builder();
    for (int dayOfMonth : bidConfig.getRequiredDayOffList()) {
      builder.add(yearMonth.toLocalDate(dayOfMonth));
    }
    this.requiredDaysOff = builder.build();
    this.alteredSchedule = alteredSchedule;
    this.nonTripIntervals = alteredSchedule.getNonTripIntervals();
  }

  public OverlapEvaluation evaluate(Set<LocalDate> dates) {
    LocalDate first = Ordering.natural().min(dates);
    LocalDate last = Ordering.natural().max(dates);
    // Company vacation or training.
    Interval taskInterval = new Interval(first.toDateTimeAtStartOfDay(),
        last.toDateTime(LOCALTIME_END_OF_DAY));
    if (overlapsNonTrip(taskInterval)) {
      return new OverlapEvaluation(Overlap.UNDROPPABLE);
    }

    // Hard-and-fast days off.
    if (overlaps(dates, requiredDaysOff)) {
      return new OverlapEvaluation(Overlap.DAY_OFF);
    }

    for (Trip trip : alteredSchedule.getRetainedTrips()) {
      if (overlapsDates(trip, dates)) {
        return new OverlapEvaluation(Overlap.RETAINED_TRIP);
      }
    }

    return new OverlapEvaluation(Overlap.NO_OVERLAP);
  }

  public OverlapEvaluation evaluate(Trip proposedTrip) {
    // Company vacation or training.
    if (overlapsNonTrip(proposedTrip.getInterval())) {
      return new OverlapEvaluation(Overlap.UNDROPPABLE);
    }

    // Hard-and-fast days off.
    if (overlaps(proposedTrip.getDepartureDates(), requiredDaysOff)) {
      return new OverlapEvaluation(Overlap.DAY_OFF);
    }

    for (Trip trip : alteredSchedule.getRetainedTrips()) {
      if (overlapsDates(trip, proposedTrip.getDepartureDates())) {
        return new OverlapEvaluation(Overlap.RETAINED_TRIP);
      }
    }

    return new OverlapEvaluation(Overlap.NO_OVERLAP);
  }

  private boolean overlapsNonTrip(Interval interval) {
    for (Interval existingInterval : nonTripIntervals) {
      if (existingInterval.overlaps(interval)) {
        return true;
      }
    }
    return false;
  }

  private static final int MAX_DAYS_IN_A_ROW = 5;

  // TODO use Interval instead.
  private boolean overlapsDates(Trip scheduledTrip, Set<LocalDate> potentialTripDates) {
    // Add the day before and after a scheduled trip.
    // We don't want to end up with adjacent trips.
    Set<LocalDate> scheduledDates = new HashSet<>(scheduledTrip.getDepartureDates());
    Preconditions.checkState(!scheduledDates.isEmpty());
    if (overlaps(scheduledDates, potentialTripDates)) {
      return true;
    }
    // Check for abutment.
    LocalDate priorDate = Ordering.natural().min(scheduledDates).minusDays(1);
    LocalDate nextDate = Ordering.natural().max(scheduledDates).plusDays(1);
    if (potentialTripDates.contains(priorDate)
        || potentialTripDates.contains(nextDate)) {
      return scheduledDates.size() + potentialTripDates.size() > MAX_DAYS_IN_A_ROW;
    } else {
      return false;
    }
  }

  private boolean overlaps(Collection<LocalDate> a, Collection<LocalDate> b) {
    return !Collections.disjoint(a, b);
  }
}
