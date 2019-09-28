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

package crewtools.crewmobile;

import java.util.Iterator;

import crewtools.crewmobile.Proto.CalendarDataFeed;
import crewtools.crewmobile.Proto.CalendarEntry;
import crewtools.crewmobile.Proto.Day;
import crewtools.crewmobile.Proto.Month;

public class CalendarEntryIterator implements Iterator<CalendarEntry> {
  private Iterator<Month> months;
  private Iterator<Day> days;
  private Iterator<CalendarEntry> entries;
  private Month currentMonth;
  private Day currentDay;

  public CalendarEntryIterator(CalendarDataFeed feed) {
    this.months = feed.getMonthList().iterator();
    this.currentMonth = months.next();
    this.days = currentMonth.getDayList().iterator();
    this.currentDay = days.next();
    this.entries = currentDay.getCalendarEntryList().iterator();
  }

  @Override
  public boolean hasNext() {
    return days.hasNext() || months.hasNext() || entries.hasNext();
  }

  @Override
  public CalendarEntry next() {
    CalendarEntry entry;
    do {
      entry = nextEntry();
    } while (!entry.hasFlight() || !entry.getFlight().hasDep());
    return entry;
  }

  private CalendarEntry nextEntry() {
    while (!entries.hasNext()) {
      currentDay = nextDay();
      entries = currentDay.getCalendarEntryList().iterator();
    }
    return entries.next();
  }

  private Day nextDay() {
    while (!days.hasNext()) {
      currentMonth = months.next();
      days = currentMonth.getDayList().iterator();
    }
    return days.next();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}