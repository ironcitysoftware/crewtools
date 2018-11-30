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

import org.joda.time.LocalDate;

import com.google.common.collect.ImmutableList;

import crewtools.flica.Proto;
import crewtools.util.Period;

/**
 * A pairing is a set of flights that may repeat on different days.
 *
 * TODO - is there a need for a common type between Pairing and Trip?
 */
public class Pairing {
  private final Logger logger = Logger.getLogger(Pairing.class.getName());

  private List<Section> sections;
  private Period block;
  private Period credit;
  private Period tafb;
  private Period duty;
  private Proto.Trip proto;
  private Set<LocalDate> departureDates;

  public Pairing(List<Section> sections, Period block, Period credit,
      Period tafb, Period duty,
      Set<LocalDate> departureDates,
      Proto.Trip proto) {
    this.sections = sections;
    this.block = block;
    this.credit = credit;
    this.tafb = tafb;
    this.duty = duty;
    this.proto = proto;
    this.departureDates = departureDates;
  }

  // This is the first date of departure, not the date of the first show time.
  Section getFirstSection() {
    return sections.get(0);
  }

  public List<Trip> getTrips() {
    if (!proto.hasOperates() || proto.getOperates().contains("Only")) {
      return ImmutableList.of(getTrip(getFirstSection().getShowDate()));
    }

    int year = getFirstSection().getShowDate().getYear();
    OperationDateExpander expander = new OperationDateExpander(
        year, proto.getOperates(), proto.getDayOfWeekList(), proto.getOperatesExcept());
    ImmutableList.Builder<Trip> result = ImmutableList.builder();

    for (LocalDate date : expander.getDates()) {
      result.add(getTrip(date));
    }
    return result.build();
  }

  Trip getTrip(LocalDate date) {
    int daysBetween = date.getDayOfMonth()
        - getFirstSection().getDepartureDate().getDayOfMonth();
    List<Section> newSections = new ArrayList<>();
    for (Section section : sections) {
      newSections.add(section.copyWithDateOffset(daysBetween));
    }
    Proto.Trip.Builder newProto = proto.toBuilder();

    Set<LocalDate> newDepartureDates = new HashSet<>();
    for (LocalDate departureDate : departureDates) {
      newDepartureDates.add(departureDate.plusDays(daysBetween));
    }

    return new Trip(newSections, block, credit, tafb, duty, newDepartureDates,
        newProto.build());
  }
}
