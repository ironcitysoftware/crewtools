/**
 * Copyright 2018 Iron City Software LLC
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

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

public class MonthBoundary {
  private LocalDate begin;
  private LocalDate end;
  private YearMonth yearMonth;

  public MonthBoundary(YearMonth yearMonth) {
    this.yearMonth = yearMonth;
    this.begin = new LocalDate(yearMonth.getYear(), 1, 31);
    this.end = new LocalDate(yearMonth.getYear(), 3, 1);
  }

  public boolean isWithin(LocalDate localDate) {
    if (yearMonth.getMonthOfYear() != 2) {
      return localDate.getYear() == yearMonth.getYear()
          && localDate.getMonthOfYear() == yearMonth.getMonthOfYear();
    } else {
      return !localDate.isBefore(this.begin)
          && !localDate.isAfter(this.end);
    }
  }
}
