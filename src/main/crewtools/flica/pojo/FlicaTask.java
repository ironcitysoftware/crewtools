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
import java.util.List;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import crewtools.util.Period;

// OpenTime javascript object
public class FlicaTask {
  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').omitEmptyStrings().trimResults();
  private static final DateTimeFormatter LOCAL_DATE = DateTimeFormat.forPattern("ddMMM");
  private static final DateTimeFormatter LOCAL_PAIRING_DATE = DateTimeFormat.forPattern("yyyyMMdd");
  private static final DateTimeFormatter LOCAL_TIME = DateTimeFormat.forPattern("HH:mm");
  private static final DateTimeFormatter LOCAL_PERIOD = DateTimeFormat.forPattern("HHmm");

  public FlicaTask(PairingKey key, Period creditTime, int numDays) {
    this.tradeboardRequestId = null;
    this.creditTime = creditTime;
    this.pairingName = key.getPairingName();
    this.pairingDate = key.getPairingDate();
    isTruePairing = true;
    date = null;
    this.numDays = numDays;
    reportTime = null;
    departureTime = null;
    arrivalTime = null;
    blockTime = null;
    layoverAirportCodes = null;
    bidPos = "";
    position = "";
    rpt = 0;
    end = 0;
    asterisk = "";
    isQualified = false;
    unqmsg = "";
    carryOver = 0;
    canSplit = false;
    created = "";
    AOTS = 0;
    unpub = "";
    isPairUnpublished = false;
    repeqpd = false;
    repdate = null;
    isReserveDate = false;
    maxDutyPeriod = null;
  }

  public FlicaTask(PairingKey key, Period creditTime) {
    this.tradeboardRequestId = null;
    this.creditTime = creditTime;
    this.pairingName = key.getPairingName();
    this.pairingDate = key.getPairingDate();
    isTruePairing = true;
    date = null;
    numDays = 0;
    reportTime = null;
    departureTime = null;
    arrivalTime = null;
    blockTime = null;
    layoverAirportCodes = null;
    bidPos = "";
    position = "";
    rpt = 0;
    end = 0;
    asterisk = "";
    isQualified = false;
    unqmsg = "";
    carryOver = 0;
    canSplit = false;
    created = "";
    AOTS = 0;
    unpub = "";
    isPairUnpublished = false;
    repeqpd = false;
    repdate = null;
    isReserveDate = false;
    maxDutyPeriod = null;
  }

  public FlicaTask(int year, Iterator<String> input, Integer tradeboardRequestId) {
    this.tradeboardRequestId = tradeboardRequestId;
    isTruePairing = Boolean.parseBoolean(input.next());
    pairingName = input.next();
    date = LOCAL_DATE.parseLocalDate(input.next()).withYear(year);
    pairingDate = LOCAL_PAIRING_DATE.parseLocalDate(input.next());
    numDays = Integer.parseInt(input.next());
    reportTime = LOCAL_TIME.parseLocalTime(input.next());
    departureTime = LOCAL_TIME.parseLocalTime(input.next());
    arrivalTime = LOCAL_TIME.parseLocalTime(input.next());
    blockTime = Period.fromText(input.next());
    layoverAirportCodes = SPACE_SPLITTER.splitToList(input.next());
    bidPos = input.next();
    position = input.next();
    rpt = Integer.parseInt(input.next());
    end = Integer.parseInt(input.next());
    asterisk = input.next();
    isQualified = Boolean.parseBoolean(input.next());
    unqmsg = input.next();
    carryOver = Integer.parseInt(input.next());
    creditTime = Period.fromText(input.next());
    canSplit = Boolean.parseBoolean(input.next());
    created = input.next();
    AOTS = Integer.parseInt(input.next());
    unpub = input.next();
    isPairUnpublished = Boolean.parseBoolean(input.next());
    repeqpd = Boolean.parseBoolean(input.next());
    // in canceled pairings (from Tradeboard?), this is blank
    String repdateString = input.next();
    if (!repdateString.isEmpty()) {
      repdate = LOCAL_DATE.parseLocalDate(repdateString).withYear(year);
    } else {
      repdate = null;
    }
    isReserveDate = Boolean.parseBoolean(input.next());
    maxDutyPeriod = LOCAL_PERIOD.parseLocalTime(input.next());
    Preconditions.checkState(!input.hasNext());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pairingName", pairingName)
        .add("date", date)
        .add("pairingDate", pairingDate)
        .add("blockTime", blockTime)
        .add("creditTime", creditTime)
        .toString();
  }

  final boolean isTruePairing;  // truepair
  public final String pairingName;
  final LocalDate date;  // d
  public final LocalDate pairingDate;  // pdate
  public final int numDays;  // days
  final LocalTime reportTime;  // rpttime
  final LocalTime departureTime;  // dpttime
  final LocalTime arrivalTime;  // endtime
  public final Period blockTime;  // hrs
  public final List<String> layoverAirportCodes;  // lay
  final String bidPos;
  final String position;  // pos, eg FO
  final int rpt;
  final int end;
  final String asterisk;
  final boolean isQualified;
  final String unqmsg;
  final int carryOver;
  public final Period creditTime;  // pay
  final boolean canSplit;
  final String created;  // eg "479:39"
  final int AOTS;  // maybe boolean?
  final String unpub;
  final boolean isPairUnpublished;
  final boolean repeqpd;
  public final LocalDate repdate;
  final boolean isReserveDate;
  final LocalTime maxDutyPeriod;
  public final Integer tradeboardRequestId;

  @Override
  public boolean equals(Object that) {
    if (that == null) { return false; }
    if (!(that instanceof FlicaTask)) { return false; }
    return ((FlicaTask) that).pairingName.equals(pairingName);
  }

  @Override
  public int hashCode() {
    return pairingName.hashCode();
  }
}
