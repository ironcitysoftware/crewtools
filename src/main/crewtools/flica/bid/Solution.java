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

import java.util.logging.Level;
import java.util.logging.Logger;

import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;

public class Solution {
  private final Logger logger = Logger.getLogger(Solution.class.getName());

  private final ProposedSchedule proposedSchedule;
  private final int score;

  public Solution(ProposedSchedule proposedSchedule, TripDatabase tripDatabase, BidConfig bidConfig) {
    this.proposedSchedule = proposedSchedule;
    ReducedSchedule reducedSchedule = proposedSchedule.getReducedSchedule();
    int score = reducedSchedule.getScore();
    for (PairingKey addKey : proposedSchedule.getAddedKeys()) {
      try {
        Trip trip = tripDatabase.getTrip(addKey);
        TripScore tripScore = new TripScore(trip, bidConfig);
        score += tripScore.getPoints();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Error getting trip " + addKey, e);
        score = Integer.MIN_VALUE;
      }
    }
    this.score = score;
  }

  public ProposedSchedule getProposedSchedule() {
    return proposedSchedule;
  }

  public int getScore() {
    return score;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof Solution)) {
      return false;
    }
    Solution that = (Solution) o;
    return proposedSchedule.equals(that.proposedSchedule);
  }

  @Override
  public int hashCode() {
    return proposedSchedule.hashCode();
  }

  @Override
  public String toString() {
    return proposedSchedule.toString() + ", score: " + score;
  }
}