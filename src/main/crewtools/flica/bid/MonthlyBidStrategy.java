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
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.Ordering;

import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Period;

public class MonthlyBidStrategy implements Comparator<LineScore> {
  private final Logger logger = Logger.getLogger(MonthlyBidStrategy.class.getName());

  private final BidConfig bidConfig;
  private final Period minimumCredit;
  private List<String> currentDebug;

  public MonthlyBidStrategy(BidConfig bidConfig) {
    this.bidConfig = bidConfig;
    this.minimumCredit = Period.hours(bidConfig.getMinimumCreditHours());
  }

  public void setDebug(List<String> debug) {
    currentDebug = debug;
  }

  private void debug(String spec, Object... args) {
    if (currentDebug != null) {
      currentDebug.add(String.format(spec, args));
    }
  }

  @Override
  public int compare(LineScore a, LineScore b) {
    int aOverrideIndex = bidConfig.getMonthlyBidOverrideList().indexOf(a.getLineName());
    int bOverrideIndex = bidConfig.getMonthlyBidOverrideList().indexOf(b.getLineName());
    if (aOverrideIndex > -1 && bOverrideIndex > -1) {
      debug("override:left %d vs right %d", aOverrideIndex, bOverrideIndex);
      return Integer.compare(aOverrideIndex, bOverrideIndex);
    } else if (aOverrideIndex > -1 && bOverrideIndex == -1) {
      debug("override:left %d vs right %d", aOverrideIndex, bOverrideIndex);
      return -1;
    } else if (aOverrideIndex == -1 && bOverrideIndex > -1) {
      debug("override:left %d vs right %d", aOverrideIndex, bOverrideIndex);
      return +1;
    }

    int aLoserIndex = bidConfig.getLoserLineList().indexOf(a.getLineName());
    int bLoserIndex = bidConfig.getLoserLineList().indexOf(b.getLineName());
    if (aLoserIndex > -1 && bLoserIndex > -1) {
      debug("loser:left %d vs right %d", aLoserIndex, bLoserIndex);
      return Integer.compare(aLoserIndex, bLoserIndex);
    } else if (aLoserIndex > -1 && bLoserIndex == -1) {
      debug("loser:left %d vs right %d", aLoserIndex, bLoserIndex);
      return +1;
    } else if (aLoserIndex == -1 && bLoserIndex > -1) {
      debug("loser:left %d vs right %d", aLoserIndex, bLoserIndex);
      return -1;
    }

    int isReserve = new Boolean(a.hasReserve()).compareTo(b.hasReserve());
    if (isReserve != 0) {
      debug("reserve:left %s vs right %s", "" + a.hasReserve(), "" + b.hasReserve());
      return -isReserve;
    }

    if (bidConfig.getEnableMonthlySortByDesirable()) {
      int isDesirable = new Boolean(a.isDesirableLine())
          .compareTo(b.isDesirableLine());
      if (isDesirable != 0) {
        debug("desirable:left %s vs right %s", "" + a.isDesirableLine(),
            "" + b.isDesirableLine());
        return -isDesirable;
      }
    }

    if (bidConfig.getEnableWeekendWorkScoring()) {
      int weekendWork = new Integer(a.getNumWeekendWorkdays())
          .compareTo(b.getNumWeekendWorkdays());
      if (weekendWork != 0) {
        debug("weekend work:left %d vs right %d", a.getNumWeekendWorkdays(),
            b.getNumWeekendWorkdays());
        return weekendWork;
      }
    }

    int aHighest = new Integer(
        a.getNHighestCreditsPlusCarryIn().compareTo(minimumCredit));
    int bHighest = new Integer(
        b.getNHighestCreditsPlusCarryIn().compareTo(minimumCredit));
    if (aHighest >= 0 ^ bHighest >= 0) {
      debug("highestCredit:left %d vs right %d", aHighest, bHighest);
      return (aHighest >= 0) ? -1 : 1;
    }

    if (bidConfig.getEnableMonthlySortByCredit()) {
      int creditCmp = new Integer(
          a.getNHighestCreditsPlusCarryIn().compareTo(
              b.getNHighestCreditsPlusCarryIn()));
      if (creditCmp != 0) {
        debug("sortedCredit:left %s vs right %s",
            a.getNHighestCreditsPlusCarryIn().toString(),
            b.getNHighestCreditsPlusCarryIn().toString());
        return -creditCmp;
      }
    }

    // sort 5-days-in-a-row below 4-days-in-a-row
    int aMaxLength = Ordering.natural().max(a.getTripLengthToCount().keySet());
    int bMaxLength = Ordering.natural().max(b.getTripLengthToCount().keySet());
    if (bidConfig.getEnableTripLengthScoring()) {
      if (aMaxLength > 4 || bMaxLength > 4) {
        if (aMaxLength != bMaxLength) {
          debug("maxLength left %d vs right %d", aMaxLength, bMaxLength);
          return Integer.compare(aMaxLength, bMaxLength);
        } else {
          debug("maxLength:%d count left %d vs count right %d",
              aMaxLength,
              a.getTripLengthToCount().get(aMaxLength),
              b.getTripLengthToCount().get(bMaxLength));
          return Integer.compare(
              a.getTripLengthToCount().get(aMaxLength),
              b.getTripLengthToCount().get(bMaxLength));
        }
      }
    }

    int aPoints = 0;
    String aTrips = "";
    for (Trip trip : a.getMinimumTrips()) {
      TripScore tripScore = new TripScore(trip, bidConfig);
      debug("Scoring trip %s", trip.getPairingName());
      for (String explanation : tripScore.getScoreExplanation()) {
        debug("   %s", explanation);
      }
      debug(" left:%s points %d", trip.getPairingName(), tripScore.getPoints());
      aPoints += tripScore.getPoints();
      if (!aTrips.isEmpty()) {
        aTrips += ", ";
      }
      aTrips += trip.getPairingName();
    }
    debug(" left:adjst points %d", a.getScoreAdjustmentPoints());
    aPoints += a.getScoreAdjustmentPoints();

    int bPoints = 0;
    String bTrips = "";
    for (Trip trip : b.getMinimumTrips()) {
      TripScore tripScore = new TripScore(trip, bidConfig);
      debug("Scoring trip %s", trip.getPairingName());
      for (String explanation : tripScore.getScoreExplanation()) {
        debug("   %s", explanation);
      }
      debug("right:%s points %d", trip.getPairingName(), tripScore.getPoints());
      bPoints += tripScore.getPoints();
      if (!bTrips.isEmpty()) {
        bTrips += ", ";
      }
      bTrips += trip.getPairingName();
    }
    debug("right:adjst points %d", b.getScoreAdjustmentPoints());
    bPoints += b.getScoreAdjustmentPoints();

    logger.fine("T" + a.getLineName() + " (" + aTrips + "=" + aPoints + ") vs "
        + "T" + b.getLineName() + " (" + bTrips + "=" + bPoints + ")");

    debug("points:left %d vs right %d", aPoints, bPoints);

    return -((Integer) aPoints).compareTo(bPoints);
  }
}
