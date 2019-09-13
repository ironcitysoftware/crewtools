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

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;

import com.google.common.base.MoreObjects;

public class ParsedTaf {
  public DateTime issued;
  public DateTime validFrom;
  public DateTime validTo;

  public static class TafPeriod implements Comparable<TafPeriod> {
    public TafPeriod(Interval interval, Modifier modifier, Integer probability) {
      this.interval = interval;
      this.modifier = modifier;
      this.probability = probability;
    }

    final Interval interval;

    enum Modifier {
      BECMG,
      TEMPO,
      PROB,
    }

    final Modifier modifier;
    final Integer probability;

    public TafPeriod truncateTo(DateTime newEnd) {
      return new TafPeriod(
          new Interval(interval.getStartMillis(), newEnd.getMillis()),
          modifier, probability);
    }

    @Override
    public int hashCode() {
      return interval.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || !(o instanceof TafPeriod)) {
        return false;
      }
      TafPeriod that = (TafPeriod) o;
      return interval.equals(that.interval);
    }

    @Override
    public String toString() {
      DateTime start = new DateTime(interval.getStartMillis()).withZone(DateTimeZone.UTC);
      DateTime end = new DateTime(interval.getEndMillis()).withZone(DateTimeZone.UTC);
      return MoreObjects.toStringHelper(this)
          .add("start", start)
          .add("end", end)
          .add("modifier", modifier)
          .add("prob", probability)
          .toString();
    }

    @Override
    public int compareTo(TafPeriod o) {
      return interval.getStart().compareTo(o.interval.getStart());
    }
  }

  public Map<TafPeriod, ParsedMetar> forecast = new TreeMap<>();

  @Override
  public int hashCode() {
    return Objects.hash(issued, validFrom, validTo, forecast);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof ParsedTaf)) {
      return false;
    }
    ParsedTaf that = (ParsedTaf) o;
    return Objects.equals(issued, that.issued)
        && Objects.equals(validFrom, that.validFrom)
        && Objects.equals(validTo, that.validTo)
        && Objects.equals(forecast, that.forecast);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("issued", issued)
        .add("validFrom", validFrom)
        .add("validTo", validTo)
        .add("forecast", forecast)
        .toString();
  }
}
