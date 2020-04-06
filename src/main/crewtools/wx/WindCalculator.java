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

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public class WindCalculator {
  public WindCalculator() {
  }

  public Result calculateExcludingGusts(Wind wind, int runwayNumber) {
    Preconditions.checkNotNull(wind.velocity, "Wind must have a velocity: " + wind);
    return calculate(wind, wind.velocity, runwayNumber);
  }

  public Result calculateIncludingGusts(Wind wind, int runwayNumber) {
    Preconditions.checkNotNull(wind.velocity, "Wind must have a velocity: " + wind);
    int velocity = wind.velocity;
    if (wind.gusts != null) {
      Preconditions.checkState(wind.gusts > velocity,
          "Expected gusts > velocity? " + wind);
      velocity = wind.gusts;
    }
    return calculate(wind, velocity, runwayNumber);
  }

  private Result calculate(Wind wind, int velocity, int runwayNumber) {
    int runwayHeading = 10 * runwayNumber;
    int crosswindVelocity;
    int headwindVelocity;
    if (wind.isVariable && (wind.varyFromDegrees == null || wind.varyToDegrees == null)) {
      // assume it is a direct crosswind and a direct tailwind.
      crosswindVelocity = wind.velocity;
      headwindVelocity = -wind.velocity;
    } else if (wind.isVariable && wind.varyFromDegrees != null
        && wind.varyToDegrees != null) {
      throw new UnsupportedOperationException("Implement varying wind");
    } else {
      int angle = runwayHeading - wind.fromDegrees;
      headwindVelocity = (int) (Math.cos(Math.toRadians(angle)) * wind.velocity);
      crosswindVelocity = Math
          .abs((int) (Math.sin(Math.toRadians(angle)) * wind.velocity));
    }
    return new Result(crosswindVelocity, headwindVelocity);
  }

  public class Result {
    private final int crosswindVelocity;
    private final int headwindVelocity; // negative is tailwind

    private Result(int crosswindVelocity, int headwindVelocity) {
      this.crosswindVelocity = crosswindVelocity;
      this.headwindVelocity = headwindVelocity;
    }

    public int getCrosswindVelocity() {
      return crosswindVelocity;
    }

    public int getHeadwindVelocity() {
      return headwindVelocity;
    }

    // Probably a better way to do this.
    // Maximize the crosswind and the tailwind.
    public Result maximize(Result that) {
      if (that == null) {
        return this;
      }
      return new Result(Ints.max(crosswindVelocity, that.crosswindVelocity),
          Ints.min(headwindVelocity, that.headwindVelocity));
    }

    @Override
    public String toString() {
      return String.format("xwind:%02d tailwind:%02d", crosswindVelocity,
          headwindVelocity < 0 ? -headwindVelocity : 0);
    }
  }

}
