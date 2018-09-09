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
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;

public class MonthlyBidStrategy implements Comparator<LineScore> {
  private final Logger logger = Logger.getLogger(MonthlyBidStrategy.class.getName());

  private final BidConfig bidConfig;

  public MonthlyBidStrategy(BidConfig bidConfig) {
    this.bidConfig = bidConfig;
  }

  @Override
  public int compare(LineScore a, LineScore b) {
    int aOverrideIndex = bidConfig.getMonthlyBidOverrideList().indexOf(a.getLineName());
    int bOverrideIndex = bidConfig.getMonthlyBidOverrideList().indexOf(b.getLineName());
    // 104
    if (aOverrideIndex > -1 && bOverrideIndex > -1) {
      return Integer.compare(aOverrideIndex, bOverrideIndex);
    }
    if (aOverrideIndex > -1 && bOverrideIndex == -1) {
      return -1;
    }
    if (aOverrideIndex == -1 && bOverrideIndex > -1) {
      return +1;
    }
    int isDesirable = new Boolean(a.isDesirableLine())
        .compareTo(b.isDesirableLine());
    if (isDesirable != 0) {
      return -isDesirable;
    }

    int minTripEligible =
        new Boolean(a.hasMinimumTripsThatMeetMinCredit()).compareTo(b.hasMinimumTripsThatMeetMinCredit());
    if (minTripEligible != 0) {
      return -minTripEligible;
    }
    
    // both LineScores either are or are not N-trip eligible.
    
    int aPoints = 0;
    String aTrips = "";
    for (Trip trip : getTrips(a)) {
      aPoints += new TripScore(trip, bidConfig).getPoints();
      if (!aTrips.isEmpty()) {
        aTrips += ", ";
      }
      aTrips += trip.getPairingName();
    }

    int bPoints = 0;
    String bTrips = "";
    for (Trip trip : getTrips(b)) {
      bPoints += new TripScore(trip, bidConfig).getPoints();
      if (!bTrips.isEmpty()) {
        bTrips += ", ";
      }
      bTrips += trip.getPairingName();
    }

    logger.fine("T" + a.getLineName() + " (" + aTrips + "=" + aPoints + ") vs "
        + "T" + b.getLineName() + " (" + bTrips + "=" + bPoints + ")");
    
    return -((Integer) aPoints).compareTo(bPoints);
  }
    
  private List<Trip> getTrips(LineScore lineScore) {
    if (lineScore.hasMinimumTripsThatMeetMinCredit()) {
      return new ArrayList<>(lineScore.getMinimumTripsThatMeetMinCredit().keySet());
    } else {
      return new ArrayList<>(lineScore.getTrips());
    }
  }
}
