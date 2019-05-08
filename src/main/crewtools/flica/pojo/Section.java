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

package crewtools.flica.pojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto;
import crewtools.flica.Proto.Equipment;
import crewtools.flica.Proto.LegType;
import crewtools.util.Period;

public class Section implements Comparable<Section> {
  private final Logger logger = Logger.getLogger(Section.class.getName());

  public Section(Proto.Section protoSection, LocalDate date,
      Period block, Period credit, Period duty,
      DateTime startDuty, DateTime endDuty) {
    this.protoSection = protoSection;
    this.date = date;
    this.block = block;
    this.credit = credit;
    this.duty = duty;
    this.startDuty = startDuty;
    this.endDuty = endDuty;
    this.legs = new ArrayList<>();
    int i = 0;
    for (Proto.Leg leg : protoSection.getLegList()) {
      if (!leg.hasLegType()) {
        legs.add(new Leg(leg, startDuty, i++));
      }
    }
  }

  public Section copyWithDateOffset(int daysBetween) {
    // TODO protoSection change?
    return new Section(protoSection, date.plusDays(daysBetween), block, credit,
        duty,
        startDuty.plusDays(daysBetween),
        endDuty.plusDays(daysBetween));
  }

  private Proto.Section protoSection;
  public LocalDate date;
  public Period block;
  public Period credit;
  public Period duty;
  private DateTime startDuty;
  private DateTime endDuty;
  private List<Leg> legs;

  // TODO: define this list.
  private static final Set<LegType> UNDROPPABLE_LEG_TYPES = ImmutableSet.of(
      LegType.COMPANY_BUSINESS,
      LegType.GROUND_SCHOOL,
      LegType.PCR);

  public boolean isDroppable() {
    for (Proto.Leg leg : protoSection.getLegList()) {
      if (UNDROPPABLE_LEG_TYPES.contains(leg.getLegType())) {
        return false;
      }
    }
    return true;
  }

  public Set<String> getAllTurnAirports() {
    Set<String> airports = new HashSet<>();
    for (Proto.Leg leg : protoSection.getLegList()) {
      airports.add(leg.getArrivalAirportCode());
      airports.add(leg.getDepartureAirportCode());
    }
    return airports;
  }

  public boolean isEquipmentTwoHundred() {
    if (protoSection.getLegCount() == 0) {
      return false;
    }
    for (Proto.Leg leg : protoSection.getLegList()) {
      if (leg.getIsDeadhead()) {
        continue;
      }
      if (!leg.hasEquipment()) {
        logger.fine("Section should have an equipment.");
        return false;
      }
      return leg.getEquipment().equals(Equipment.RJ2);
    }
    logger.warning("Ambiguius equipment");
    return false;
  }

  public LocalDate getShowDate() {
    return startDuty.toLocalDate();
  }

  public LocalDate getDepartureDate() {
    return date;
  }

  public DateTime getStart() {
    return startDuty;
  }

  public DateTime getEnd() {
    return endDuty;
  }

  public boolean hasLayoverAirportCode() {
    return protoSection.hasLayoverAirportCode();
  }

  public String getLayoverAirportCode() {
    return protoSection.getLayoverAirportCode();
  }

  public Period getLayoverDuration() {
    return Period.fromText(protoSection.getLayoverDuration());
  }

  /** Returns the number of actual flying legs. */
  public int getNumLegs() {
    return legs.size();
  }

  /** Returns the actual flying legs. */
  public List<Leg> getLegs() {
    return legs;
  }

  @Override
  public boolean equals(Object that) {
    if (that == null) { return false; }
    if (!(that instanceof Section)) { return false; }
    // TODO
    return ((Section) that).date.equals(date);
  }

  @Override
  public int hashCode() {
    return date.hashCode();
  }

  @Override
  public int compareTo(Section that) {
    return date.compareTo(that.date);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("date", date)
        .add("block", block)
        .add("credit", credit)
        .add("duty", duty)
        .add("startDuty", startDuty)
        .add("endDuty", endDuty)
        .toString();
  }
}
