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
      .add("RFD")
      .add("TUL")
      .add("OKC")
      .add("ORD")
      .add("MKE")
      .add("HSV")
      .add("BHM")
      .add("DSM")
      .add("CID")
      .add("GPT")
      .add("PNS")
      .add("VPS")
      .add("JAN")
      .add("XNA")
      .add("MSN")
      .add("MOB")
      .add("STL")
      .add("BNA")
      .add("PIA")
      .add("EVV")
      .add("BTR")
      .add("LIT")
      .add("SAT")
      .add("MEM")
      .add("OMA")
      .build();

  private static final DateTimeZone EASTERN = DateTimeZone.forID("America/New_York");
  private static final DateTimeZone CENTRAL = DateTimeZone.forID("America/Chicago");

  public DateTimeZone getDateTimeZone(String station) {
    if (CENTRAL_TIMEZONE_STATIONS.contains(station)) {
      return CENTRAL;
    } else {
      return EASTERN;
    }
  }
}
