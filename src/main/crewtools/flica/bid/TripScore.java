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
import crewtools.util.Period;

public class TripScore implements Comparable<TripScore> {
  private final Logger logger = Logger.getLogger(TripScore.class.getName());

  private static final Set<String> FAVORITE_OVERNIGHTS = ImmutableSet.of("GSP");

  private static final Set<String> FAVORITE_TURNS = ImmutableSet.of("RAP", "SAT", "TUL",
      "OKC", "DSM");

  private final Period favoriteOvernightPeriod;
  private final int numFavoriteOvernights;
  private final int startTimePoints;
  private final int endTimePoints;
  private final boolean hasEquipmentTwoHundredSegments;
  private final int numLegs;
  private final int points;
  private final List<String> scoreExplanation = new ArrayList<>();
  
  public TripScore(Trip trip) {
    int goodPoints = 0;
    int badPoints = 0;
    
    Period favoriteOvernightPeriod = Period.ZERO;
    int numFavoriteOvernights = 0;
    
    int numLegs = 0;
    for (Section section : trip.sections) {
      if (section.hasLayoverAirportCode()
          && FAVORITE_OVERNIGHTS.contains(section.getLayoverAirportCode())) {
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
    for (Section section : trip.sections) {
      for (String airportCode : section.getAllTurnAirports()) {
        if (FAVORITE_TURNS.contains(airportCode)) {
          goodPoints++;
          scoreExplanation.add("+1 for a turn to " + airportCode);
        }
      }
    }

    // numLegs
    for (int i = 0; i < trip.sections.size(); i++) {
      Section section = trip.sections.get(i);
      boolean isFirstOrLast = i == 0 || i == trip.sections.size() - 1;
      int idealNumLegs = isFirstOrLast ? 3 : 2;
      int excessiveLegs = section.getNumLegs() - idealNumLegs;
      if (excessiveLegs > 0) {
        badPoints += excessiveLegs;
        scoreExplanation.add("-" + excessiveLegs + " for legs");
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
    
    for (Section section : trip.sections) {
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
    
    // TODO don't reward trips which span the weekend
    if (isPreferredStartDayOfWeek(trip.getFirstSection().date)) {
      goodPoints += 1;
      scoreExplanation.add("+1 for weekday start");
    } else {
      badPoints += 1;
      scoreExplanation.add("-1 for weekend start");
    }

    this.points = goodPoints - badPoints;
    scoreExplanation.add("Final score: " + points);
  }
  
  private boolean isPreferredStartDayOfWeek(LocalDate date) {
    switch (date.getDayOfWeek()) {
    case DateTimeConstants.SUNDAY: return false;
    case DateTimeConstants.MONDAY: return true;
    case DateTimeConstants.TUESDAY: return true;
    case DateTimeConstants.WEDNESDAY: return true;
      case DateTimeConstants.THURSDAY:
        return false;
      case DateTimeConstants.FRIDAY:
        return false;
    case DateTimeConstants.SATURDAY: return false;
    default: throw new IllegalStateException("What day of week is this? " + date);
    }
  }
  
  public int getNumGspOvernights() {
    return numFavoriteOvernights;
  }

  public Period getGspOvernightPeriod() {
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
