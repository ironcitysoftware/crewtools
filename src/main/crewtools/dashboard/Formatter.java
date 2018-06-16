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

package crewtools.dashboard;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Formatter {
  private final DateTimeFormatter HH_COLON_MM = DateTimeFormat.forPattern("HH:mm");

  public String getZulu(DateTime dateTime) {
    return dateTime == null ? ""
        : HH_COLON_MM.print(dateTime.withZone(DateTimeZone.UTC)) + "Z";
  }

  public String getPrettyOffset(DateTime now, DateTime then) {
    if (then == null) {
      return "";
    }
    boolean isNegative = then.isBefore(now);
    Interval interval = isNegative ? new Interval(then, now) : new Interval(now, then);
    Period period = interval.toPeriod();
    int hours = period.getHours() + 24 * period.getDays();
    int minutes = period.getMinutes();
    if (hours > 0) {
      if (minutes > 0) {
        return String.format("%s%dh%dm",
            isNegative ? "-" : "+",
            hours,
            minutes);
      } else {
        return String.format("%s%dh",
            isNegative ? "-" : "+",
            hours);
      }
    } else {
      return String.format("%s%dm",
          isNegative ? "-" : "+",
          minutes);
    }
  }
}
