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
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

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

  private final Period favoriteOvernightPeriod;
  private final int numFavoriteOvernights;
  private final int startTimePoints;
  private final int endTimePoints;
  private final boolean hasEquipmentTwoHundredSegments;
  private final int numLegs;
  private final int points;
  private final List<String> scoreExplanation = new ArrayList<>();
  
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
      }
    }

    // numLegs
    for (int i = 0; i < trip.getSections().size(); i++) {
      Section section = trip.getSections().get(i);
      boolean isFirstOrLast = i == 0 || i == trip.getNumSections() - 1;
      int idealNumLegs = isFirstOrLast ? 3 : 2;
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
    
    Section firstSection = trip.getFirstSection();
    if (firstSection != null) {
      LocalTime reportTime = firstSection.getStart().toLocalTime();
      if (reportTime.getHourOfDay() > 9 && reportTime.getHourOfDay() <= 20) {
        startTimePoints++;
        goodPoints++;
        scoreExplanation.add("+1 for good start time");
      } else {
        badPoints++;
        scoreExplanation.add("-1 for bad start time");
      }
    }
      
    Section lastSection = trip.getLastSection();
    if (lastSection != null) {
      LocalTime endTime = lastSection.getEnd().toLocalTime();
      if (endTime.getHourOfDay() <= 18) {
        endTimePoints++;
        goodPoints++;        
        scoreExplanation.add("+1 for good end time");
      } else {
        badPoints++;
        scoreExplanation.add("-1 for bad end time");
      }
    }
    
    for (Section section : trip.getSections()) {
      if (section.isEquipmentTwoHundred()) {
        hasEquipmentTwoHundredSegments = true;
      }
    }
    if (hasEquipmentTwoHundredSegments) {
      scoreExplanation.add("-" + numLegs + " for 200 segments");
      badPoints += numLegs;
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
      if (scoreAdjustment.getSoftDayOffCount() > 0
          && trip.spansDaysOfMonth(scoreAdjustment.getSoftDayOffList())) {
        goodPoints += adjustment;
        scoreExplanation.add(String.format("%d for soft day off", adjustment));
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
}
