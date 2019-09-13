/**
 * Copyright 2019 Iron City Software LLC
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

package crewtools.wx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.base.Joiner;

import crewtools.wx.ParsedTaf.TafPeriod;

public class TafFormatter {
  private MetarFormatter metarFormatter = new MetarFormatter();
  private final Joiner joiner = Joiner.on(' ');

  public String[] format(ParsedTaf taf) {
    List<String> result = new ArrayList<>();
    List<String> firstLine = new ArrayList<>();
    firstLine.add("TAF");
    firstLine.add("KXXX");
    if (taf.issued != null) {
      firstLine.add(String.format("%02d%02d%02dZ",
          taf.issued.getDayOfMonth(),
          taf.issued.getHourOfDay(),
          taf.issued.getMinuteOfHour()));
    }
    firstLine.add(String.format("%02d%02d/%02d%02d",
        taf.validFrom.getDayOfMonth(),
        taf.validFrom.getHourOfDay(),
        taf.validTo.getDayOfMonth(),
        taf.validTo.getHourOfDay()));
    if (taf.forecast.isEmpty()) {
      result.add(joiner.join(firstLine));
    } else {
      Iterator<TafPeriod> periods = taf.forecast.keySet().iterator();
      TafPeriod period = periods.next();
      firstLine.add(format(false, period, taf.forecast.get(period)));
      result.add(joiner.join(firstLine));
      while (periods.hasNext()) {
        period = periods.next();
        result.add(format(true, period, taf.forecast.get(period)));
      }
    }
    return result.toArray(new String[result.size()]);
  }

  private String format(boolean includeTime, TafPeriod period, ParsedMetar forecast) {
    String result = "";
    if (period.interval != null) {
      DateTime start = period.interval.getStart().withZone(DateTimeZone.UTC);
      DateTime end = period.interval.getEnd().withZone(DateTimeZone.UTC);
      if (includeTime && period.modifier == null) {
        result += String.format("FM%02d%02d%02d ",
            start.getDayOfMonth(),
            start.getHourOfDay(),
            start.getMinuteOfHour());
      } else if (period.modifier != null) {
        String prefix = "";
        switch (period.modifier) {
          case BECMG:
            prefix = "BECMG";
            break;
          case TEMPO:
            prefix = "TEMPO";
            break;
          case PROB:
            prefix = String.format("PROB%d", period.probability);
            break;
        }
        result += String.format("%s %02d%02d/%02d%02d ",
            prefix,
            start.getDayOfMonth(),
            start.getHourOfDay(),
            end.getDayOfMonth(),
            end.getHourOfDay());
      }
    }
    result += metarFormatter.formatConditions(forecast);
    return result;
  }
}
