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

package crewtools.flica.parser;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import crewtools.flica.Proto.DayOfWeek;
import crewtools.flica.Proto.Trip;
import static org.junit.Assert.assertEquals;

public class ScheduleParserHelperTest {
  @Test
  public void testSingleDay() throws ParseException {
    ScheduleParserHelper helper = new ScheduleParserHelper();
    Trip.Builder builder = Trip.newBuilder();
    helper.parseDayOfWeek("ONLY ON MON", builder);
    assertEquals(builder.build().getDayOfWeekList(),
        ImmutableList.of(DayOfWeek.MONDAY));    
  }
  
  @Test
  public void testSingleDayExcept() throws ParseException {
    ScheduleParserHelper helper = new ScheduleParserHelper();
    Trip.Builder builder = Trip.newBuilder();
    helper.parseDayOfWeek("EXCEPT MON", builder);
    assertEquals(builder.build().getDayOfWeekList(),
        ImmutableList.of(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));    
  }

  @Test
  public void testDayRange() throws ParseException {
    ScheduleParserHelper helper = new ScheduleParserHelper();
    Trip.Builder builder = Trip.newBuilder();
    helper.parseDayOfWeek("WED-THU", builder);
    assertEquals(builder.build().getDayOfWeekList(),
        ImmutableList.of(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY));    
  }
  
  @Test
  public void testDayRangeExcept() throws ParseException {
    ScheduleParserHelper helper = new ScheduleParserHelper();
    Trip.Builder builder = Trip.newBuilder();
    helper.parseDayOfWeek("EXCEPT MON-FRI", builder);
    assertEquals(builder.build().getDayOfWeekList(),
        ImmutableList.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY));    
  }
  
  @Test
  public void testDayList() throws ParseException {
    ScheduleParserHelper helper = new ScheduleParserHelper();
    Trip.Builder builder = Trip.newBuilder();
    helper.parseDayOfWeek("WED THU", builder);
    assertEquals(builder.build().getDayOfWeekList(),
        ImmutableList.of(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY));    
  }
  
  @Test
  public void testDayListExcept() throws ParseException {
    ScheduleParserHelper helper = new ScheduleParserHelper();
    Trip.Builder builder = Trip.newBuilder();
    helper.parseDayOfWeek("EXCEPT MON TUE WED THU FRI", builder);
    assertEquals(builder.build().getDayOfWeekList(),
        ImmutableList.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY));    
  }
  
  @Test
  public void testEveryDay() throws ParseException {
    ScheduleParserHelper helper = new ScheduleParserHelper();
    Trip.Builder builder = Trip.newBuilder();
    helper.parseDayOfWeek("EVERY DAY", builder);
    assertEquals(builder.build().getDayOfWeekList(),
        ImmutableList.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));    
  }
}
