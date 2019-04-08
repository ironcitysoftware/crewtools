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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableMap;

import crewtools.flica.Proto;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.ThinLine;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.rpc.Proto.ScoreAdjustment;
import crewtools.util.Collections;
import crewtools.util.Period;

public class LineScore {
  private final Logger logger = Logger.getLogger(LineScore.class.getName());

  private final ThinLine line;
  private final Map<PairingKey, Trip> trips;
  private final BidConfig bidConfig;
  private final Period favoriteOvernightCredit;
  private final Period allCredit;
  private final Period favoriteOvernightPeriod;
  private final int numFavoriteOvernights;
  private final Map<Trip, Period> minimumTripsThatMeetMinCredit;
  private final Period minimumTripFavoriteOvernightPeriod;
  private final int startTimePoints;
  private final int endTimePoints;
  private final int scoreAdjustmentPoints;
  private final boolean hasEquipmentTwoHundredSegments;

  public LineScore(ThinLine line,
      Map<PairingKey, Trip> trips, BidConfig bidConfig) {
    this.line = line;
    this.trips = trips;
    this.bidConfig = bidConfig;

    Period favoriteOvernightCredit = Period.ZERO;
    Period allCredit = Period.ZERO;
    Period favoriteOvernightPeriod = Period.ZERO;
    int numFavoriteOvernights = 0;

    Map<Trip, Period> creditsInMonthMap = new HashMap<>();

    Set<Integer> daysObligated = new HashSet<>();  // Carry-ins or trips
    for (Trip trip : trips.values()) {
      Period creditInMonth = trip.getCreditInMonth(
          bidConfig.getVacationDateList(), YearMonth.parse(bidConfig.getYearMonth()));
      creditsInMonthMap.put(trip, creditInMonth);
      allCredit = allCredit.plus(creditInMonth);

      boolean hasFavoriteOvernight = false;
      for (Section section : trip.getSections()) {
        daysObligated.add(section.getDepartureDate().getDayOfMonth());
        if (bidConfig.getVacationDateList().contains(section.date.getDayOfMonth())) {
          // This day will be dropped as it falls on vacation.
          continue;
        }
        if (bidConfig.getFavoriteOvernightCount() > 0
            && section.hasLayoverAirportCode()
            && bidConfig.getFavoriteOvernightList().contains(
                section.getLayoverAirportCode())) {
          hasFavoriteOvernight = true;
          favoriteOvernightPeriod = favoriteOvernightPeriod
              .plus(section.getLayoverDuration());
          numFavoriteOvernights++;
        }
      }
      if (hasFavoriteOvernight) {
        favoriteOvernightCredit = favoriteOvernightCredit.plus(creditInMonth);
      }
    }
    for (LocalDate date : line.getCarryInDays()) {
      daysObligated.add(date.getDayOfMonth());
    }

    this.favoriteOvernightCredit = favoriteOvernightCredit;
    this.allCredit = allCredit;
    this.favoriteOvernightPeriod = favoriteOvernightPeriod;
    this.numFavoriteOvernights = numFavoriteOvernights;
    this.minimumTripsThatMeetMinCredit = evaluateMinCredit(creditsInMonthMap);

    Period minimumTripFavoriteOvernightPeriod = Period.ZERO;
    for (Trip trip : minimumTripsThatMeetMinCredit.keySet()) {
      for (Section section : trip.getSections()) {
        if (bidConfig.getFavoriteOvernightCount() > 0
            && section.hasLayoverAirportCode()
            && bidConfig.getFavoriteOvernightList().contains(
                section.getLayoverAirportCode())) {
          minimumTripFavoriteOvernightPeriod = minimumTripFavoriteOvernightPeriod
              .plus(section.getLayoverDuration());
        }
      }
    }
    this.minimumTripFavoriteOvernightPeriod = minimumTripFavoriteOvernightPeriod;

    // more points are better.
    int startTimePoints = 0;
    int endTimePoints = 0;
    boolean hasEquipmentTwoHundredSegments = false;

    for (Trip trip : minimumTripsThatMeetMinCredit.keySet()) {
      LocalTime reportTime = trip.getDutyStart().toLocalTime();
      if (reportTime.getHourOfDay() > 9 && reportTime.getHourOfDay() <= 20) {
        startTimePoints++;
      }

      LocalTime endTime = trip.getDutyEnd().toLocalTime();
      if (endTime.getHourOfDay() <= 18) {
        endTimePoints++;
      }

      for (Section section : trip.getSections()) {
        if (section.isEquipmentTwoHundred()) {
          hasEquipmentTwoHundredSegments = true;
        }
      }
    }
    this.startTimePoints = startTimePoints;
    this.endTimePoints = endTimePoints;
    this.hasEquipmentTwoHundredSegments = hasEquipmentTwoHundredSegments;
    this.scoreAdjustmentPoints = getScoreAdjustments(daysObligated);
  }

  private int getScoreAdjustments(Set<Integer> daysObligated) {
    for (ScoreAdjustment scoreAdjustment : bidConfig.getScoreAdjustmentList()) {
      for (int dayOfMonth : scoreAdjustment.getSoftDayOffList()) {
        if (daysObligated.contains(dayOfMonth)) {
          return scoreAdjustment.getScoreAdjustment();
        }
      }
    }
    return 0;
  }

  /** Are there any N trips that together meet minimum credit? */
  private Map<Trip, Period> evaluateMinCredit(Map<Trip, Period> creditsInMonth) {
    Map<Trip, Period> largestToSmallestCredit = Collections
        .sortByValueDescending(creditsInMonth);
    ImmutableMap.Builder<Trip, Period> result = ImmutableMap.builder();
    Period requiredCredit = Period.hours(bidConfig.getMinimumCreditHours());
    for (Map.Entry<Trip, Period> entry : largestToSmallestCredit.entrySet()) {
      result.put(entry.getKey(), entry.getValue());
      if (requiredCredit.compareTo(entry.getValue()) <= 0) {
        // we're done
        return result.build();
      }
      if (result.build().size() == bidConfig.getMinimumNumberOfTrips()) {
        break;
      }
      requiredCredit = requiredCredit.minus(entry.getValue());
    }
    return ImmutableMap.of();
  }

  /** Returns true if we want to consider this line for our bid. */
  public boolean isDesirableLine() {
    boolean hasAnyFavoriteOvernights = false;
    for (Trip trip : getTrips()) {
      if (!bidConfig.getEnableAllTripsRespectRequiredDaysOff()) {
        if (hasMinimumTripsThatMeetMinCredit()) {
          if (!getMinimumTripsThatMeetMinCredit().containsKey(trip)) {
            // We're planning on dropping this trip in this line in the SAP anyway,
            // because it is not one of the "magic N" (and there are a magic
            // N in this line).
            // We don't care if it spans a day off.
            continue;
          }
        } else {
          if (bidConfig.getFavoriteOvernightCount() == 0) {
            // We're planning on keeping this trip. "All overnights are favorites".
            hasAnyFavoriteOvernights = true;
          } else {
            int numFavoriteLayoverOvernights = countFavoriteOvernights(
                bidConfig.getFavoriteOvernightList(), trip);
            if (numFavoriteLayoverOvernights == 0) {
              // We're planning on dropping this trip in this line in the SAP anyway,
              // because it isn't a favorite overnight.
              continue;
            } else {
              // We're planning on keeping this trip.
              hasAnyFavoriteOvernights = true;
            }
          }
        }
      }
      if (trip.spansDaysOfMonth(bidConfig.getRequiredDayOffList())) {
        // A trip on this line spans a desired day off. Disqualify the line.
        return false;
      }
      if (bidConfig.getEnableCarryInsRespectRequiredDaysOff()) {
        for (LocalDate carryInDay : line.getCarryInDays()) {
          if (bidConfig.getRequiredDayOffList().contains(carryInDay.getDayOfMonth())) {
            return false;
          }
        }
      }
    }

    return hasMinimumTripsThatMeetMinCredit() || hasAnyFavoriteOvernights;
  }

  private int countFavoriteOvernights(List<String> favoriteOvernights,
      Trip trip) {
    int numFavoriteOvernights = 0;
    for (Proto.Section section : trip.proto.getSectionList()) {
      if (favoriteOvernights.contains(section.getLayoverAirportCode())) {
        numFavoriteOvernights++;
      }
    }
    return numFavoriteOvernights;
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

  public Period getFavoriteOvernightCredit() {
    return favoriteOvernightCredit;
  }

  public int getNumFavoriteOvernights() {
    return numFavoriteOvernights;
  }

  public Period getLineCredit() {
    return allCredit;
  }

  public Period getFavoriteOvernightPeriod() {
    return favoriteOvernightPeriod;
  }

  public Map<Trip, Period> getMinimumTripsThatMeetMinCredit() {
    return minimumTripsThatMeetMinCredit;
  }

  public boolean hasMinimumTripsThatMeetMinCredit() {
    return !minimumTripsThatMeetMinCredit.isEmpty();
  }

  public Period getMinimumTripFavoriteOvernightPeriod() {
    return minimumTripFavoriteOvernightPeriod;
  }

  public int getStartTimePoints() {
    return startTimePoints;
  }

  public int getEndTimePoints() {
    return endTimePoints;
  }

  public int getScoreAdjustmentPoints() {
    return scoreAdjustmentPoints;
  }

  public boolean hasEquipmentTwoHundredSegments() {
    return hasEquipmentTwoHundredSegments;
  }
}
