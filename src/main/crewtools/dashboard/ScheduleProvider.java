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

package crewtools.dashboard;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTimeZone;
import org.joda.time.YearMonth;

import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.ScheduleParser;
import crewtools.flica.pojo.Schedule;
import crewtools.util.Clock;

public class ScheduleProvider {
  private final Clock clock;
  private final FlicaService flicaService;

  private Map<YearMonth, Schedule> cache = Collections.synchronizedMap(new HashMap<>());

  private static final DateTimeZone EASTERN_TIME_ZONE = DateTimeZone
      .forID("America/New_York");

  public ScheduleProvider(Clock clock, FlicaService flicaService) {
    this.clock = clock;
    this.flicaService = flicaService;
  }

  public Schedule getPreviousMonthSchedule()
      throws IOException, ParseException {
    return getSchedule(getYearMonthEasternTime().minusMonths(1));
  }
  
  public Schedule getCurrentMonthSchedule()
      throws IOException, ParseException {
    return getSchedule(getYearMonthEasternTime());
  }

  public Schedule getNextMonthSchedule()
      throws IOException, ParseException {
    return getSchedule(getYearMonthEasternTime().plusMonths(1));
  }

  private YearMonth getYearMonthEasternTime() {
    return new YearMonth(clock.now().withZone(EASTERN_TIME_ZONE));
  }

  private Schedule getSchedule(YearMonth yearMonth)
      throws IOException, ParseException {
    if (cache.containsKey(yearMonth)) {
      return cache.get(yearMonth);
    }
    String rawSchedule = flicaService.getSchedule(yearMonth);
    ScheduleParser scheduleParser = new ScheduleParser(rawSchedule);
    Proto.Schedule protoSchedule = scheduleParser.parse();
    ScheduleAdapter scheduleAdapter = new ScheduleAdapter();
    Schedule schedule = scheduleAdapter.adapt(protoSchedule);
    cache.put(yearMonth, schedule);
    return schedule;
  }
}
