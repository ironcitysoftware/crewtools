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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;
import crewtools.util.Period;

public class ScheduleWrapper {
  private final Logger logger = Logger.getLogger(ScheduleWrapper.class.getName());
  private final Map<PairingKey, Trip> trips;

  /**
   * Potential opentime trips which overlap this date will be discarded.
   */
  private static final Set<LocalDate> REQUIRED_DAYS_OFF = ImmutableSet.of(
  );

  // subset of schedule
  // only contains future, droppable trips.
  private final Map<PairingKey, Trip> droppableSchedule;

  private final Collection<PairingKey> baggageTrips;
  private final Schedule schedule;
  private final YearMonth yearMonth;
  private final Clock clock;
  private final BidConfig bidConfig;
  
  private Set<Interval> nonTripIntervals;
  private Map<PairingKey, Period> creditInMonthMap;  // least first
  private Period minRequiredCredit;
  
  private static final Period SIXTY_FIVE = Period.hours(65);
 
  public ScheduleWrapper(
      Schedule schedule,
      YearMonth yearMonth,
      Clock clock,
      BidConfig bidConfig) {
    this.trips = new HashMap<>();  // Trips (not vacation or training)
    this.droppableSchedule = new HashMap<>();
    this.nonTripIntervals = new HashSet<>();
    this.creditInMonthMap = new HashMap<>();
    this.minRequiredCredit = SIXTY_FIVE;
    this.baggageTrips = new ArrayList<>();
    this.schedule = schedule;
    this.yearMonth = yearMonth;
    this.clock = clock;
    this.bidConfig = bidConfig;
    populate(schedule, yearMonth);
  }

  private void populate(Schedule schedule, YearMonth yearMonth) {
    Collection<PairingKey> allBaggageTrips = identifyBaggageTrips(schedule);
    logger.info("baggage trips: " + allBaggageTrips);
    Period nonBaggageCreditThisMonth = Period.ZERO;
    for (Trip trip : schedule.trips) {
      if (trip.hasScheduleType()) {
        // Vacation, training, etc.
        mergeNonTripInterval(trip.getInterval());
        nonBaggageCreditThisMonth = nonBaggageCreditThisMonth.plus(trip.credit);
      } else {
        logger.info("Scheduled trip " + trip.getPairingKey());
        if (!allBaggageTrips.contains(trip.getPairingKey())) {
          trips.put(trip.getPairingKey(), trip);
          if (trip.isDroppable() && trip.getDutyStart().isAfter(clock.now())) {
            droppableSchedule.put(trip.getPairingKey(), trip);
          }
          Period tripCredit = trip.getCreditInMonth(yearMonth);
          this.creditInMonthMap.put(trip.getPairingKey(), tripCredit);
          nonBaggageCreditThisMonth = nonBaggageCreditThisMonth.plus(tripCredit);
        } else {
          baggageTrips.add(trip.getPairingKey());
        }
      }
    }
    logger.info("Non-trip intervals: " + nonTripIntervals);
    logger.info("non-baggage credit this month: " + nonBaggageCreditThisMonth);
    this.creditInMonthMap = crewtools.util.Collections.sortByValueAscending(creditInMonthMap);
    Period overage = nonBaggageCreditThisMonth.minus(SIXTY_FIVE);
    logger.info("(ordered) credit this month: " + creditInMonthMap);
    logger.info("non-baggage credit overage this month: " + overage);
    // This period is the minimum period of a trip we care about in opentime.
    // That is, there exists a droppable trip on our schedule such that dropping
    // it and adding a trip of this credit value will yield the minimum schedule credit.
    //
    // creditInMonthMap does not include baggage.
    this.minRequiredCredit = getSmallestDroppableCredit(creditInMonthMap).minus(overage);
    logger.info("Minimum credit for an added trip: " + minRequiredCredit);
  }
  
  // Credits should be ordered from smallest to largest period.
  private Period getSmallestDroppableCredit(Map<PairingKey, Period> credits) {
    for (Map.Entry<PairingKey, Period> entry : credits.entrySet()) {
      Trip trip = droppableSchedule.get(entry.getKey());
      if (trip == null) {
        logger.finest(entry.getKey() + " is not droppable");
        continue;
      }
      logger.info(
          "Smallest droppable credit is " + entry.getKey() + " for " + entry.getValue());
      return entry.getValue();
    }
    logger.severe("Can't find any droppable trips?");
    return SIXTY_FIVE;
  }
  
  public boolean meetsMinimumCredit(Trip trip, YearMonth yearMonth) {
    Period tripCredit = trip.getCreditInMonth(yearMonth);
    logger.info("Trip " + trip.getPairingName() + " credit=" + tripCredit + 
        ", minRequired=" + minRequiredCredit);
    return tripCredit.compareTo(minRequiredCredit) >= 0;
  }
  
  public boolean meetsMinimumCredit(
      PairingKey scheduledTrip, Trip trip, YearMonth yearMonth) {
    Period tripCredit = trip.getCreditInMonth(yearMonth);
    Period newCredit = schedule.getCreditInMonth()
        .minus(creditInMonthMap.get(scheduledTrip))
        .plus(tripCredit);
    boolean result = SIXTY_FIVE.compareTo(newCredit) <= 0;
    logger.info("If we drop " + scheduledTrip + " and add " 
        + trip.getPairingName() + " for " + tripCredit + ", is it OK? " + result
        + "\nTotalCreditInMonth:" + schedule.getCreditInMonth()
        + " - scheduledTrip:" + creditInMonthMap.get(scheduledTrip)
        + " + tripCredit:" + tripCredit
        + " = " + newCredit);
    return result;
  }
  
  /**
   * trip is a potential trip that we want to add to our schedule. If trip
   * overlaps with one or more existing trips, returns those trips.
   *
   * If the trip overlaps with something undroppable (eg vacation or training),
   * then returns "overlapsUndroppable".
   */
  public class OverlapEvaluation {
    public boolean overlapsUndroppable;
    public boolean noOverlap;
    public Collection<Trip> droppable;

    public OverlapEvaluation(Trip trip) {
      // Company vacation or training.
      if (overlapsNonTrip(trip.getInterval())) {
        overlapsUndroppable = true;
        droppable = ImmutableSet.of();
        return;
      }

      // Hard-and-fast days off.
      if (overlaps(trip.getDepartureDates(), REQUIRED_DAYS_OFF)) {
        overlapsUndroppable = true;
        droppable = ImmutableSet.of();
        return;
      }

      Set<Trip> result = new HashSet<>();
      for (Map.Entry<PairingKey, Trip> entry : trips.entrySet()) {
        if (overlapsDates(entry.getValue(), trip)) {
          // The dates overlap.
          if (!droppableSchedule.containsKey(entry.getKey())) {
            // overlaps with something not droppable. Useless.
            overlapsUndroppable = true;
            droppable = ImmutableSet.of();
            return;
          } else {
            result.add(entry.getValue());
          }
        }
      }
      noOverlap = result.isEmpty();
      droppable = noOverlap ? droppableSchedule.values() : result;
    }

    // TODO use Interval instead.
    private boolean overlapsDates(Trip scheduledTrip, Trip potentialTrip) {
      // Add the day before and after a scheduled trip.
      // We don't want to end up with adjacent trips.
      Set<LocalDate> scheduledDates = new HashSet<>(scheduledTrip.getDepartureDates());
      Preconditions.checkState(!scheduledDates.isEmpty());
      scheduledDates.add(Ordering.natural().min(scheduledDates).minusDays(1));
      scheduledDates.add(Ordering.natural().max(scheduledDates).plusDays(1));
      return overlaps(scheduledDates, potentialTrip.getDepartureDates());
    }

    private boolean overlaps(Collection<LocalDate> a, Collection<LocalDate> b) {
      return !Collections.disjoint(a, b);
    }
  }

  public OverlapEvaluation evaluateOverlap(Trip trip) {
    return new OverlapEvaluation(trip);
  }

  public Collection<Trip> getAllDroppable() {
    return droppableSchedule.values();
  }
  
  //
  // Create a new ScheduleWrapper based on adds and drops.
  //
  
  public ScheduleWrapper mutate(List<Trip> adds, List<PairingKey> dropKeys) {
    List<PairingKey> addKeys = new ArrayList<>();
    adds.forEach(trip -> addKeys.add(trip.getPairingKey()));
    
    Schedule newSchedule = schedule.copyAndModify(adds, dropKeys);
    ScheduleWrapper newWrapper = new ScheduleWrapper(
        newSchedule, yearMonth, clock, bidConfig);
    return newWrapper;
  }
  
  /** Returns any baggage trips that remain in this schedule. */
  public Collection<PairingKey> getBaggage() {
    return baggageTrips;
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

  /**
   * Returns the set of the smallest-credit trips beyond the minimum
   * month credit. These are trips which we desire to drop.
   */
  Collection<PairingKey> identifyBaggageTrips(Schedule schedule) {
    List<PairingKey> baggageKeys = new ArrayList<>();
    if (bidConfig.getBaggagePairingKeyCount() > 0) {
      // manual
      for (String keyText : bidConfig.getBaggagePairingKeyList()) {
        PairingKey key = PairingKey.parseShort(keyText,
            YearMonth.parse(bidConfig.getYearMonth()));
        baggageKeys.add(key);
      }
    } else {
      // automatic
      Map<PairingKey, Period> sortedTripCreditsInMonth = crewtools.util.Collections
          .sortByValueDescending(
              schedule.getTripCreditInMonth());
      Period totalCredit = schedule.getNonTripCreditInMonth();
      for (PairingKey key : sortedTripCreditsInMonth.keySet()) {
        if (totalCredit.isLessThan(SIXTY_FIVE)) {
          totalCredit = totalCredit.plus(sortedTripCreditsInMonth.get(key));
        } else {
          baggageKeys.add(key);
        }
      }
    }
    return baggageKeys;
  }

  private boolean overlapsNonTrip(Interval interval) {
    for (Interval existingInterval : nonTripIntervals) {
      if (existingInterval.overlaps(interval)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return schedule.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof ScheduleWrapper)) {
      return false;
    }
    ScheduleWrapper that = (ScheduleWrapper) o;
    return schedule.equals(that.schedule);
  }

  @Override
  public String toString() {
    String result = schedule.toString();
    if (!getBaggage().isEmpty()) {
      result += "\nBaggage keys: " + getBaggage();
    }
    return result;
  }

  public void populate(crewtools.rpc.Proto.ScheduleNode.Builder builder) {
    for (PairingKey key : creditInMonthMap.keySet()) {
      builder.addTrip(key.toShortString());
    }
  }
}
