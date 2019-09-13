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
import java.util.Objects;
import java.util.TreeMap;

import org.joda.time.DateTime;

import com.google.common.base.MoreObjects;

public class ParsedMetar {
  int rvr;
  boolean isVisibilityGreaterThanSix = false;
  int visibilityMeters;
  int visibilityWhole;
  int visNum;
  int visDen;

  int windVaryFrom;
  int windVaryTo;
  int windFrom;
  int windVelocity;
  int windGusts;
  boolean windVariable;
  boolean windSpecified;

  public DateTime issued;
  public boolean isAutomated = false;
  public boolean isValid = false;

  public List<String> weather = new ArrayList<>();
  public Map<Integer, String> ceiling = new TreeMap<>();

  @Override
  public int hashCode() {
    return Objects.hash(
        rvr,
        isVisibilityGreaterThanSix,
        visibilityMeters,
        visibilityWhole,
        visNum,
        visDen,
        ceiling,
        windVaryFrom,
        windVaryTo,
        windFrom,
        windVelocity,
        windGusts,
        windSpecified,
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
        && Objects.equals(isVisibilityGreaterThanSix, that.isVisibilityGreaterThanSix)
        && Objects.equals(visibilityMeters, that.visibilityMeters)
        && Objects.equals(visibilityWhole, that.visibilityWhole)
        && Objects.equals(visNum, that.visNum)
        && Objects.equals(visDen, that.visDen)
        && Objects.equals(ceiling, that.ceiling)
        && Objects.equals(windVaryFrom, that.windVaryFrom)
        && Objects.equals(windVaryTo, that.windVaryTo)
        && Objects.equals(windFrom, that.windFrom)
        && Objects.equals(windVelocity, that.windVelocity)
        && Objects.equals(windGusts, that.windGusts)
        && Objects.equals(windSpecified, that.windSpecified)
        && Objects.equals(issued, that.issued)
        && Objects.equals(isAutomated, that.isAutomated)
        && Objects.equals(isValid, that.isValid)
        && Objects.equals(weather, that.weather);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("rvr", rvr)
        .add("P6SM", isVisibilityGreaterThanSix)
        .add("visMeters", visibilityMeters)
        .add("visWhole", visibilityWhole)
        .add("visNum", visNum)
        .add("visDen", visDen)
        .add("ceiling", ceiling)
        .add("windVaryFrom", windVaryFrom)
        .add("windVaryTo", windVaryTo)
        .add("windFrom", windFrom)
        .add("windVelocity", windVelocity)
        .add("windGusts", windGusts)
        .add("windSpecified", windSpecified)
        .add("issued", issued)
        .add("auto", isAutomated)
        .add("isValid", isValid)
        .add("weather", weather)
        .toString();
  }
}
