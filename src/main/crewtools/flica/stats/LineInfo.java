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

import java.util.Map;
import java.util.Objects;

public class LineInfo {
  private final Map<String, AwardType> lineNameToAwardType;
  private final int roundOne;
  private final int roundTwo;
  private final int longCall;

  public LineInfo(Map<String, AwardType> lineNameToAwardType, int roundOne, int roundTwo,
      int longCall) {
    this.lineNameToAwardType = lineNameToAwardType;
    this.roundOne = roundOne;
    this.roundTwo = roundTwo;
    this.longCall = longCall;
  }

  public int getNum(AwardType awardType) {
    switch (awardType) {
      case ROUND1:
        return roundOne;
      case ROUND2:
        return roundTwo;
      case LCR:
        return longCall;
      case SCR:
        return 0;
    }
    throw new IllegalStateException("Handle awardType: " + awardType);
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

  public AwardType getAwardType(String lineName) {
    return lineNameToAwardType.get(lineName);
  }

  public LineInfo increment(int factor) {
    return new LineInfo(lineNameToAwardType, roundOne + factor, roundTwo, longCall);
  }

  @Override
  public int hashCode() {
    return Objects.hash(lineNameToAwardType, roundOne, roundTwo, longCall);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof LineInfo)) {
      return false;
    }
    LineInfo that = (LineInfo) o;
    return this.lineNameToAwardType.equals(that.lineNameToAwardType)
        && this.roundOne == that.roundOne
        && this.roundTwo == that.roundTwo
        && this.longCall == that.longCall;
  }

  @Override
  public String toString() {
    return String.format("1:%d 2:%d lc:%d", roundOne, roundTwo, longCall);
  }
}
