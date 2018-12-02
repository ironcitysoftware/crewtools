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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.YearMonth;

import com.google.common.base.Joiner;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto;
import crewtools.flica.Proto.DayOfWeek;
import crewtools.flica.Proto.Leg;
import crewtools.flica.Proto.Section;
import crewtools.flica.Proto.ThinLine;
import crewtools.flica.Proto.ThinPairing;
import crewtools.flica.Proto.Trip;

public class ThinLineFormatter {
  private final int lastDayOfMonth;
  private final Set<Integer> weekends;

  private static final String WEEKEND_CSS_CLASS = "weekend";
  private static final String TWO_HUNDRED_CSS_CLASS = "rj2";
  private static final String NOT_ELIGIBLE_CSS_CLASS = "gray";

  public ThinLineFormatter(YearMonth yearMonth) {
    this.lastDayOfMonth = yearMonth.toLocalDate(1).dayOfMonth().getMaximumValue();
    this.weekends = new HashSet<>();
    for (int i = 1; i <= lastDayOfMonth; i++) {
      int dayOfWeek = yearMonth.toLocalDate(i).getDayOfWeek();
      if (dayOfWeek == DayOfWeek.SATURDAY_VALUE
          || dayOfWeek == DayOfWeek.SUNDAY_VALUE) {
        weekends.add(i);
      }
    }
  }

  public String getHeaderHtml() {
    String result = "<tr><td/>";
    for (int dom = 1; dom <= lastDayOfMonth; ++dom) {
      result += String.format("<td%s>%d</td>",
          weekends.contains(dom) ? " class=\"" + WEEKEND_CSS_CLASS + "\"" : "",
          dom);
    }
    return result + "</tr>\n";
  }

  public String getRowHtml(ThinLine line, AwardDomicile awardDomicile,
      Map<String, Trip> optionalTripDetail, boolean isEligible) {
    Map<Integer, String> events = new HashMap<>();
    Map<Integer, String> pairings = new HashMap<>();
    for (String carryInDay : line.getCarryInDayList()) {
      events.put(DateTime.parse(carryInDay).getDayOfMonth(), "CI");
    }
    for (ThinPairing pairing : line.getThinPairingList()) {
      int dom = DateTime.parse(pairing.getDate()).getDayOfMonth();
      for (int i = 0; i < pairing.getOvernightAirportCodeCount(); ++i) {
        if (i < pairing.getOvernightAirportCodeCount() - 1
            || !pairing.getOvernightAirportCode(i).equals(awardDomicile.name())) {
          events.put(dom, pairing.getOvernightAirportCode(i));
          pairings.put(dom++, pairing.getPairingName());
        }
      }
    }
    String result = String.format("<tr><td%s>%s</td>",
        !isEligible ? " class=\"" + NOT_ELIGIBLE_CSS_CLASS + "\"" : "",
        line.getLineName());
    for (int dom = 1; dom <= lastDayOfMonth; ++dom) {
      List<String> css = new ArrayList<>();
      if (weekends.contains(dom)) {
        css.add(WEEKEND_CSS_CLASS);
      }
      if (!isEligible) {
        css.add(NOT_ELIGIBLE_CSS_CLASS);
      }
      if (events.containsKey(dom)) {
        if (pairings.containsKey(dom)) {
          String pairingName = pairings.get(dom);
          if (optionalTripDetail.containsKey(pairingName)) {
            if (isTwoHundred(optionalTripDetail.get(pairingName))) {
              css.add(TWO_HUNDRED_CSS_CLASS);
            }
          }
          result += String.format("<td%s id=\"%s\">%s</td>",
              getClass(css), pairings.get(dom), events.get(dom));
        } else {
          result += String.format("<td%s>%s</td>",
              getClass(css), events.get(dom));
        }
      } else {
        result += String.format("<td%s>&nbsp;&nbsp;&nbsp;</td>", getClass(css));
      }
    }
    return result + "</tr>\n";
  }

  private static final Joiner CSS_CLASS_JOINER = Joiner.on(' ');

  private String getClass(List<String> css) {
    return css.isEmpty()
        ? ""
        : " class=\"" + CSS_CLASS_JOINER.join(css) + "\"";
  }

  private boolean isTwoHundred(Trip trip) {
    for (Section section : trip.getSectionList()) {
      for (Leg leg : section.getLegList()) {
        if (leg.hasEquipment() && leg.getEquipment().equals(Proto.Equipment.RJ2)) {
          return true;
        }
      }
    }
    return false;
  }
}