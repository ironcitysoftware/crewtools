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

package crewtools.util;

import org.joda.time.DateTime;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import com.google.common.base.Preconditions;

public class Period implements Comparable<Period> {
  private static PeriodFormatter HHMM_PERIOD =
      new PeriodFormatterBuilder().maximumParsedDigits(2).appendHours().appendMinutes().toFormatter();

  private final int minutes;
  
  public static final Period ZERO = new Period(0);

  public static Period hours(int hours) {
    return new Period(hours * 60);
  }
  
  public static Period minutes(int minutes) {
    return new Period(minutes);
  }
  
  public static Period fromText(String hhMmText) {
    return Period.fromJodaPeriod(HHMM_PERIOD.parsePeriod(hhMmText));
  }

  private static Period fromJodaPeriod(org.joda.time.Period jodaPeriod) {
    Preconditions.checkState(jodaPeriod.getMillis() == 0);
    Preconditions.checkState(jodaPeriod.getSeconds() == 0);
    Preconditions.checkState(jodaPeriod.getMonths() == 0);
    Preconditions.checkState(jodaPeriod.getYears() == 0);
    return new Period(jodaPeriod.getMinutes()
        + jodaPeriod.getHours() * 60
        + jodaPeriod.getDays() * 24 * 60);
  }

  public Period(DateTime start, DateTime end) {
    org.joda.time.Period jodaPeriod = new org.joda.time.Period(start, end);
    this.minutes = Period.fromJodaPeriod(jodaPeriod).minutes;
  }

  public Period(Period other) {
    this(other.minutes);
  }
  
  private Period(int minutes) {
    this.minutes = minutes;
  }
    
  public Period plus(Period that) {
    return new Period(minutes + that.minutes);
  }
  
  public Period minus(Period that) {
    return new Period(minutes - that.minutes);
  }

  public Period half() {
    return Period.minutes((minutes + 1) / 2);
  }
  
  public int getHours() {
    return minutes / 60;
  }
  
  public String toHhMmString() {
    Preconditions.checkState(minutes >= 0);
    return String.format("%02d%02d", minutes / 60, minutes % 60);
  }
  
  public boolean isLessThan(Period period) {
    return minutes < period.minutes;
  }

  @Override
  public String toString() {
    int absMinutes = Math.abs(minutes);
    return String.format("%s%02d:%02d", 
        minutes < 0 ? "-" : "", absMinutes / 60, absMinutes % 60);
  }

  @Override
  public int hashCode() {
    return minutes;
  }
  
  @Override
  public boolean equals(Object that) {
    if (that == null) {
      return false;
    }
    if (!(that instanceof Period)) {
      return false;
    }
    return ((Period) that).minutes == minutes;
  }

  @Override
  public int compareTo(Period that) {
    if (minutes > that.minutes) {
      return 1;
    }
    return minutes < that.minutes ? -1 : 0;
  }
}
