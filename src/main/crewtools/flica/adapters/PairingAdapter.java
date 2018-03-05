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

package crewtools.flica.adapters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto;
import crewtools.flica.Proto.LegType;
import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.pojo.Pairing;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.Trip;
import crewtools.util.Period;

// converts a proto schedule to a pojo schedule
// "TripAdapter" ?
public class PairingAdapter {
  private final Logger logger = Logger.getLogger(PairingAdapter.class.getName());

  private static final Period CONTRACTUAL_TRAINING_LEG_BLOCK_PERIOD = Period.hours(3).plus(Period.minutes(45));

  private static class TripOrPairingData {
    private final List<Section> sections;
    private final Period block;
    private final Period credit;
    private final Period tafb;
    private final Set<LocalDate> departureDates;
    private final Proto.Trip proto;

    TripOrPairingData(List<Section> sections, Period block, Period credit, Period tafb,
        Set<LocalDate> departureDates,
        Proto.Trip proto) {
      this.sections = sections;
      this.block = block;
      this.credit = credit;
      this.tafb = tafb;
      this.departureDates = departureDates;
      this.proto = proto;
    }

    private Trip toTrip() {
      return new Trip(sections, block, credit, tafb, departureDates, proto);
    }
    
    Pairing toPairing() {
      return new Pairing(sections, block, credit, tafb, departureDates, proto);
    }
  }
  
  public Trip adaptTrip(Proto.Trip protoTrip) {
    return adaptInternal(protoTrip).toTrip();
  }

  public Pairing adaptPairing(Proto.Trip protoTrip) {
    return adaptInternal(protoTrip).toPairing();
  }

  class Stats {
    Period block = Period.ZERO;
    Period credit = Period.ZERO;
    Period deadhead = Period.ZERO;
    Period flightDuty = Period.ZERO;
    Period duty = Period.ZERO;

    void add(Stats that) {
      this.block = this.block.plus(that.block);
      this.credit = this.credit.plus(that.credit);
      this.deadhead = this.deadhead.plus(that.deadhead);
      this.flightDuty = this.flightDuty.plus(that.flightDuty);
      this.duty = this.duty.plus(that.duty);
    }

    void addBlock(Period period) {
      this.block = this.block.plus(period);
    }

    void addDeadhead(Period period) {
      this.deadhead = this.deadhead.plus(period);
    }

    void addCredit(Period period) {
      this.credit = this.credit.plus(period);
    }
  }

  private ProtoTimeHelper timeHelper = new ProtoTimeHelper();

  private TripOrPairingData adaptInternal(Proto.Trip protoTrip) {
    List<Section> sections = new ArrayList<>();
    Stats tripStats = new Stats();

    // Start date is the date of departure.  The show time can be the day prior.
    LocalDate legDate = LocalDate.parse(protoTrip.getStartDate());
    
    for (int sectionIndex = 0; sectionIndex < protoTrip.getSectionCount(); ++sectionIndex) {
      Proto.Section protoSection = protoTrip.getSection(sectionIndex);
      Stats sectionStats = new Stats();
      List<Proto.Leg> protoLegs = filterLegs(protoSection, LegType.TEST);
      for (int i = 0; i < protoLegs.size(); ++i) {
        Proto.Leg protoLeg = protoLegs.get(i);
        Period legBlock = Period.fromText(protoLeg.getBlockDuration());
        if (!protoLeg.getLegType().equals(LegType.TRAINING_LEG)) {
          sectionStats.addCredit(legBlock);
          // If this is a non-deadhead taxi leg (will likely be at the end of the day),
          // don't add the block.
          // TODO is this legal?
          if (protoLeg.getIsDeadhead()) {
            sectionStats.addDeadhead(legBlock);
          } else if (!protoLeg.getLegType().equals(LegType.TAXI)) {
            sectionStats.addBlock(legBlock);
          }
        }
        verifyBlockTime(protoLeg, legDate, legBlock, getIsSectionAllDeadhead(protoSection));
      }

      sectionStats.flightDuty = calculateFlightDuty(protoSection, legDate);

      // Deadheads are paid the greater of the scheduled or actual duration of the flight.
      // TODO verify: credit can always be larger than block.
      // No easy way to get scheduled duration?
      Period creditDuration = Period.fromText(protoSection.getCreditDuration());
      if (creditDuration.compareTo(sectionStats.credit) >= 0) {
        sectionStats.credit = creditDuration;
      }

      // local duty start time isn't bounded.
      // If the plane leaves in < 45 minutes, you don't get paid extra.

      // verify local duty end time
      DateTime startDuty = null;
      DateTime endDuty = null;
      if (protoSection.getLegCount() > 0) {
        Proto.Leg lastLeg = protoSection.getLeg(protoSection.getLegCount() - 1);
        LocalTime calculatedLocalDutyEndTime = timeHelper.getArrivalLocalTime(lastLeg);
        // Ignore a final TAXi leg unless it is marked DH.
        // Sometimes they are, sometimes not.
        // TODO is this legal?
        if (!lastLeg.getLegType().equals(LegType.TAXI) || lastLeg.getIsDeadhead()) {
          calculatedLocalDutyEndTime =
              calculatedLocalDutyEndTime.plusMinutes(MINUTES_OF_DUTY_AFTER_LAST_FLIGHT);
        }
        LocalTime protoLocalDutyEndTime = timeHelper.getLocalDutyEndTime(protoSection);
        if (!calculatedLocalDutyEndTime.equals(protoLocalDutyEndTime)) {
          logger.fine(String.format("Calculated section duty end %s but proto %s",
              calculatedLocalDutyEndTime, protoLocalDutyEndTime));
        }

        // calculate section duty stats
        startDuty = timeHelper.getLocalDutyStartDateTime(protoSection, legDate);
        endDuty = timeHelper.getLocalDutyEndDateTime(protoSection, legDate);
        sectionStats.duty = new Period(startDuty, endDuty);
      }

      // verify section stats
      verifyPeriod("section block", sectionStats.block, Period.fromText(protoSection.getBlockDuration()));
      verifyPeriod("section credit", sectionStats.credit, Period.fromText(protoSection.getCreditDuration()));
      verifyPeriod("section deadhead", sectionStats.deadhead, Period.fromText(protoSection.getDeadheadDuration()));
      verifyPeriod("section flight duty", sectionStats.flightDuty, Period.fromText(protoSection.getFlightDutyDuration()));
      verifyPeriod("section duty", sectionStats.duty, Period.fromText(protoSection.getDutyDuration()));

      tripStats.add(sectionStats);

      sections.add( 
          new Section(protoSection, legDate, sectionStats.block, sectionStats.credit, startDuty, endDuty));

      if (!isNextSectionSameDate(protoTrip, sectionIndex)) {
        legDate = legDate.plusDays(1);
      }
    }
    verifyPeriod("trip block", tripStats.block, Period.fromText(protoTrip.getBlockDuration()));
    verifyPeriod("trip credit", tripStats.credit, Period.fromText(protoTrip.getCreditDuration()));
    verifyPeriod("trip deadhead", tripStats.deadhead, Period.fromText(protoTrip.getDeadheadDuration()));
    verifyPeriod("trip flight duty", tripStats.flightDuty, Period.fromText(protoTrip.getFlightDutyDuration()));
    verifyPeriod("trip duty", tripStats.duty, Period.fromText(protoTrip.getDutyDuration()));

    Period tafb = Period.fromText(protoTrip.getTimeAwayFromBaseDuration());

    Set<LocalDate> dates = getDates(protoTrip, sections);
    
    Period credit = tripStats.credit;
    if (sections.isEmpty()) {
      // Vacation credit.
      credit = Period.fromText(protoTrip.getCreditDuration());
    }
    
    return new TripOrPairingData(sections, tripStats.block, credit, tafb, dates, protoTrip);
  }

  boolean isNextSectionSameDate(Proto.Trip trip, int sectionIndex) {
    if (sectionIndex == trip.getSectionCount() - 1) {
      return true;  // Last section, doesn't really matter.
    }
    if (trip.getSection(sectionIndex).getLegCount() == 0
        || trip.getSection(sectionIndex + 1).getLegCount() == 0) {
      logger.warning("isNextSectionSameDate is indeterminate due to there not being any legs");
      return true;  // maybe, maybe not...
    }
    return trip.getSection(sectionIndex).getLeg(0).getDayOfMonth() ==
        trip.getSection(sectionIndex + 1).getLeg(0).getDayOfMonth();
  }

  void verifyPeriod(String description, Period calculatedValue, Period protoValue) {
    if (!calculatedValue.equals(protoValue)) {
      logger.fine(String.format("%s: calculated %s but proto was %s",
          description, calculatedValue, protoValue));
    }
  }

  void verifyPeriodGreaterThanOrEquals(String description, Period first, Period second) {
    // TODO to be precise we need to convert to a duration
    if (first.compareTo(second) < 0) {
        logger.warning(String.format("%s: expected %s >= %s",
            description, second, first));
    }
  }

  boolean getIsSectionAllDeadhead(Proto.Section protoSection) {
    for (Proto.Leg protoLeg : filterLegs(protoSection, LegType.TEST)) {
      if (!protoLeg.getIsDeadhead()) {
        return false;
      }
    }
    return true;
  }

  boolean isAnyDeadheads(List<Proto.Leg> legs) {
    for (Proto.Leg leg : legs) {
      if (leg.getIsDeadhead()) {
        return true;
      }
    }
    return false;
  }

  public static final int MINUTES_OF_DUTY_AFTER_LAST_FLIGHT = 15;
  public static final int MINUTES_OF_DUTY_BEFORE_FIRST_FLIGHT = 45;

  Period calculateFlightDuty(Proto.Section protoSection, LocalDate legDate) {
    Period flightDuty = Period.ZERO;

    List<Proto.Leg> legs = filterLegs(protoSection, LegType.TEST, LegType.TAXI, LegType.TRAINING_LEG);

    int numRemainingFlightLegs = 0;
    for (Proto.Leg leg : legs) {
      if (!leg.getIsDeadhead()) {
        numRemainingFlightLegs++;
      }
    }

    // It appears that if a flight duty leg appears between two deadhead legs,
    // the first deadhead leg is included in flight duty.  May 23 2017

    // For a day that ends with a DH, the FDP is report time to arrival time
    // prior to deadhead.

    // For a day that begins with a DH, it is included in the FDP.

    for (int i = 0; i < legs.size(); ++i) {
      Proto.Leg leg = legs.get(i);
      if (!leg.getIsDeadhead()) {
        numRemainingFlightLegs--;
      }
      if (!leg.getIsDeadhead() || numRemainingFlightLegs > 0) {
        if (i > 0) {
          // Add in ground time between flight legs.
          Proto.Leg prevLeg = legs.get(i - 1);
          DateTime startGroundTime = timeHelper.getArrivalDateTime(prevLeg, legDate);
          DateTime endGroundTime = timeHelper.getDepartureDateTime(leg, legDate);
          Period groundTime = new Period(startGroundTime, endGroundTime);
          flightDuty = flightDuty.plus(groundTime);
        }
        DateTime startFlightDuty = i == 0
            ? timeHelper.getLocalDutyStartDateTime(protoSection, legDate)
            : timeHelper.getDepartureDateTime(leg, legDate);
        DateTime endFlightDuty = timeHelper.getArrivalDateTime(leg, legDate);
        Period flightDutyPeriod = new Period(startFlightDuty, endFlightDuty);
        flightDuty = flightDuty.plus(flightDutyPeriod);
      }
    }
    Preconditions.checkState(numRemainingFlightLegs == 0);
    return flightDuty;
  }

  private void verifyBlockTime(Proto.Leg protoLeg, LocalDate legDate,
      Period legBlock, boolean isSectionAllDeadhead) {
    if (protoLeg.getLegType().equals(LegType.HOT_RESERVE)) {
      verifyPeriod("Hot Reserve block", Period.ZERO, legBlock);
      return;
    }
    DateTime departureTime = timeHelper.getDepartureDateTime(protoLeg, legDate);
    DateTime arrivalTime = timeHelper.getArrivalDateTime(protoLeg, legDate);
    Period calculatedBlock = new Period(departureTime, arrivalTime);
    // Deadhead block time does not propagate to section and trip totals.
    // If a day is exclusively deadheads, leg block will be >= dep - arr.
    // Otherwise, a deadhead leg block will be (dep - arr) / 2.
    if (!protoLeg.getIsDeadhead()) {
      // Training legs are worth 3.75 hours
      if (protoLeg.getLegType().equals(LegType.TRAINING_LEG)) {
        verifyPeriod("training leg block", legBlock, CONTRACTUAL_TRAINING_LEG_BLOCK_PERIOD);
      } else if (!protoLeg.getLegType().equals(LegType.STANDBY_2)) {
        verifyPeriod("leg block", calculatedBlock, legBlock);
      }
    } else {
      if (isSectionAllDeadhead) {
        // we should be payed at least calculated, but possbly more.
        verifyPeriodGreaterThanOrEquals("DH leg block", legBlock, calculatedBlock);
      } else {
        if (protoLeg.getLegType().equals(LegType.TAXI)) {
          // Taxi deadheads are often blocked at 12 minutes but allocated 1 minute.
          verifyPeriodGreaterThanOrEquals("DH taxi leg block", legBlock, calculatedBlock);
        } else {
          verifyPeriod("DH leg half block", calculatedBlock.half(), legBlock);
        }
      }
    }
  }

  List<Proto.Leg> filterLegs(Proto.Section section, LegType...excludedLegTypes) {
    List<Proto.Leg> legs = new ArrayList<>();
    ImmutableSet<LegType> filterSet = ImmutableSet.copyOf(excludedLegTypes);
    for (Proto.Leg leg : section.getLegList()) {
      if (!filterSet.contains(leg.getLegType())) {
        legs.add(leg);
      }
    }
    return legs;
  }
  
  Set<LocalDate> getDates(Proto.Trip protoTrip, List<Section> sections) {
    ImmutableSet.Builder<LocalDate> result = ImmutableSet.builder();
    if (!protoTrip.hasScheduleType()) {
      for (Section section : sections) {
        result.add(section.getDepartureDate());
      }
    } else if (protoTrip.getScheduleType() == ScheduleType.VACATION
        || protoTrip.getScheduleType() == ScheduleType.VACATION_START
        || protoTrip.getScheduleType() == ScheduleType.VACATION_END) {
      LocalDate startDate = LocalDate.parse(protoTrip.getStartDate());
      LocalDate endDate = LocalDate.parse(protoTrip.getEndDate());
      if (startDate.equals(endDate)) {
        // VAS (vacation end) has start = end (inclusive)
        result.add(startDate);
      } else {
        // VAX and VAC are exclusive.
        for (LocalDate i = startDate; i.isBefore(endDate); i = i.plusDays(1)) {
          result.add(i);
        }
      }
    }
    return result.build();
  }
}
