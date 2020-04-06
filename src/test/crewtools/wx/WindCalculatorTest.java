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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WindCalculatorTest {
  private final WindCalculator calc = new WindCalculator();

  @Test
  public void testPureHeadwind() {
    Wind wind = new Wind(360, false, 5, null, null, null);
    WindCalculator.Result result = calc.calculateExcludingGusts(wind, 36);
    assertEquals(0, result.getCrosswindVelocity());
    assertEquals(5, result.getHeadwindVelocity());
  }

  @Test
  public void testPureCrosswind() {
    Wind wind = new Wind(270, false, 5, null, null, null);
    WindCalculator.Result result = calc.calculateExcludingGusts(wind, 36);
    assertEquals(5, result.getCrosswindVelocity());
    assertEquals(0, result.getHeadwindVelocity());
  }

  @Test
  public void testPureReciprocalCrosswind() {
    Wind wind = new Wind(90, false, 5, null, null, null);
    WindCalculator.Result result = calc.calculateExcludingGusts(wind, 36);
    assertEquals(5, result.getCrosswindVelocity());
    assertEquals(0, result.getHeadwindVelocity());
  }

  @Test
  public void testPureTailwind() {
    Wind wind = new Wind(180, false, 5, null, null, null);
    WindCalculator.Result result = calc.calculateExcludingGusts(wind, 36);
    assertEquals(0, result.getCrosswindVelocity());
    assertEquals(-5, result.getHeadwindVelocity());
  }

  @Test
  public void testVariableUnconstrained() {
    Wind wind = new Wind(null, true, 5, null, null, null);
    WindCalculator.Result result = calc.calculateExcludingGusts(wind, 36);
    assertEquals(5, result.getCrosswindVelocity());
    assertEquals(-5, result.getHeadwindVelocity());
  }

  /*
   * @Test public void testVariableConstrained() { Wind wind = new Wind(null,
   * true, 5, null, 90, 270); WindCalculator.Result result =
   * calc.calculateExcludingGusts(wind, 36); assertEquals(5,
   * result.getCrosswindVelocity()); assertEquals(-5,
   * result.getHeadwindVelocity()); }
   */
}
