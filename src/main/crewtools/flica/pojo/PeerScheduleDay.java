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

import java.util.Iterator;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class PeerScheduleDay {
  private static final DateTimeFormatter LOCAL_PAIRING_DATE = DateTimeFormat
      .forPattern("yyyyMMdd");
  private static final String ESCAPED_NBSP = "%26nbsp%3B";
  private static final String NBSP = "&nbsp;";
  private static final String DARR = "&darr;";

  public final boolean unknown;
  public final String dayOfWeek;
  public final String duty;
  public final int dayOfMonth;
  public final String overnight;
  public final LocalDate pairingDate;
  public final boolean isPairing;
  public final boolean unknown2;
  public final String linkText;

  public PeerScheduleDay(Iterator<String> input) {
    this.unknown = Boolean.parseBoolean(input.next());
    this.dayOfWeek = input.next();
    this.dayOfMonth = Integer.parseInt(input.next());
    String tmp = input.next();
    if (tmp.equals(ESCAPED_NBSP)) {
      tmp = "";
    }
    this.duty = tmp;
    this.overnight = input.next();
    tmp = input.next();
    this.pairingDate = tmp.isEmpty() ? null : LOCAL_PAIRING_DATE.parseLocalDate(tmp);
    this.isPairing = Boolean.parseBoolean(input.next());
    this.unknown2 = Boolean.parseBoolean(input.next());
    tmp = input.next();
    tmp = tmp.replace(NBSP, "");
    tmp = tmp.replace(DARR, "");
    this.linkText = tmp;
    Preconditions.checkArgument(!input.hasNext());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("unknown", unknown)
        .add("dayOfWeek", dayOfWeek)
        .add("duty", duty)
        .add("dayOfMonth", dayOfMonth)
        .add("overnight", overnight)
        .add("pairingDate", pairingDate)
        .add("isPairing", isPairing)
        .add("unknown2", unknown2)
        .add("linkText", linkText)
        .toString();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null) {
      return false;
    }
    if (!(that instanceof PeerScheduleDay)) {
      return false;
    }
    return ((PeerScheduleDay) that).toString().equals(toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }
}
