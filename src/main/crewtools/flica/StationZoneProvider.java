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

package crewtools.flica;

import org.joda.time.DateTimeZone;

import com.google.common.collect.ImmutableSet;

// TODO: productionize.
public class StationZoneProvider {
  ImmutableSet<String> CENTRAL_TIMEZONE_STATIONS = ImmutableSet.<String>builder()
      .add("AUS")
      .add("BHM")
      .add("BNA")
      .add("BTR")
      .add("CID")
      .add("DSM")
      .add("EVV")
      .add("GPT")
      .add("HSV")
      .add("JAN")
      .add("LIT")
      .add("MCI")
      .add("MEM")
      .add("MGM")
      .add("MKE")
      .add("MOB")
      .add("MSN")
      .add("MSP")
      .add("MSY")
      .add("OKC")
      .add("OMA")
      .add("ORD")
      .add("PIA")
      .add("PNS")
      .add("RFD")
      .add("SAT")
      .add("SHV")
      .add("STL")
      .add("TUL")
      .add("VPS")
      .add("XNA")
      .build();

  ImmutableSet<String> MOUNTAIN_TIMEZONE_STATIONS = ImmutableSet.<String>builder()
      .add("RAP")
      .build();

  private static final DateTimeZone EASTERN = DateTimeZone.forID("America/New_York");
  private static final DateTimeZone CENTRAL = DateTimeZone.forID("America/Chicago");
  private static final DateTimeZone MOUNTAIN = DateTimeZone.forID("America/Denver");

  public DateTimeZone getDateTimeZone(String station) {
    if (CENTRAL_TIMEZONE_STATIONS.contains(station)) {
      return CENTRAL;
    } else if (MOUNTAIN_TIMEZONE_STATIONS.contains(station)) {
      return MOUNTAIN;
    } else {
      return EASTERN;
    }
  }
}
