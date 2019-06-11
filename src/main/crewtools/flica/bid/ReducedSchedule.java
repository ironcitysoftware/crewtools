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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.Interval;

import com.google.common.base.Preconditions;

import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Period;

public class ReducedSchedule {
  private final Logger logger = Logger.getLogger(ReducedSchedule.class.getName());

  private final Schedule schedule;
  private final int numWorkingDays;
  private final Set<Trip> retainedTrips;
  private final Period credit;
  private final Set<PairingKey> dropKeys;
  private final int score;

  public ReducedSchedule(Schedule schedule, Set<PairingKey> retainedTripKeys,
      BidConfig bidConfig) {
    this.schedule = schedule;

    int numWorkingDays = 0;
    Map<PairingKey, Integer> workDays = schedule.getNumWorkDays();
    for (PairingKey key : retainedTripKeys) {
      numWorkingDays += workDays.get(key);
    }
    this.numWorkingDays = numWorkingDays;

    this.retainedTrips = new HashSet<>();
    Period credit = schedule.getNonTripCreditInMonth();
    int score = 0;
    for (PairingKey retainedTripKey : retainedTripKeys) {
      Trip trip = schedule.getTrips().get(retainedTripKey);
      Preconditions.checkNotNull(trip);
      retainedTrips.add(trip);
      credit = credit.plus(schedule.getTripCreditInMonth().get(retainedTripKey));
      score += new TripScore(trip, bidConfig).getPoints();
    }
    this.credit = credit;
    this.score = score;

    this.dropKeys = new HashSet<>();
    for (PairingKey key : schedule.getTripCreditInMonth().keySet()) {
      if (!retainedTripKeys.contains(key)) {
        dropKeys.add(key);
      }
    }
  }

  public int getOriginalNumWorkingDays() {
    return schedule.getTotalNumWorkDays();
  }

  public int getNumWorkingDays() {
    return numWorkingDays;
  }

  public Set<Trip> getRetainedTrips() {
    return retainedTrips;
  }

  public Set<Interval> getNonTripIntervals() {
    return schedule.getNonTripIntervals();
  }

  public Period getCredit() {
    return credit;
  }

  public Set<PairingKey> getDropKeys() {
    return dropKeys;
  }

  public int getScore() {
    return score;
  }
}
