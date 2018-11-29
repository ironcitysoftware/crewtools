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

package crewtools.flica.formatter;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import crewtools.flica.Proto.DayOfWeek;
import crewtools.flica.Proto.Equipment;
import crewtools.flica.Proto.Leg;
import crewtools.flica.Proto.Section;
import crewtools.flica.Proto.Trip;

public class TripFormatter {
  private static final Map<DayOfWeek, String> DAYS_OF_WEEK = ImmutableMap
      .<DayOfWeek, String>builder()
      .put(DayOfWeek.SUNDAY, "SU")
      .put(DayOfWeek.MONDAY, "MO")
      .put(DayOfWeek.TUESDAY, "TU")
      .put(DayOfWeek.WEDNESDAY, "WE")
      .put(DayOfWeek.THURSDAY, "TH")
      .put(DayOfWeek.FRIDAY, "FR")
      .put(DayOfWeek.SATURDAY, "SA")
      .build();

  private static final Map<Equipment, String> EQUIPMENT = ImmutableMap
      .<Equipment, String>builder()
      .put(Equipment.CRJ, "CRJ")
      .put(Equipment.RJ2, "RJ2")
      .put(Equipment.RJ7, "RJ7")
      .put(Equipment.RJ9, "RJ9")
      .build();

  public String getHtml(Trip trip) {
    String result = "";
    if (trip.getSectionCount() == 0) {
      return "No sections?<br/><pre>" + trip.toString() + "</pre>";
    }
    result = "<table>";
    for (int i = 0; i < trip.getSectionCount(); ++i) {
      Section nextSection = i < trip.getSectionCount() - 1
          ? trip.getSection(i + 1)
          : null;
      result += getTripHtml(trip, trip.getSection(i), nextSection);
    }
    return result + "</table>";
  }

  public String getTripHtml(Trip trip, Section section, Section nextSection) {
    String result = "";
    for (int i = 0; i < section.getLegCount(); ++i) {
      boolean isLastLeg = i == section.getLegCount() - 1;
      result += getLegHtml(section.getLeg(i), isLastLeg ? section : null);
      if (isLastLeg) {
        result += getSectionFooter(trip, section, nextSection);
      }
    }
    return result;
  }

  private String getLegHtml(Leg leg, Section section) {
    String result = "<tr>";
    result += "<td>" + DAYS_OF_WEEK.get(leg.getDayOfWeek()) + "</td>";
    result += "<td>" + leg.getDayOfMonth() + "</td>";
    result += "<td>" + (leg.getIsDeadhead() ? "DH" : "") + "</td>";
    result += "<td>" + (leg.getIsEquipmentSwapUponCompletion() ? "*" : "") + "</td>";
    result += "<td>" + leg.getFlightNumber() + "</td>";
    result += "<td>" + leg.getDepartureAirportCode() + "-" + leg.getArrivalAirportCode()
        + "</td>";
    result += "<td>" + leg.getDepartureLocalTime() + "</td>";
    result += "<td>" + leg.getArrivalLocalTime() + "</td>";
    result += "<td>" + leg.getBlockDuration() + "</td>";
    result += "<td>" + leg.getGroundDuration() + "</td>";
    result += "<td>" + leg.getOtherAirline() + "</td>";
    result += "<td>" + EQUIPMENT.get(leg.getEquipment()) + "</td>";
    if (section != null) {
      result += "<td>" + section.getBlockDuration() + "</td>";
      result += "<td>" + section.getDeadheadDuration() + "</td>";
      result += "<td>" + section.getCreditDuration() + "</td>";
      result += "<td>" + section.getDutyDuration() + "/" + section.getFlightDutyDuration()
          + "</td>";
      result += "<td>" + section.getLayoverAirportCode() + "</td>";
      result += "<td>" + section.getLayoverDuration() + "</td>";
    }
    return result + "</tr>";
  }

  private String getSectionFooter(Trip trip, Section section, Section nextSection) {
    String result = "<tr>";
    result += "<td/>";  // DayOfWeek
    if (nextSection != null) {
      result += String.format("<td colspan=9>D-END: %sL REPT: %sL</td>",
          section.getLocalDutyEndTime(), nextSection.getLocalDutyStartTime());
      result += String.format("<td colspan=4>%s</td>", section.getHotelName());
      result += String.format("<td colspan=3>%s</td>", section.getHotelPhoneNumber());
    } else {
      // end of trip
      result += String.format("<td colspan=5>D-END: %sL", section.getLocalDutyEndTime());
      result += String.format("<td colspan=5><b>T.A.F.B.: %s</b></td>",
          trip.getTimeAwayFromBaseDuration());
      result += "</tr><tr>";
      result += "<td colspan=10 align=right><b>Total:</b></td>";
      result += String.format("<td><b>%s</b></td>", trip.getBlockDuration());
      result += String.format("<td><b>%s</b></td>", trip.getDeadheadDuration());
      result += String.format("<td><b>%s</b></td>", trip.getCreditDuration());
      result += String.format("<td><b>%s/%s</b></td>", trip.getDutyDuration(),
          trip.getFlightDutyDuration());
    }
    return result + "</tr>";
  }
}
