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

package crewtools.flica.grid;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Schedule;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.ScheduleParser;
import crewtools.util.PeriodicDaemonThread;

public class ScheduleLoaderThread extends PeriodicDaemonThread {
  private final Logger logger = Logger.getLogger(ScheduleLoaderThread.class.getName());

  private static final Duration NO_INITIAL_DELAY = Duration.ZERO;

  private final YearMonth yearMonth;
  private final FlicaService service;
  private final Observer observer;

  public ScheduleLoaderThread(
      Duration interval,
      YearMonth yearMonth,
      FlicaService service,
      Observer observer) {
    super(NO_INITIAL_DELAY, interval);
    this.yearMonth = yearMonth;
    this.service = service;
    this.observer = observer;
    this.setName("ScheduleLoaderThread");
    this.setDaemon(true);
  }

  @Override
  public WorkResult doPeriodicWork() {
    logger.info("Refreshing schedule");
    try {
      observer.observe(getSchedule(service, yearMonth));
      return WorkResult.COMPLETE;
    } catch (Exception e) {
      logger.log(Level.WARNING, "Unable to refresh schedule", e);
      return WorkResult.INCOMPLETE;
    }
  }

  private final String NO_SCHEDULE_AVAILABLE = "No schedule available.";

  private Schedule getSchedule(FlicaService service, YearMonth yearMonth) throws Exception {
    String rawSchedule = service.getSchedule(yearMonth);
    if (rawSchedule.contains(NO_SCHEDULE_AVAILABLE)) {
      throw new ParseException("No schedule available for " + yearMonth);
    }
    ScheduleParser scheduleParser = new ScheduleParser(rawSchedule);
    return scheduleParser.parse();
  }
}
