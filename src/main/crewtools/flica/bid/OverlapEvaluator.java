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
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import crewtools.flica.bid.OverlapEvaluator.OverlapEvaluation.Overlap;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;

public class OverlapEvaluator {
  private final Logger logger = Logger.getLogger(OverlapEvaluator.class.getName());

  public static class OverlapEvaluation {
    public enum Overlap {
      UNDROPPABLE,
      DAY_OFF,
      RETAINED_TRIP,
      NO_OVERLAP
    }
    public OverlapEvaluation(Overlap overlap) {
      this.overlap = overlap;
      this.overlappedTrips = ImmutableSet.of();
    }

    public OverlapEvaluation(Overlap overlap, Set<Trip> overlappedTrips) {
      this.overlap = overlap;
      this.overlappedTrips = overlappedTrips;
    }

    public final Overlap overlap;
    public final Set<Trip> overlappedTrips;

    @Override
    public boolean equals(Object o) {
      if (o == null || !(o instanceof OverlapEvaluation)) {
        return false;
      }
      OverlapEvaluation that = (OverlapEvaluation) o;
      return this.overlap == that.overlap
          && this.overlappedTrips.equals(that.overlappedTrips);
    }

    @Override
    public int hashCode() {
      return Objects.hash(overlap, overlappedTrips);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("overlap", overlap)
          .add("overlappedTrips", overlappedTrips)
          .toString();
    }
  }

  private final Set<LocalDate> requiredDaysOff;
  private final Set<Interval> nonTripIntervals;
  private final ReducedSchedule alteredSchedule;
  private final BidConfig bidConfig;

  private static final LocalTime LOCALTIME_END_OF_DAY = LocalTime.parse("23:59");

  public OverlapEvaluator(ReducedSchedule alteredSchedule,
      BidConfig bidConfig) {
    ImmutableSet.Builder<LocalDate> builder = ImmutableSet.builder();
    Set<LocalDate> requiredDaysOff = bidConfig.getRequiredDayOffList()
        .stream().map(s -> LocalDate.parse(s)).collect(Collectors.toSet());
    for (LocalDate date : requiredDaysOff) {
      builder.add(date);
    }
    this.requiredDaysOff = builder.build();
    this.alteredSchedule = alteredSchedule;
    this.nonTripIntervals = alteredSchedule.getNonTripIntervals();
    this.bidConfig = bidConfig;
  }

  /** dates are the dates of the trip we're looking at adding. */
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

    Set<Trip> overlappedTrips = new HashSet<>();
    for (Trip trip : alteredSchedule.getRetainedTrips()) {
      if (overlapsDates(trip, dates)) {
        overlappedTrips.add(trip);
      }
    }
    if (!overlappedTrips.isEmpty()) {
      return new OverlapEvaluation(Overlap.RETAINED_TRIP, overlappedTrips);
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

    Set<Trip> overlappedTrips = new HashSet<>();
    for (Trip trip : alteredSchedule.getRetainedTrips()) {
      if (overlapsDates(trip, proposedTrip.getDepartureDates())) {
        overlappedTrips.add(trip);
      }
    }
    if (!overlappedTrips.isEmpty()) {
      return new OverlapEvaluation(Overlap.RETAINED_TRIP, overlappedTrips);
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
  // Returns true if the potential trip dates conflict with a scheduled trip.
  private boolean overlapsDates(Trip scheduledTrip, Set<LocalDate> potentialTripDates) {
    // Add the day before and after a scheduled trip.
    // We don't want to end up with adjacent trips.
    Set<LocalDate> scheduledDates = new HashSet<>(
        scheduledTrip.getDepartureDates());
    Preconditions.checkState(!scheduledDates.isEmpty());
    // Pure overlap.
    // TODO: this does not account for time of day.
    // Hence the comment above to use intervals.
    if (overlaps(scheduledDates, potentialTripDates)) {
      return true;
    }

    // Check for abutment within N days of existing trip.
    if (bidConfig.getMinimumNumberOfDaysBetweenTrips() > 0) {
      Set<LocalDate> extendedScheduledDates = new HashSet<>(scheduledDates);
      for (int i = 0; i < bidConfig.getMinimumNumberOfDaysBetweenTrips(); ++i) {
        extendedScheduledDates
            .add(Ordering.natural().min(extendedScheduledDates).minusDays(1));
        extendedScheduledDates
            .add(Ordering.natural().max(extendedScheduledDates).plusDays(1));
      }
      if (overlaps(extendedScheduledDates, potentialTripDates)) {
        return true;
      }
    }

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
