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

package crewtools.flica.bid.alpha;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.FlicaService;
import crewtools.flica.bid.AutoBidderCommandLineConfig;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;
import crewtools.util.Period;

public class ScheduleWrapper {
  // TODO: config
  private static final List<PairingKey> BAGGAGE_KEYS = ImmutableList.of();
  private static final Set<Integer> GOOD_STARTING_DAYS = ImmutableSet.of();
  private static final Period MIN_CREDIT = null;

  private final Logger logger = Logger.getLogger(ScheduleWrapper.class.getName());
  private final LocalDate TODAY = LocalDate.now();

  private final Schedule schedule;
  private final YearMonth yearMonth;
  private final Clock clock;
  private final BidConfig bidConfig;
  private final AutoBidderCommandLineConfig cmdLine;
  private final Map<PairingKey, Trip> trips;

  private Map<PairingKey, FlicaTask> addedTrips = new HashMap<>();
  private Set<PairingKey> removedTrips = new HashSet<>();

  public ScheduleWrapper(
      Schedule schedule,
      YearMonth yearMonth,
      Clock clock,
      BidConfig bidConfig,
      AutoBidderCommandLineConfig cmdLine) {
    this.schedule = schedule;
    this.yearMonth = yearMonth;
    this.clock = clock;
    this.bidConfig = bidConfig;
    this.cmdLine = cmdLine;
    this.trips = new TreeMap<>();
    for (Trip trip : schedule.trips) {
      Preconditions.checkState(!trip.hasScheduleType());
      logger.info("Scheduled trip " + trip.getPairingKey());
      trips.put(trip.getPairingKey(), trip);
    }
  }

  public boolean isBestDates() {
    for (PairingKey baggageKey : BAGGAGE_KEYS) {
      if (trips.containsKey(baggageKey) && !removedTrips.contains(baggageKey)) {
        return false;
      }
    }
    for (FlicaTask addedTask : addedTrips.values()) {
      if (addedTask.numDays != 2 || addedTask.numDays != 4) {
        return false;
      }
    }
    for (Trip trip : trips.values()) {
      if (!GOOD_STARTING_DAYS.contains(trip.getStartingDayOfMonth())) {
        return false;
      }
    }
    return true;
  }

  // used by evaluate to find the four trips in the schedule.
  public Collection<PairingKey> getOrderedKeys() {
    Map<Integer, PairingKey> result = new TreeMap<>();
    for (PairingKey key : trips.keySet()) {
      if (!removedTrips.contains(key)) {
        result.put(trips.get(key).getStartingDayOfMonth(), key);
      }
    }
    for (PairingKey key : addedTrips.keySet()) {
      if (!removedTrips.contains(key)) {
        result.put(key.getPairingDate().getDayOfMonth(), key);
      }
    }
    if (result.size() != 4) {
      logger.info("MISTAKE: result does not have 4 keys: " + result);
    }
    return trips.keySet();
  }

  public Trip getTripWithoutCheckingForRemoval(PairingKey key) {
    return trips.get(key);
  }

  public Period currentCredit() {
    Period currentCredit = Period.ZERO;
    for (Trip trip : trips.values()) {
      if (!removedTrips.contains(trip.getPairingKey())) {
        currentCredit = currentCredit.plus(trip.credit);
      }
    }
    for (PairingKey key : addedTrips.keySet()) {
      if (!removedTrips.contains(key)) {
        currentCredit = currentCredit.plus(addedTrips.get(key).creditTime);
      }
    }
    return currentCredit;
  }

  public void swap(PairingKey dropKey, FlicaTask addTask, FlicaService service)
      throws URISyntaxException, IOException {
    if (addedTrips.containsValue(addTask)) {
      logger.info("Ignoring previous add " + addTask.pairingName);
      return;
    }
    if (removedTrips.contains(dropKey)) {
      logger.info("Ignoring previous drop " + dropKey);
      return;
    }

    Period currentCredit = currentCredit();
    Period dropCredit = trips.get(dropKey).credit;
    Period addCredit = addTask.creditTime;
    Period newCredit = currentCredit.minus(dropCredit).plus(addCredit);
    if (newCredit.isLessThan(MIN_CREDIT)) {
      logger.info("Cowardly refusing to drop " + dropKey + " for " + addTask.pairingName
          + " due to min credit\n      " +
          String.format("Credit: %s - %s + %s = %s",
              currentCredit, dropCredit, addCredit, newCredit));
      return;
    } else {
      logger.info("Swap from " + dropKey + " to " + addTask.pairingName + "\n      " +
          String.format("Credit: %s - %s + %s = %s",
              currentCredit, dropCredit, addCredit, newCredit));
    }

    PairingKey addKey = new PairingKey(addTask.pairingDate, addTask.pairingName);
    if (!cmdLine.isDebug()) {
      service.submitSwap(cmdLine.getRound(), yearMonth, TODAY,
          ImmutableList.of(addKey),
          ImmutableList.of(dropKey));
    }
    addedTrips.put(addKey, addTask);
    removedTrips.add(dropKey);
  }

  // if no swaps have gone through, don't discard this schedule object.
  public boolean equalsOriginal(ScheduleWrapper that) {
    return this.trips.equals(that.trips);
  }
}
