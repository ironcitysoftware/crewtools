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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VisibilityTest {
  @Test
  public void testGreaterThanSixMiles() {
    Visibility unlimited = Visibility.greaterThanSixMiles();
    assertTrue(unlimited.isUnlimited());
    assertFalse(unlimited.hasFeet());
  }

  @Test
  public void testFaaConversion() {
    assertEquals(1600, Visibility.statuteMile("1/4").getFeet());
    assertEquals(4500, Visibility.statuteMile("9/10").getFeet());
    assertEquals(6000, Visibility.statuteMile("1 1/4").getFeet()); // special
    assertEquals(7400, Visibility.statuteMile("1 1/2").getFeet());
    assertEquals(5000 + 4500, Visibility.statuteMile("1 9/10").getFeet());
    assertEquals(10000, Visibility.statuteMile("2").getFeet());
  }

  @Test
  public void testEquals() {
    // sic
    assertFalse(Visibility.statuteMile("1/4").equals(Visibility.rvr(16)));
  }
}
