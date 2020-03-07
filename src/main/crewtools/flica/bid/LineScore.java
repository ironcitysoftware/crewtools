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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableMap;

import crewtools.flica.Proto;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.ThinLine;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.rpc.Proto.ScoreAdjustment;
import crewtools.util.Calendar;
import crewtools.util.Collections;
import crewtools.util.Period;

public class LineScore {
  private final Logger logger = Logger.getLogger(LineScore.class.getName());

  private final ThinLine line;
  private final Map<PairingKey, Trip> trips;
  private final BidConfig bidConfig;
  private final Period favoriteOvernightCredit;
  private final Period favoriteOvernightPeriod;
  private final Period nonOverlappingCarryInCredit;
  private final Period NHighestCreditsPlusCarryIn;
  private final int numFavoriteOvernights;
  private final Map<Trip, Period> minimumTripsThatMeetMinCredit;
  private final Period minimumTripFavoriteOvernightPeriod;
  private final int scoreAdjustmentPoints;
  private final int numEquipmentTwoHundredSegments;
  private final int numWeekendWorkdays;
  private final boolean hasReserve;
  private Map<Trip, Period> creditsInMonthMap = new HashMap<>();
  private Map<Integer, Integer> tripLengthToCount = new HashMap<>();

  public LineScore(ThinLine line,
      Map<PairingKey, Trip> trips,
      BidConfig bidConfig,
      Map<LocalDate, Period> carryInCredit,
      Set<LocalDate> vacationDays) {
    this.line = line;
    this.trips = trips;
    this.bidConfig = bidConfig;

    Period favoriteOvernightCredit = Period.ZERO;
    Period allCredit = Period.ZERO;
    Period favoriteOvernightPeriod = Period.ZERO;
    int numFavoriteOvernights = 0;
    int numWeekendWorkdays = 0;

    Set<LocalDate> daysObligated = new HashSet<>(); // Carry-ins or trips
    for (Trip trip : trips.values()) {
      // credit of this trip, handling overlapping CI credit, vacation credit.
      Period creditInMonth = trip.getCreditInMonth(
          vacationDays,
          YearMonth.parse(bidConfig.getYearMonth()),
          carryInCredit);
      creditsInMonthMap.put(trip, creditInMonth);
      allCredit = allCredit.plus(creditInMonth);

      boolean hasFavoriteOvernight = false;
      for (Section section : trip.getSections()) {
        daysObligated.add(section.getDepartureDate());
        if (vacationDays.contains(section.date)) {
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
        if (section.getDepartureDate().getDayOfWeek() == DateTimeConstants.SATURDAY
            || section.getDepartureDate().getDayOfWeek() == DateTimeConstants.SUNDAY) {
          numWeekendWorkdays++;
        }
      }
      if (hasFavoriteOvernight) {
        favoriteOvernightCredit = favoriteOvernightCredit.plus(creditInMonth);
      }
    }
    for (LocalDate date : line.getCarryInDays()) {
      daysObligated.add(date);
    }

    // Include non-overlapping CI credit in allCredit.
    Period nonOverlappingCarryInCredit = Period.ZERO;
    for (LocalDate date : carryInCredit.keySet()) {
      if (!daysObligated.contains(date)) {
        nonOverlappingCarryInCredit = nonOverlappingCarryInCredit
            .plus(carryInCredit.get(date));
      }
    }

    this.tripLengthToCount = computeHistogram(daysObligated);
    this.nonOverlappingCarryInCredit = nonOverlappingCarryInCredit;
    this.favoriteOvernightCredit = favoriteOvernightCredit;
    this.favoriteOvernightPeriod = favoriteOvernightPeriod;
    this.numFavoriteOvernights = numFavoriteOvernights;
    this.minimumTripsThatMeetMinCredit = evaluateMinCredit(creditsInMonthMap);
    this.NHighestCreditsPlusCarryIn = evaluateNCredit(creditsInMonthMap);
    this.numWeekendWorkdays = numWeekendWorkdays;

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

    int numEquipmentTwoHundredSegments = 0;
    for (Trip trip : trips.values()) {
      for (Section section : trip.getSections()) {
        if (section.isEquipmentTwoHundred()) {
          numEquipmentTwoHundredSegments++;
        }
      }
    }
    this.numEquipmentTwoHundredSegments = numEquipmentTwoHundredSegments;
    this.scoreAdjustmentPoints = getScoreAdjustments(daysObligated);
    this.hasReserve = line.hasReserve();
  }

  public int getScore() {
    int points = scoreAdjustmentPoints;
    for (Trip trip : getMinimumTrips()) {
      points += new TripScore(trip, bidConfig).getPoints();
    }
    return points;
  }

  public List<Trip> getMinimumTrips() {
    if (hasMinimumTripsThatMeetMinCredit()) {
      return new ArrayList<>(getMinimumTripsThatMeetMinCredit().keySet());
    } else {
      return new ArrayList<>(getAllTrips());
    }
  }

  private Map<Integer, Integer> computeHistogram(Set<LocalDate> daysObligated) {
    Calendar calendar = new Calendar(YearMonth.parse(bidConfig.getYearMonth()));
    Map<Integer, Integer> result = new HashMap<>();
    int currentTripLength = 0;
    for (LocalDate date : calendar.getDatesInPeriod()) {
      if (daysObligated.contains(date)) {
        currentTripLength++;
      } else if (currentTripLength > 0) {
        int currentValue = result.containsKey(currentTripLength)
            ? result.get(currentTripLength)
            : 0;
        result.put(currentTripLength, currentValue + 1);
        currentTripLength = 0;
      }
    }
    return ImmutableMap.copyOf(result);
  }

  private int getScoreAdjustments(Set<LocalDate> daysObligated) {
    for (ScoreAdjustment scoreAdjustment : bidConfig.getScoreAdjustmentList()) {
      for (String localDateString : scoreAdjustment.getSoftDayOffList()) {
        if (daysObligated.contains(LocalDate.parse(localDateString))) {
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
    requiredCredit = requiredCredit.minus(nonOverlappingCarryInCredit);
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

  public Period evaluateNCredit(Map<Trip, Period> creditsInMonth) {
    Map<Trip, Period> largestToSmallestCredit = Collections
        .sortByValueDescending(creditsInMonth);
    Period result = Period.ZERO.plus(nonOverlappingCarryInCredit);
    int num = 0;
    for (Map.Entry<Trip, Period> entry : largestToSmallestCredit.entrySet()) {
      if (entry.getKey().isTwoHundred()) {
        continue;
      }
      result = result.plus(entry.getValue());
      if (num++ == bidConfig.getMinimumNumberOfTrips()) {
        break;
      }
    }
    return result;
  }

  /** Returns true if we want to consider this line for our bid. */
  public boolean isDesirableLine() {
    boolean hasAnyFavoriteOvernights = false;
    for (Trip trip : getAllTrips()) {
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
      Set<LocalDate> requiredDaysOff = bidConfig.getRequiredDayOffList()
          .stream().map(s -> LocalDate.parse(s)).collect(Collectors.toSet());
      if (trip.spansDaysOfMonth(requiredDaysOff)) {
        // A trip on this line spans a desired day off. Disqualify the line.
        return false;
      }
      if (bidConfig.getEnableCarryInsRespectRequiredDaysOff()) {
        for (LocalDate carryInDay : line.getCarryInDays()) {
          if (requiredDaysOff.contains(carryInDay)) {
            return false;
          }
        }
      }
    }

    if (bidConfig.getEnableMonthlyPreferredOvernightsAreDesirable()
        && hasAnyFavoriteOvernights) {
      return true;
    }

    return hasMinimumTripsThatMeetMinCredit();
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

  public Collection<Trip> getAllTrips() {
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

  public int getScoreAdjustmentPoints() {
    return scoreAdjustmentPoints;
  }

  public int getNumEquipmentTwoHundredSegments() {
    return numEquipmentTwoHundredSegments;
  }

  public boolean hasReserve() {
    return hasReserve;
  }

  public Period getNHighestCreditsPlusCarryIn() {
    return NHighestCreditsPlusCarryIn;
  }

  public Period getAdjustedCredit(Trip trip) {
    return creditsInMonthMap.get(trip);
  }

  public Map<Integer, Integer> getTripLengthToCount() {
    return tripLengthToCount;
  }

  public int getNumWeekendWorkdays() {
    return numWeekendWorkdays;
  }
}
