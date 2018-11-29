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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.DayOfWeek;
import crewtools.flica.Proto.ThinLine;
import crewtools.flica.Proto.ThinPairing;

public class ThinLineFormatter {
  private final YearMonth yearMonth;
  private final int lastDayOfMonth;
  private final Set<Integer> weekends;

  private static final String WEEKEND_CSS_CLASS = "weekend";

  public ThinLineFormatter(YearMonth yearMonth) {
    this.yearMonth = yearMonth;
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

  public String getRowHtml(ThinLine line, AwardDomicile awardDomicile) {
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
    String result = "<tr><td>" + line.getLineName() + "</td>";
    for (int dom = 1; dom <= lastDayOfMonth; ++dom) {
      String classString = weekends.contains(dom)
          ? " class=\"" + WEEKEND_CSS_CLASS + "\""
          : "";
      if (events.containsKey(dom)) {
        if (pairings.containsKey(dom)) {
          result += String.format("<td %s id=\"%s\">%s</td>",
              classString, pairings.get(dom), events.get(dom));
        } else {
          result += String.format("<td%s>%s</td>",
              classString, events.get(dom));
        }
      } else {
        result += String.format("<td%s>&nbsp;&nbsp;&nbsp;</td>", classString);
      }
    }
    return result + "</tr>\n";
  }
}