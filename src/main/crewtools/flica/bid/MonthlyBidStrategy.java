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

import java.util.Comparator;
import java.util.logging.Logger;

import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Period;

public class MonthlyBidStrategy implements Comparator<LineScore> {
  private final Logger logger = Logger.getLogger(MonthlyBidStrategy.class.getName());

  private static final Period MIN_CREDIT = Period.hours(65);

  private final BidConfig bidConfig;

  public MonthlyBidStrategy(BidConfig bidConfig) {
    this.bidConfig = bidConfig;
  }

  @Override
  public int compare(LineScore a, LineScore b) {
    int aOverrideIndex = bidConfig.getMonthlyBidOverrideList().indexOf(a.getLineName());
    int bOverrideIndex = bidConfig.getMonthlyBidOverrideList().indexOf(b.getLineName());
    if (aOverrideIndex > -1 && bOverrideIndex > -1) {
      return Integer.compare(aOverrideIndex, bOverrideIndex);
    }
    if (aOverrideIndex > -1 && bOverrideIndex == -1) {
      return -1;
    }
    if (aOverrideIndex == -1 && bOverrideIndex > -1) {
      return +1;
    }
    int isReserve = new Boolean(a.hasReserve()).compareTo(b.hasReserve());
    if (isReserve != 0) {
      return -isReserve;
    }


    if (bidConfig.getEnableMontlySortByDesirable()) {
      int isDesirable = new Boolean(a.isDesirableLine())
          .compareTo(b.isDesirableLine());
      if (isDesirable != 0) {
        return -isDesirable;
      }
    }

    int aHighest = new Integer(
        a.getNHighestCreditsPlusCarryIn().compareTo(MIN_CREDIT));
    int bHighest = new Integer(
        b.getNHighestCreditsPlusCarryIn().compareTo(MIN_CREDIT));
    if (aHighest >= 0 ^ bHighest >= 0) {
      return (aHighest >= 0) ? -1 : 1;
    }

    if (bidConfig.getEnableMonthlySortByCredit()) {
      int creditCmp = new Integer(
          a.getNHighestCreditsPlusCarryIn().compareTo(
              b.getNHighestCreditsPlusCarryIn()));
      if (creditCmp != 0) {
        return -creditCmp;
      }
    }

    int aPoints = 0;
    String aTrips = "";
    for (Trip trip : a.getMinimumTrips()) {
      aPoints += new TripScore(trip, bidConfig).getPoints();
      if (!aTrips.isEmpty()) {
        aTrips += ", ";
      }
      aTrips += trip.getPairingName();
    }
    aPoints += a.getScoreAdjustmentPoints();

    int bPoints = 0;
    String bTrips = "";
    for (Trip trip : b.getMinimumTrips()) {
      bPoints += new TripScore(trip, bidConfig).getPoints();
      if (!bTrips.isEmpty()) {
        bTrips += ", ";
      }
      bTrips += trip.getPairingName();
    }
    bPoints += b.getScoreAdjustmentPoints();

    logger.fine("T" + a.getLineName() + " (" + aTrips + "=" + aPoints + ") vs "
        + "T" + b.getLineName() + " (" + bTrips + "=" + bPoints + ")");

    return -((Integer) aPoints).compareTo(bPoints);
  }
}
