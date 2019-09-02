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

package crewtools.flica.stats;

import java.util.Objects;

public class LineCount {
  private final int roundOne;
  private final int roundTwo;
  private final int longCall;

  public LineCount(int roundOne, int roundTwo, int longCall) {
    this.roundOne = roundOne;
    this.roundTwo = roundTwo;
    this.longCall = longCall;
  }

  public int getNumRoundOne() {
    return roundOne;
  }

  public int getNumRoundTwo() {
    return roundTwo;
  }

  public int getNumLongCall() {
    return longCall;
  }

  public LineCount increment(int factor) {
    return new LineCount(roundOne + factor, roundTwo, longCall);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roundOne, roundTwo, longCall);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof LineCount)) {
      return false;
    }
    LineCount that = (LineCount) o;
    return this.roundOne == that.roundOne
        && this.roundTwo == that.roundTwo
        && this.longCall == that.longCall;
  }

  @Override
  public String toString() {
    return String.format("1:%d 2:%d lc:%d", roundOne, roundTwo, longCall);
  }
}
