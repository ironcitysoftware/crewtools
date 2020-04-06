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

package crewtools.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class CompoundFractionTest {
  @Test
  public void testParse() {
    assertEquals("10", CompoundFraction.parse("10").toString());
    assertEquals("1/2", CompoundFraction.parse("1/2").toString());
    assertEquals("10 1/2", CompoundFraction.parse("10 1/2").toString());
  }

  @Test
  public void testEquals() {
    // sic
    assertFalse(CompoundFraction.parse("1/2").equals(CompoundFraction.parse("2/4")));
  }
}
