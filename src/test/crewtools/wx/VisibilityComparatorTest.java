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

public class VisibilityComparatorTest {
  private VisibilityComparator comparator = new VisibilityComparator();

  @Test
  public void testUnlimited() {
    Visibility unlimited = Visibility.greaterThanSixMiles();
    assertEquals(0, comparator.compare(unlimited, unlimited));
    Visibility limited = Visibility.statuteMile("1");
    assertEquals(1, comparator.compare(unlimited, limited));
    assertEquals(-1, comparator.compare(limited, unlimited));
  }

  @Test
  public void testEquivalence() {
    assertEquals(0,
        comparator.compare(Visibility.rvr(16), Visibility.statuteMile("1/4")));
    assertEquals(1,
        comparator.compare(Visibility.rvr(17), Visibility.statuteMile("1/4")));
  }
}
