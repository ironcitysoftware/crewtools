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
import java.util.ListIterator;

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
    List<LocalDate> dates = getDatesInPeriod();
    ListIterator<LocalDate> iterator = dates.listIterator();
    while (iterator.hasNext()) {
      LocalDate date = iterator.next();
      if (date.isBefore(today)) {
        iterator.remove();
      }
    }
    return dates;
  }

  public List<LocalDate> getDatesInPeriod() {
    LocalDate date = getFirstDateInPeriod();
    LocalDate end = getLastDateInPeriod();
    List<LocalDate> result = new ArrayList<>();
    while (!date.isAfter(end)) {
      result.add(date);
      date = date.plusDays(1);
    }
    return result;
  }

  public LocalDate getFirstDateInPeriod() {
    if (yearMonth.getMonthOfYear() == 2) {
      return new LocalDate(yearMonth.getYear(), 1, 31);
    } else if (yearMonth.getMonthOfYear() == 3) {
      return new LocalDate(yearMonth.getYear(), 3, 2);
    } else {
      return yearMonth.toLocalDate(1);
    }
  }

  public LocalDate getLastDateInPeriod() {
    if (yearMonth.getMonthOfYear() == 1) {
      return new LocalDate(yearMonth.getYear(), 1, 30);
    } else if (yearMonth.getMonthOfYear() == 2) {
      return new LocalDate(yearMonth.getYear(), 3, 1);
    } else {
      return yearMonth.toLocalDate(1).dayOfMonth().withMaximumValue();
    }
  }

  public static YearMonth getAssociatedYearMonth(LocalDate localDate) {
    YearMonth result = new YearMonth(localDate.getYear(), localDate.getMonthOfYear());
    if (localDate.getMonthOfYear() == 1 && localDate.getDayOfMonth() == 31) {
      return result.plusMonths(1);
    } else if (localDate.getMonthOfYear() == 3 && localDate.getDayOfMonth() == 1) {
      return result.minusMonths(1);
    }
    return result;
  }
}
