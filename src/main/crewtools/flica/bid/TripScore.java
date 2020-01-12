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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import com.google.common.collect.ImmutableSet;

import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.rpc.Proto.ScoreAdjustment;
import crewtools.util.Period;

public class TripScore implements Comparable<TripScore> {
  private final Logger logger = Logger.getLogger(TripScore.class.getName());

  public static final int START_END_SCORE_FACTOR = 100;
  public static final LocalTime EARLIEST_START_LOCAL_TIME = new LocalTime(11, 00);
  public static final LocalTime LATEST_END_LOCAL_TIME = new LocalTime(19, 19); // 7:19pm
  private static final int DEUCE_CANOE_FACTOR = 20;
  private static final int DESPISED_TURN_PENALITY = 10000;

  // Hub will be odd, even.., odd. Non-hub all even.
  private static final int IDEAL_NUMBER_OF_LEGS_FIRST_OR_LAST_DAY = 4;
  private static final int IDEAL_NUMBER_OF_LEGS_OTHER_DAYS = 4;

  private final Period favoriteOvernightPeriod;
  private final int numFavoriteOvernights;
  private final int startTimePoints;
  private final int endTimePoints;
  private final boolean hasEquipmentTwoHundredSegments;
  private final int numLegs;
  private final int points;
  private final List<String> scoreExplanation = new ArrayList<>();
  private final Trip trip;

  public TripScore(Trip trip, BidConfig config) {
    int goodPoints = 0;
    int badPoints = 0;

    Period favoriteOvernightPeriod = Period.ZERO;
    int numFavoriteOvernights = 0;

    int numLegs = 0;
    for (Section section : trip.getSections()) {
      if (section.hasLayoverAirportCode()
          && config.getFavoriteOvernightList()
              .contains(section.getLayoverAirportCode())) {
        favoriteOvernightPeriod = favoriteOvernightPeriod
            .plus(section.getLayoverDuration());
        numFavoriteOvernights++;
      }
      numLegs += section.getNumLegs();
    }

    this.favoriteOvernightPeriod = favoriteOvernightPeriod;
    this.numFavoriteOvernights = numFavoriteOvernights;
    this.numLegs = numLegs;

    if (config.getEnableDingPartialTrips()) {
      switch (trip.getSections().size()) {
        case 4:
        case 3:
          break;
        case 2:
          badPoints += 50;
          break;
        case 1:
          badPoints += 75;
          break;
      }
    }

    goodPoints += numFavoriteOvernights * 3;
    if (numFavoriteOvernights > 0) {
      scoreExplanation.add("+" + numFavoriteOvernights * 3 + " for favorite overnights");
    }

    // goodPoints += (favoriteOvernightPeriod.getHours() / 6);
    // if (favoriteOvernightPeriod.getHours() > 0) {
    // debug.add("+" + favoriteOvernightPeriod.getHours() + "/6 for favorite
    // overnights
    // hours");
    // }

    // favorite turns
    for (Section section : trip.getSections()) {
      for (String airportCode : section.getAllTurnAirports()) {
        if (config.getFavoriteTurnList().contains(airportCode)) {
          goodPoints++;
          scoreExplanation.add("+1 for a turn to " + airportCode);
        }
        if (config.getDespisedTurnList().contains(airportCode)) {
          badPoints += DESPISED_TURN_PENALITY;
          scoreExplanation
              .add("-" + DESPISED_TURN_PENALITY + " for a turn to " + airportCode);
        }
      }
    }

    // numLegs
    for (int i = 0; i < trip.getSections().size(); i++) {
      Section section = trip.getSections().get(i);
      boolean isFirstOrLast = i == 0 || i == trip.getNumSections() - 1;
      int idealNumLegs = isFirstOrLast
          ? IDEAL_NUMBER_OF_LEGS_FIRST_OR_LAST_DAY
          : IDEAL_NUMBER_OF_LEGS_OTHER_DAYS;
      int excessiveLegs = section.getNumLegs() - idealNumLegs;
      if (excessiveLegs > 0) {
        badPoints += excessiveLegs;
        scoreExplanation.add(
            String.format("-%d for legs on day %d (%d > %d)",
                excessiveLegs, i + 1, section.getNumLegs(), idealNumLegs));
      }
    }

    // more points are better.
    int startTimePoints = 0;
    int endTimePoints = 0;
    boolean hasEquipmentTwoHundredSegments = false;
    boolean arriveDayEarly = false;
    boolean leaveDayLate = false;

    Section firstSection = trip.getFirstSection();
    if (firstSection != null) {
      if (firstSection.getInitialDeadheadToAirport() != null
          && firstSection.getInitialDeadheadToAirport().equals(
              config.getPreferredOriginAirportCode())) {
        goodPoints += START_END_SCORE_FACTOR;
        scoreExplanation.add("+" + START_END_SCORE_FACTOR + " for initial DH");
      } else {
        LocalTime reportTime = firstSection.getStart().toLocalTime();
        if (!reportTime.isAfter(EARLIEST_START_LOCAL_TIME)) {
          startTimePoints += START_END_SCORE_FACTOR;
          goodPoints += START_END_SCORE_FACTOR;
          scoreExplanation.add("+" + START_END_SCORE_FACTOR + " for good start time");
        } else {
          arriveDayEarly = true;
          badPoints += START_END_SCORE_FACTOR;
          scoreExplanation
              .add("-" + START_END_SCORE_FACTOR + " for bad start time (uncommutable)");
        }
      }
    }

    Section lastSection = trip.getLastSection();
    if (lastSection != null) {
      if (lastSection.getFinalDeadheadFromAirport() != null
          && lastSection.getFinalDeadheadFromAirport().equals(
              config.getPreferredOriginAirportCode())) {
        goodPoints += START_END_SCORE_FACTOR;
        scoreExplanation.add("+" + START_END_SCORE_FACTOR + " for final DH");
      } else {
        LocalTime endTime = lastSection.getEnd().toLocalTime();
        if (!endTime.isAfter(LATEST_END_LOCAL_TIME)) {
          endTimePoints += START_END_SCORE_FACTOR;
          goodPoints += START_END_SCORE_FACTOR;
          scoreExplanation.add("+" + START_END_SCORE_FACTOR + " for good end time");
        } else {
          leaveDayLate = true;
          badPoints += START_END_SCORE_FACTOR;
          scoreExplanation
              .add("-" + START_END_SCORE_FACTOR + " for bad end time (uncommutable)");
        }
      }
    }

    for (Section section : trip.getSections()) {
      if (section.isEquipmentTwoHundred()) {
        hasEquipmentTwoHundredSegments = true;
      }
    }
    if (hasEquipmentTwoHundredSegments) {
      scoreExplanation.add("-" + numLegs + " for 200 segments");
      badPoints += numLegs * DEUCE_CANOE_FACTOR;
    }

    this.startTimePoints = startTimePoints;
    this.endTimePoints = endTimePoints;
    this.hasEquipmentTwoHundredSegments = hasEquipmentTwoHundredSegments;

    if (config.getEnableEfficiencyScore()
        && trip.credit.isMoreThan(Period.ZERO)) {
      // 0.0 is no flying when away from home.
      // 1.0 is flying every minute away from home.
      float efficiency = trip.credit.dividedBy(trip.getDuty());
      int factor = (int) -(((1.0 - efficiency)) * 10);
      scoreExplanation.add(
          String.format("Efficiency %.2f; efficiency factor %d (%s credit / %s duty)",
              efficiency, factor, trip.credit.toString(), trip.getDuty().toString()));
      goodPoints += factor;
    }

    for (ScoreAdjustment scoreAdjustment : config.getScoreAdjustmentList()) {
      int adjustment = scoreAdjustment.getScoreAdjustment();
      if (scoreAdjustment.getCrewEmployeeIdCount() > 0
          && trip.containsCrewmember(scoreAdjustment.getCrewEmployeeIdList())) {
        goodPoints += adjustment;
        scoreExplanation.add(String.format("%d for crew", adjustment));
      }
      if (scoreAdjustment.getSoftDayOffCount() > 0) {
        Set<LocalDate> softDaysOff = scoreAdjustment.getSoftDayOffList()
            .stream().map(s -> LocalDate.parse(s)).collect(Collectors.toSet());
        if (trip.spansDaysOfMonth(softDaysOff)) {
          goodPoints += adjustment;
          scoreExplanation.add(String.format("%d for soft day off", adjustment));
        } else {
          if (arriveDayEarly) {
            if (softDaysOff.contains(trip.getDutyStart().minusDays(1).toLocalDate())) {
              goodPoints += adjustment;
              scoreExplanation.add(
                  String.format("%d for soft day off (uncommutable start)", adjustment));
            }
          }
          if (leaveDayLate) {
            if (softDaysOff.contains(trip.getDutyEnd().plusDays(1).toLocalDate())) {
              goodPoints += adjustment;
              scoreExplanation.add(
                  String.format("%d for soft day off (uncommutable end)", adjustment));
            }
          }
        }
      }
      if (scoreAdjustment.getPreferWeekdays()) {
        int dayAdjustment = computeDayAdjustment(trip.getDepartureDates(), WEEKDAYS,
            adjustment);
        goodPoints += dayAdjustment;
        scoreExplanation.add(String.format("%d for weekdays", dayAdjustment));
      }
      if (scoreAdjustment.getPreferWeekends()) {
        int dayAdjustment = computeDayAdjustment(trip.getDepartureDates(), WEEKENDS,
            adjustment);
        goodPoints += dayAdjustment;
        scoreExplanation.add(String.format("%d for weekends", dayAdjustment));
      }
    }

    this.points = goodPoints - badPoints;
    this.trip = trip;
    scoreExplanation.add("Final score: " + points);
  }

  //@formatter:off

  private static final Set<Integer> WEEKDAYS = ImmutableSet.of(
      DateTimeConstants.MONDAY,
      DateTimeConstants.TUESDAY,
      DateTimeConstants.WEDNESDAY,
      DateTimeConstants.THURSDAY,
      DateTimeConstants.FRIDAY);

  private static final Set<Integer> WEEKENDS = ImmutableSet.of(
      DateTimeConstants.FRIDAY,
      DateTimeConstants.SATURDAY,
      DateTimeConstants.SUNDAY);

  private int computeDayAdjustment(
      Set<LocalDate> tripDates, Set<Integer> preferredDaysOfWeek, int adjustment) {
    int dayAdjustment = 0;
    for (LocalDate tripDate : tripDates) {
      if (preferredDaysOfWeek.contains(tripDate.getDayOfWeek())) {
        dayAdjustment += adjustment;
      }
    }
    return dayAdjustment;
  }

  //@formatter:on

  public int getNumFavoriteOvernights() {
    return numFavoriteOvernights;
  }

  public Period getFavoriteOvernightPeriod() {
    return favoriteOvernightPeriod;
  }

  /** Points are good.  The more points, the better. */
  public int getStartTimePoints() {
    return startTimePoints;
  }

  /** Points are good.  The more points, the better. */
  public int getEndTimePoints() {
    return endTimePoints;
  }

  public int getNumLegs() {
    return numLegs;
  }

  public boolean hasEquipmentTwoHundredSegments() {
    return hasEquipmentTwoHundredSegments;
  }

  public int getPoints() {
    return points;
  }

  public List<String> getScoreExplanation() {
    return scoreExplanation;
  }

  @Override
  public int compareTo(TripScore that) {
    return new Integer(getPoints()).compareTo(that.getPoints());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof TripScore)) {
      return false;
    }
    TripScore that = (TripScore) o;
    return ((Integer) this.getPoints()).equals(that.getPoints());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getPoints());
  }

  @Override
  public String toString() {
    return trip.getPairingName() + ":" + getPoints();
  }
}
