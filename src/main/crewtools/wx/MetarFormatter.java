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
    if (metar.windSpecified) {
      wind = formatWind(metar.windVariable, metar.windFrom, metar.windVelocity,
          metar.windGusts, metar.windVaryFrom, metar.windVaryTo);
    }
    String visibility = formatVisibility(metar.isVisibilityGreaterThanSix,
        metar.visibilityWhole, metar.visNum, metar.visDen,
        metar.visibilityMeters); // RVR
    String weather = weatherJoiner.join(metar.weather);
    if (weather.isEmpty()) {
      weather = null;
    }
    String ceiling = formatCeiling(metar.ceiling);
    return spaceJoiner.join(wind, visibility, weather, ceiling);
  }

  private String formatWind(boolean isVariable, Integer from, int velocity,
      int gusts, int varyFrom, int varyTo) {
    String result;
    if (isVariable) {
      result = "VRB";
    } else {
      result = String.format("%03d", from);
    }
    result += String.format("%02d", velocity);
    if (gusts > 0) {
      result += String.format("G%d", gusts);
    }
    result += "KT";
    if (varyFrom > 0 && varyTo > 0) {
      result += String.format(" %dV%d", varyFrom, varyTo);
    }
    return result;
  }

  private String formatVisibility(boolean isGreaterThanSix,
      int whole, int num, int den, int meters) {
    if (isGreaterThanSix) {
      return "P6SM";
    }
    if (whole == 0 && num > 0 && den == 0) {
      return String.format("%dSM", num);
    }
    if (whole > 0 && num == 0 && den == 0) {
      return String.format("%dSM", whole);
    }
    if (whole > 0 && num > 0 && den > 0) {
      return String.format("%d %d/%dSM", whole, num, den);
    }
    if (whole == 0 && num > 0 && den > 0) {
      return String.format("%d/%dSM", num, den);
    }
    if (meters > 0) {
      return String.format("%04d", meters);
    }
    return null;
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
