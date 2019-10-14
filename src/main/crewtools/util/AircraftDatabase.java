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

package crewtools.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;

public class AircraftDatabase {
  private static final File AIRCRAFT_DATABASE = new File("data/aircraft.txt");

  private static final Map<String, String> TYPES = ImmutableMap.of(
      "CL-600-2B19", "RJ2",
      "CL-600-2C10", "RJ7",
      "CL-600-2D24", "RJ9");

  private static final Splitter SPLITTER = Splitter.on(',');

  private final Map<Integer, String> tailMap = new HashMap<>();
  private final Map<Integer, String> typeMap = new HashMap<>();

  public AircraftDatabase() throws IOException {
    for (String line : Files.readLines(AIRCRAFT_DATABASE, StandardCharsets.UTF_8)) {
      // Expect N539EA,539,10318,CL-600-2C10
      List<String> components = SPLITTER.splitToList(line);
      int shorthandTailNumber = Ints.tryParse(components.get(1));
      tailMap.put(shorthandTailNumber, components.get(0));
      typeMap.put(shorthandTailNumber, TYPES.get(components.get(3)));
    }
  }

  public String getTailNumber(int shorthandTailNumber) {
    return tailMap.get(shorthandTailNumber);
  }

  public String getAircraftType(int shorthandTailNumber) {
    return typeMap.get(shorthandTailNumber);
  }
}
