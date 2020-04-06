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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;

public class MetarFormatter {
  private final Joiner spaceJoiner = Joiner.on(' ').skipNulls();
  private final Joiner weatherJoiner = Joiner.on("").skipNulls();
  public void format(ParsedMetar metar) {
    throw new UnsupportedOperationException("TODO");
  }

  public String formatConditions(ParsedMetar metar) {
    String wind = null;
    if (metar.wind != null) {
      wind = metar.wind.toString();
    }
    String visibility = metar.visibility.toString();
    if (metar.rvr != null) {
      visibility += " " + metar.rvr;
    }
    String weather = weatherJoiner.join(metar.weather);
    if (weather.isEmpty()) {
      weather = null;
    }
    String ceiling = formatCeiling(metar.ceiling);
    return spaceJoiner.join(wind, visibility, weather, ceiling);
  }

  private String formatCeiling(Map<Integer, String> ceiling) {
    if (ceiling.isEmpty()) {
      return "SKC";
    }
    List<String> layers = new ArrayList<>();
    for (int altitude : ceiling.keySet()) {
      layers.add(ceiling.get(altitude) + String.format("%03d", altitude / 100));
    }
    return spaceJoiner.join(layers);
  }
}
