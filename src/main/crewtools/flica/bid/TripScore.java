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

import java.util.logging.Logger;

import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.Trip;
import crewtools.util.Period;

public class TripScore implements Comparable<TripScore> {
  private final Logger logger = Logger.getLogger(TripScore.class.getName());

  private final Period gspOvernightPeriod;
  private final int numGspOvernights;
  private final int startTimePoints;
  private final int endTimePoints;
  private final boolean hasEquipmentTwoHundredSegments;
  private final int numLegs;
  
  private final int points;
  
  public TripScore(Trip trip) {
    int goodPoints = 0;
    int badPoints = 0;
    
    Period gspOvernightPeriod = Period.ZERO;
    int numGspOvernights = 0;
    
    int numLegs = 0;
    for (Section section : trip.sections) {
      if (section.hasLayoverAirportCode()
          && section.getLayoverAirportCode().equals("GSP")) {
        gspOvernightPeriod = gspOvernightPeriod.plus(section.getLayoverDuration());
        numGspOvernights++;
      }
      numLegs += section.getNumLegs();
    }
    
    this.gspOvernightPeriod = gspOvernightPeriod;
    this.numGspOvernights = numGspOvernights;
    this.numLegs = numLegs;
    
    logger.fine("Begin point diagnostic for " + trip.getPairingName() + " ----------");
    
    goodPoints += 2 * numGspOvernights;
    if (numGspOvernights > 0) {
      logger.fine("+2*" + numGspOvernights + " for GSP overnights");
    }
    
    goodPoints += (gspOvernightPeriod.getHours() / 6);
    if (gspOvernightPeriod.getHours() > 0) {
      logger.fine("+" + gspOvernightPeriod.getHours() + "/6 for GSP overnights hours");
    }
    badPoints += numLegs;
    logger.fine("-" + numLegs + " for legs");
    if (numLegs > 3) {
      logger.fine("-1 (surcharge) for > 3 legs");
      badPoints++;
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
        logger.fine("+1 for good start time");
      } else {
        badPoints++;
        logger.fine("-1 for bad start time");
      }
    }
      
    Section lastSection = trip.getLastSection();
    if (lastSection != null) {
      LocalTime endTime = lastSection.getEnd().toLocalTime();
      if (endTime.getHourOfDay() <= 18) {
        endTimePoints++;
        goodPoints++;        
        logger.fine("+1 for good end time");
      } else {
        badPoints++;
        logger.fine("-1 for bad end time");
      }
    }
    
    int numTwoHundredSegments = 0;
    for (Section section : trip.sections) {
      if (section.isEquipmentTwoHundred()) {
        hasEquipmentTwoHundredSegments = true;
        numTwoHundredSegments++;
      }
    }
    if (numTwoHundredSegments > 0) {
      logger.fine("-" + numTwoHundredSegments + " for 200 segments");
    }
    badPoints += numTwoHundredSegments;

    this.startTimePoints = startTimePoints;
    this.endTimePoints = endTimePoints;
    this.hasEquipmentTwoHundredSegments = hasEquipmentTwoHundredSegments;
    
    if (isWeekday(trip.getFirstSection().date)) {
      goodPoints += 2;
      logger.fine("+2 for weekday start");
    } else {
      badPoints += 2;
      logger.fine("-2 for weekend start");
    }

    if (isWeekday(trip.getLastSection().date)) {
      goodPoints += 2;
      logger.fine("+2 for weekday end");
    } else {
      badPoints += 2;
      logger.fine("-2 for weekend end");
    }

    this.points = goodPoints - badPoints;
    logger.fine("Total points: " + points);
    logger.fine("end point diagnostic for " + trip.getPairingName() + " ----------");
  }
  
  private boolean isWeekday(LocalDate date) {
    switch (date.getDayOfWeek()) {
    case DateTimeConstants.SUNDAY: return false;
    case DateTimeConstants.MONDAY: return true;
    case DateTimeConstants.TUESDAY: return true;
    case DateTimeConstants.WEDNESDAY: return true;
    case DateTimeConstants.THURSDAY: return true;
    case DateTimeConstants.FRIDAY: return true;
    case DateTimeConstants.SATURDAY: return false;
    default: throw new IllegalStateException("What day of week is this? " + date);
    }
  }
  
  public int getNumGspOvernights() {
    return numGspOvernights;
  }

  public Period getGspOvernightPeriod() {
    return gspOvernightPeriod;
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

  @Override
  public int compareTo(TripScore that) {
    return new Integer(getPoints()).compareTo(that.getPoints());
  }
}
