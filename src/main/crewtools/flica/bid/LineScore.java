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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.ThinLine;
import crewtools.flica.pojo.Trip;
import crewtools.util.Collections;
import crewtools.util.Period;

public class LineScore {
  private final Logger logger = Logger.getLogger(LineScore.class.getName());

  // Possible that only one additional SAP trip is necessary to make 65 hours.
  private static final Period TIER_1_THRESHOLD = Period.hours(44);
  
  // Single-GSP overnight lines with less than this credit are less
  // valuable than double-GSP overnights with less than this credit.
  private static final Period TIER_2_THRESHOLD = Period.hours(22);
  
  private final ThinLine line;
  private final Map<PairingKey, Trip> trips;
  private final Period gspCredit;
  private final Period allCredit;
  private final Period gspOvernightPeriod;
  private final int numGspOvernights;
  private final Map<Trip, Period> threeTripsThatMeetMinCredit;
  private final Period threeTripGspOvernightPeriod;
  private final int startTimePoints;
  private final int endTimePoints;
  private final boolean hasEquipmentTwoHundredSegments;

  private static final Set<Integer> DAYS_OFF = ImmutableSet.of(
     ); //9, 10, 11);

  public LineScore(YearMonth yearMonth, ThinLine line, Map<PairingKey, Trip> trips) {
    this.line = line;
    this.trips = trips;
    
    Period gspCredit = Period.ZERO;
    Period allCredit = Period.ZERO;
    Period gspOvernightPeriod = Period.ZERO;
    int numGspOvernights = 0;
    
    Map<Trip, Period> creditsInMonthMap = new HashMap<>();
    
    for (Trip trip : trips.values()) {
      Period creditInMonth = trip.getCreditInMonth(yearMonth);
      creditsInMonthMap.put(trip, creditInMonth);
      allCredit = allCredit.plus(creditInMonth);
      boolean hasGspOvernight = false;
      for (Section section : trip.sections) {
        if (section.hasLayoverAirportCode()
            && section.getLayoverAirportCode().equals("GSP")) {
          hasGspOvernight = true;
          gspOvernightPeriod = gspOvernightPeriod.plus(section.getLayoverDuration());
          numGspOvernights++;
        }
      }
      if (hasGspOvernight) {
        gspCredit = gspCredit.plus(creditInMonth);
      }
    }
    
    this.gspCredit = gspCredit;
    this.allCredit = allCredit;
    this.gspOvernightPeriod = gspOvernightPeriod;
    this.numGspOvernights = numGspOvernights;
    this.threeTripsThatMeetMinCredit = evaluateMinCredit(creditsInMonthMap);
    
    Period threeTripGspOvernightPeriod = Period.ZERO;
    for (Trip trip : threeTripsThatMeetMinCredit.keySet()) {
      for (Section section : trip.sections) {
        if (section.hasLayoverAirportCode()
            && section.getLayoverAirportCode().equals("GSP")) {
          threeTripGspOvernightPeriod = 
              threeTripGspOvernightPeriod.plus(section.getLayoverDuration());
        }
      }
    }
    this.threeTripGspOvernightPeriod = threeTripGspOvernightPeriod;

    // more points are better.
    int startTimePoints = 0;
    int endTimePoints = 0;
    boolean hasEquipmentTwoHundredSegments = false;
    
    for (Trip trip : threeTripsThatMeetMinCredit.keySet()) {
      Section firstSection = trip.getFirstSection();
      LocalTime reportTime = firstSection.startDuty.toLocalTime();
      if (reportTime.getHourOfDay() > 9 && reportTime.getHourOfDay() <= 20) {
        startTimePoints++;
      }
      
      Section lastSection = trip.getLastSection();
      LocalTime endTime = lastSection.endDuty.toLocalTime();
      if (endTime.getHourOfDay() <= 18) {
        endTimePoints++;
      }
      
      for (Section section : trip.sections) {
        if (section.isEquipmentTwoHundred()) {
          hasEquipmentTwoHundredSegments = true;
        }
      }
    }
    this.startTimePoints = startTimePoints;
    this.endTimePoints = endTimePoints;
    this.hasEquipmentTwoHundredSegments = hasEquipmentTwoHundredSegments;
  }
  
  /** Are there any three trips that together meed minimum credit? */
  private Map<Trip, Period> evaluateMinCredit(Map<Trip, Period> creditsInMonth) {
    Map<Trip, Period> largestToSmallestCredit = Collections.reverseSortByValue(creditsInMonth);
    ImmutableMap.Builder<Trip, Period> result = ImmutableMap.builder();
    Period requiredCredit = Period.hours(65);
    for (Map.Entry<Trip, Period> entry : largestToSmallestCredit.entrySet()) {
      result.put(entry.getKey(), entry.getValue());
      if (requiredCredit.compareTo(entry.getValue()) <= 0) {
        // we're done
        return result.build();
      }
      if (result.build().size() == 3) {
        break;
      }
      requiredCredit = requiredCredit.minus(entry.getValue());
    }
    return ImmutableMap.of();
  }

  /** Returns true if we want to consider this line for our bid. */
  public boolean isDesirableLine() {
    boolean hasAnyGspOvernights = false;
    for (Trip trip : getTrips()) {
      if (hasThreeTripsThatMeetMinCredit()) {
        if (!getThreeTripsThatMeetMinCredit().containsKey(trip)) {
          // We're planning on dropping this trip in this line in the SAP anyway,
          // because it is not one of the "magic three" (and there are a magic
          // three in this line).
          // We don't care if it spans a day off.
          continue;
        }
      } else {
        int numGspOvernights = countGspOvernights(trip);
        if (numGspOvernights == 0) {
          // We're planning on dropping this trip in this line in the SAP anyway,
          // because it isn't a GSP overnight.
          continue;
        } else {
          // We're planning on keeping this trip.
          // TODO What if it spans days off?
          hasAnyGspOvernights = true;
        }
      }
      if (spansDesiredDaysOff(trip)) {
        return false;
      }
    }

    return hasThreeTripsThatMeetMinCredit() || hasAnyGspOvernights;
  }
  
  private int countGspOvernights(Trip trip) {
    int numGspOvernights = 0;
    for (Proto.Section section : trip.proto.getSectionList()) {
      if (section.getLayoverAirportCode().equals("GSP")) {
        numGspOvernights++;
      }
    }
    return numGspOvernights;
  }

  private boolean spansDesiredDaysOff(Trip trip) {
    for (LocalDate date : trip.getDepartureDates()) {
      if (DAYS_OFF.contains(date.getDayOfMonth())) {
        return true;
      }
    }
    return false;
  }
  
  public Collection<Trip> getTrips() {
    return trips.values();
  }
  
  public ThinLine getThinLine() {
    return line;
  }
  
  public String getLineName() {
    return line.getLineName();
  }
  
  public Period getGspCredit() {
    return gspCredit;
  }
  
  public int getNumGspOvernights() {
    return numGspOvernights;
  }

  public Period getLineCredit() {
    return allCredit;
  }
  
  public Period getGspOvernightPeriod() {
    return gspOvernightPeriod;
  }
  
  public Map<Trip, Period> getThreeTripsThatMeetMinCredit() {
    return threeTripsThatMeetMinCredit;
  }
  
  public boolean hasThreeTripsThatMeetMinCredit() {
    return !threeTripsThatMeetMinCredit.isEmpty();
  }
  
  public Period getThreeTripGspOvernightPeriod() {
    return threeTripGspOvernightPeriod;
  }
  
  public int getStartTimePoints() {
    return startTimePoints;
  }

  public int getEndTimePoints() {
    return endTimePoints;
  }
  
  public boolean hasEquipmentTwoHundredSegments() {
    return hasEquipmentTwoHundredSegments;
  }
  
  public int getTier() {
    if (getGspCredit().compareTo(TIER_1_THRESHOLD) > 0) {
      return 1;
    } else if (getGspCredit().compareTo(TIER_2_THRESHOLD) > 0) {
      return 2;
    } else {
      return 3;
    }
  }
}
