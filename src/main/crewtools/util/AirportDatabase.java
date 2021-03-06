/**
 * Copyright 2020 Iron City Software LLC
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.protobuf.TextFormat;

import crewtools.airport.Proto.Airport;
import crewtools.airport.Proto.AirportList;

public class AirportDatabase {
  private static final File AIRPORT_DATABASE = new File(
      "data/airport.txt");

  private final Map<String, Airport> airports;

  public AirportDatabase() throws IOException {
    AirportList.Builder builder = AirportList.newBuilder();
    TextFormat.getParser().merge(
        Files.toString(AIRPORT_DATABASE, StandardCharsets.UTF_8), builder);
    ImmutableMap.Builder<String, Airport> mapBuilder = ImmutableMap.builder();
    for (Airport airport : builder.build().getAirportList()) {
      mapBuilder.put(airport.getFaaId(), airport);
    }
    airports = mapBuilder.build();
  }

  public Airport getAirport(String faaId) {
    return airports.get(faaId);
  }
}
