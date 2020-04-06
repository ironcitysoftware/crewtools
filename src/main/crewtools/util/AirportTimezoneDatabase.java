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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTimeZone;

import com.google.common.base.Splitter;
import com.google.common.io.Files;

public class AirportTimezoneDatabase {
  private static final File AIRPORT_TIMEZONE_DATABASE = new File(
      "data/airport-timezone.txt");

  private static final Splitter SPLITTER = Splitter.on(',');

  private final Map<String, DateTimeZone> zoneMap = new HashMap<>();

  public AirportTimezoneDatabase() throws IOException {
    for (String line : Files.readLines(AIRPORT_TIMEZONE_DATABASE, StandardCharsets.UTF_8)) {
      // Expect LGA,America/New_York
      List<String> components = SPLITTER.splitToList(line);
      zoneMap.put(components.get(0), DateTimeZone.forID(components.get(1)));
    }
  }

  public DateTimeZone getZone(String faaId) {
    return zoneMap.get(faaId);
  }
}
