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

import java.util.ArrayList;
import java.util.List;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

public class Calendar {
  private final YearMonth yearMonth;

  public Calendar(YearMonth yearMonth) {
    this.yearMonth = yearMonth;
  }

  public boolean isWithinPeriod(LocalDate localDate) {
    LocalDate begin = getFirstDateInPeriod();
    LocalDate end = getLastDateInPeriod();
    return !localDate.isBefore(begin) && !localDate.isAfter(end);
  }

  public List<LocalDate> getRemainingDatesInPeriod(LocalDate today) {
    LocalDate date = getFirstDateInPeriod();
    LocalDate end = getLastDateInPeriod();
    List<LocalDate> result = new ArrayList<>();
    while (!date.isAfter(end)) {
      if (!date.isBefore(today)) {
        result.add(date);
      }
      date = date.plusDays(1);
    }
    return result;
  }

  private LocalDate getFirstDateInPeriod() {
    if (yearMonth.getMonthOfYear() == 2) {
      return new LocalDate(yearMonth.getYear(), 1, 31);
    } else if (yearMonth.getMonthOfYear() == 3) {
      return new LocalDate(yearMonth.getYear(), 3, 2);
    } else {
      return yearMonth.toLocalDate(1);
    }
  }

  private LocalDate getLastDateInPeriod() {
    if (yearMonth.getMonthOfYear() == 1) {
      return new LocalDate(yearMonth.getYear(), 1, 30);
    } else if (yearMonth.getMonthOfYear() == 2) {
      return new LocalDate(yearMonth.getYear(), 3, 1);
    } else {
      return yearMonth.toLocalDate(1).dayOfMonth().withMaximumValue();
    }
  }
}
