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

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.util.Clock;
import crewtools.util.Period;

public class ScheduleWrapper {
  private final Logger logger = Logger.getLogger(ScheduleWrapper.class.getName());
  private final Map<PairingKey, Trip> schedule;
  private final Map<PairingKey, Trip> droppableSchedule;  // subset of schedule
  private final Collection<PairingKey> baggageTrips;
  private final Schedule schedulePojo;
  private final YearMonth yearMonth;
  private final Clock clock;
  
  private Map<PairingKey, Period> creditInMonth;  // least first
  private Period totalCreditInMonth;
  private Period minRequiredCredit;
  
  private static final Period SIXTY_FIVE = Period.hours(65);
 
  public ScheduleWrapper(Collection<PairingKey> baggageTrips,
      Schedule schedulePojo,
      YearMonth yearMonth,
      Clock clock) {
    this.schedule = new HashMap<>();
    this.droppableSchedule = new HashMap<>();
    this.creditInMonth = new HashMap<>();
    this.totalCreditInMonth = Period.ZERO;
    this.minRequiredCredit = Period.hours(65);
    this.baggageTrips = baggageTrips;
    this.schedulePojo = schedulePojo;
    this.yearMonth = yearMonth;
    this.clock = clock;
    populate(schedulePojo, yearMonth);
  }

  private void populate(Schedule schedulePojo, YearMonth yearMonth) {
    for (Trip trip : schedulePojo.trips) {
      logger.info("Scheduled trip " + trip.getPairingKey());
      if (!baggageTrips.contains(trip.getPairingKey())) {
        schedule.put(trip.getPairingKey(), trip);
        if (trip.isDroppable()) {
          droppableSchedule.put(trip.getPairingKey(), trip);
        }
        Period creditInMonth = trip.getCreditInMonth(yearMonth);
        totalCreditInMonth = totalCreditInMonth.plus(creditInMonth);
        this.creditInMonth.put(trip.getPairingKey(), creditInMonth);
      }
    }
    this.creditInMonth = crewtools.util.Collections.sortByValue(creditInMonth);
    Period overage = totalCreditInMonth.minus(SIXTY_FIVE);
    logger.finest("(ordered) credit this month: " + creditInMonth);
    logger.finest("total credit this month: " + creditInMonth);
    logger.finest("Credit overage this month: " + overage);
    // This period is the minimum period of a trip we care about in opentime.
    // That is, there exists a droppable trip on our schedule such that dropping
    // it and adding a trip of this credit value will yield the minimum schedule credit.
    this.minRequiredCredit = getSmallestDroppableCredit(creditInMonth).minus(overage);
    logger.finest("Minimum credit for an added trip: " + minRequiredCredit);
  }
  
  // Credits should be ordered from smallest to largest period.
  private Period getSmallestDroppableCredit(Map<PairingKey, Period> credits) {
    LocalDate today = clock.today();
    for (Map.Entry<PairingKey, Period> entry : credits.entrySet()) {
      Trip trip = droppableSchedule.get(entry.getKey());
      if (trip == null) {
        logger.finest(entry.getKey() + " is not droppable");
        continue;
      }
      if (trip.getLastSection().getDepartureDate().isBefore(today)
          || trip.getDepartureDates().contains(today)) {
        logger.finest(entry.getKey() + " in past");
        continue;
      }
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
    Period newCredit = totalCreditInMonth
        .minus(creditInMonth.get(scheduledTrip))
        .plus(tripCredit);
    boolean result = SIXTY_FIVE.compareTo(newCredit) <= 0;
    logger.info("If we drop " + scheduledTrip + " and add " 
        + trip.getPairingName() + " for " + tripCredit + ", is it OK? " + result);
    return result;
  }
  
  public Collection<Trip> getOverlapOrAllDroppable(Trip trip) {
    Set<Trip> result = new HashSet<>();
    for (Map.Entry<PairingKey, Trip> entry : schedule.entrySet()) {
      if (overlapsDates(entry.getValue(), trip)) {
        // The dates overlap.
        if (!droppableSchedule.containsKey(entry.getKey())) {
          // overlaps with something not droppable.  Useless.
          return ImmutableSet.of();
        } else {
          result.add(entry.getValue());
        }
      }
    }
    return result.isEmpty() ? droppableSchedule.values() : result;
  }

  private boolean overlapsDates(Trip scheduledTrip, Trip potentialTrip) {
    // Add the day before and after a scheduled trip.
    // We don't want to end up with adjacent trips.
    Set<LocalDate> scheduledDates = new HashSet<>(scheduledTrip.getDepartureDates());
    Preconditions.checkState(!scheduledDates.isEmpty());
    LocalDate random = scheduledDates.iterator().next();
    LocalDate first = new LocalDate(random);
    while (scheduledDates.contains(first)) {
      first = first.minusDays(1);
    }
    scheduledDates.add(first);
    LocalDate last = new LocalDate(random);
    while (scheduledDates.contains(last)) {
      last = last.plusDays(1);
    }
    scheduledDates.add(last);

    return !Collections.disjoint(scheduledDates, potentialTrip.getDepartureDates());
  }
  
  //
  // Create a new ScheduleWrapper based on adds and drops.
  //
  
  public ScheduleWrapper mutate(List<Trip> adds, List<PairingKey> dropKeys) {
    Collection<PairingKey> newBaggage = new ArrayList<>(baggageTrips);
    newBaggage.removeAll(dropKeys);

    List<PairingKey> addKeys = new ArrayList<>();
    adds.forEach(trip -> addKeys.add(trip.getPairingKey()));
    
    Schedule newSchedule = schedulePojo.mutate(adds, dropKeys);
    ScheduleWrapper newWrapper = new ScheduleWrapper(
        newBaggage, newSchedule, yearMonth, clock);
    return newWrapper;
  }
  
  public Collection<PairingKey> getBaggage() {
    return baggageTrips;
  }
  
  @Override
  public int hashCode() {
    return schedulePojo.hashCode();
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
    return schedulePojo.equals(that.schedulePojo);
  }

  @Override
  public String toString() {
    return schedulePojo.toString();
  }
}
