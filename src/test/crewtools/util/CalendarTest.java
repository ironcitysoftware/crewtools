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

package crewtools.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.junit.Test;

public class CalendarTest {
  private static final YearMonth FEB = new YearMonth(2019, 2);
  private static final YearMonth MAR = new YearMonth(2019, 3);

  @Test
  public void testIsWithinPeriod() {
    Calendar calendar = new Calendar(FEB);
    assertTrue(calendar.isWithinPeriod(new LocalDate(2019, 1, 31)));
    assertFalse(calendar.isWithinPeriod(new LocalDate(2019, 1, 30)));
  }

  @Test
  public void testGetDatesInPeriod() {
    Calendar calendar = new Calendar(MAR);
    List<LocalDate> dates = calendar.getDatesInPeriod();
    assertEquals(new LocalDate(2019, 3, 2), dates.get(0));
    assertEquals(30, dates.size());
    assertEquals(new LocalDate(2019, 3, 31), dates.get(29));
  }

  @Test
  public void testGetRemainingDatesInPeriod() {
    Calendar calendar = new Calendar(MAR);
    List<LocalDate> dates = calendar
        .getRemainingDatesInPeriod(new LocalDate(2019, 3, 29));
    assertEquals(3, dates.size());
    assertEquals(new LocalDate(2019, 3, 29), dates.get(0));
    assertEquals(new LocalDate(2019, 3, 30), dates.get(1));
    assertEquals(new LocalDate(2019, 3, 31), dates.get(2));
  }
}
