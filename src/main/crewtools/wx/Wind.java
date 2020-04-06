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

import java.util.Objects;

import com.google.common.base.Preconditions;

public class Wind {
  Integer fromDegrees;
  boolean isVariable;
  Integer velocity;
  Integer gusts;
  Integer varyFromDegrees;
  Integer varyToDegrees;

  // from velocity (gusts)
  // VRB velocity (gusts)
  // from V to (gusts)
  public Wind(Integer fromDegrees, boolean isVariable, Integer velocity,
      Integer gusts, Integer varyFromDegrees, Integer varyToDegrees) {
    Preconditions.checkArgument(fromDegrees != null ^ isVariable);
    this.fromDegrees = fromDegrees;
    this.isVariable = isVariable;
    this.velocity = velocity;
    this.gusts = gusts;
    this.varyFromDegrees = varyFromDegrees;
    this.varyToDegrees = varyToDegrees;
  }

  @Override
  public String toString() {
    String result = "";
    boolean haveWind = false;
    if (fromDegrees != null) {
      haveWind = true;
      result = String.format("%03d%02d", fromDegrees, velocity);
    } else if (isVariable) {
      haveWind = true;
      result = String.format("VRB%02d", velocity);
    }
    if (haveWind && gusts != null) {
      result += String.format("G%02d", gusts);
    }
    if (haveWind) {
      result += "KT";
    }
    if (varyFromDegrees != null && varyToDegrees != null) {
      if (haveWind) {
        result += " ";
      }
      result += String.format("%03dV%03d", varyFromDegrees, varyToDegrees);
    }
    return result;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        fromDegrees,
        isVariable,
        velocity,
        gusts,
        varyFromDegrees,
        varyToDegrees);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof Wind)) {
      return false;
    }
    Wind that = (Wind) o;
    return Objects.equals(fromDegrees, that.fromDegrees)
        && Objects.equals(isVariable, that.isVariable)
        && Objects.equals(velocity, that.velocity)
        && Objects.equals(gusts, that.gusts)
        && Objects.equals(varyFromDegrees, that.varyFromDegrees)
        && Objects.equals(varyToDegrees, that.varyToDegrees);
  }
}
