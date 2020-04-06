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

package crewtools.wx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.joda.time.DateTime;

import com.google.common.base.MoreObjects;

public class ParsedMetar {
  // TODO: can have both?
  public Visibility rvr;
  public Visibility visibility;

  int temperature;
  int dewpoint;
  boolean temperatureDewpointSpecified;

  public Wind wind;

  public DateTime issued;
  public boolean isAutomated = false;
  public boolean isValid = false;
  public String airportId;

  public List<String> weather = new ArrayList<>();
  public Map<Integer, String> ceiling = new TreeMap<>();

  @Override
  public int hashCode() {
    return Objects.hash(
        rvr,
        visibility,
        temperature,
        dewpoint,
        temperatureDewpointSpecified,
        ceiling,
        wind,
        issued,
        isAutomated,
        isValid,
        weather);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof ParsedMetar)) {
      return false;
    }
    ParsedMetar that = (ParsedMetar) o;
    return Objects.equals(rvr, that.rvr)
        && Objects.equals(visibility, that.visibility)
        && Objects.equals(temperature, that.temperature)
        && Objects.equals(dewpoint, that.dewpoint)
        && Objects.equals(temperatureDewpointSpecified, that.temperatureDewpointSpecified)
        && Objects.equals(ceiling, that.ceiling)
        && Objects.equals(wind, that.wind)
        && Objects.equals(issued, that.issued)
        && Objects.equals(isAutomated, that.isAutomated)
        && Objects.equals(isValid, that.isValid)
        && Objects.equals(weather, that.weather);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("rvr", rvr)
        .add("visibility", visibility)
        .add("temperature", temperature)
        .add("dewpont", dewpoint)
        .add("temperatureDewpointSpecified", temperatureDewpointSpecified)
        .add("ceiling", ceiling)
        .add("wind", wind)
        .add("issued", issued)
        .add("auto", isAutomated)
        .add("isValid", isValid)
        .add("weather", weather)
        .toString();
  }
}
