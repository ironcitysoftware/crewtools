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

import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto;
import crewtools.flica.Proto.Equipment;
import crewtools.flica.Proto.Leg;
import crewtools.flica.Proto.LegType;
import crewtools.util.Period;

public class Section implements Comparable<Section> {
  public Section(Proto.Section protoSection, LocalDate date, 
      Period block, Period credit, 
      DateTime startDuty, DateTime endDuty) {
    this.protoSection = protoSection;
    this.date = date;
    this.block = block;
    this.credit = credit;
    this.startDuty = startDuty;
    this.endDuty = endDuty;
  }
  
  public Section copyWithDateOffset(int daysBetween) {
    // TODO protoSection change?
    return new Section(protoSection, date.plusDays(daysBetween), block, credit, startDuty.plusDays(daysBetween),
        endDuty.plusDays(daysBetween));
  }

  private Proto.Section protoSection;
  public LocalDate date;
  public Period block;
  public Period credit;
  private DateTime startDuty;
  private DateTime endDuty;

  // TODO: define this list.
  private static final Set<LegType> UNDROPPABLE_LEG_TYPES = ImmutableSet.of(
      LegType.COMPANY_BUSINESS,
      LegType.GROUND_SCHOOL,
      LegType.PCR);

  public boolean isDroppable() {
    for (Leg leg : protoSection.getLegList()) {
      if (UNDROPPABLE_LEG_TYPES.contains(leg.getLegType())) {
        return false;
      }
    }
    return true;
  }
  
  public boolean isEquipmentTwoHundred() {
    if (protoSection.getLegCount() == 0) {
      return false;
    }
    if (!protoSection.getLeg(0).hasEquipment()) {
      return false;
    }
    return protoSection.getLeg(0).getEquipment().equals(Equipment.RJ2);
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
  
  public int getNumLegs() {
    return protoSection.getLegCount();
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
        .add("startDuty", startDuty)
        .add("endDuty", endDuty)
        .toString();
  }
}
