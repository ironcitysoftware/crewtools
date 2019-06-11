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

package crewtools.flica.bid;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.ScheduleParser;
import crewtools.flica.pojo.Schedule;
import crewtools.util.FileUtils;
import crewtools.util.PeriodicDaemonThread;

public class ScheduleLoaderThread extends PeriodicDaemonThread {
  private final Logger logger = Logger.getLogger(ScheduleLoaderThread.class.getName());

  private final YearMonth yearMonth;
  private final Collector collector;
  private final TripDatabase tripDatabase;
  private final FlicaService service;

  public ScheduleLoaderThread(Duration interval, YearMonth yearMonth,
      Collector collector, TripDatabase tripDatabase, FlicaService service) {
    super(Duration.ZERO, interval);
    this.yearMonth = yearMonth;
    this.collector = collector;
    this.tripDatabase = tripDatabase;
    this.service = service;
    this.setName("ScheduleLoader");
    this.setDaemon(true);
  }

  @Override
  public WorkResult doPeriodicWork() {
    logger.info("Refreshing schedule");
    try {
      Schedule schedule = getSchedule(service, yearMonth);
      tripDatabase.addTripsFromSchedule(schedule);
      collector.offer(schedule);
      return WorkResult.COMPLETE;
    } catch (Exception e) {
      if (shouldDebug(e)) {
        e.printStackTrace();
        logger.log(Level.WARNING, "Unable to refresh schedule", e);
      }
      logger.warning(e.toString());
      return WorkResult.INCOMPLETE;
    }
  }

  private boolean shouldDebug(Exception e) {
    if (e.getMessage() == null) {
      return true;
    }
    return !e.getMessage().startsWith("No schedule available for ");
  }

  private final String NO_SCHEDULE_AVAILABLE = "No schedule available.";

  private Schedule getSchedule(FlicaService service, YearMonth yearMonth) throws Exception {
    String rawSchedule = null;
    try {
      rawSchedule = service.getSchedule(yearMonth);
      if (rawSchedule.contains(NO_SCHEDULE_AVAILABLE)) {
        throw new ParseException("No schedule available for " + yearMonth);
      }
      ScheduleParser scheduleParser = new ScheduleParser(rawSchedule);
      Proto.Schedule protoSchedule = scheduleParser.parse();
      ScheduleAdapter scheduleAdapter = new ScheduleAdapter();
      return scheduleAdapter.adapt(protoSchedule);
    } catch (Exception e) {
      if (shouldDebug(e)) {
        logger.log(Level.WARNING, "getSchedule", e);
        FileUtils.writeDebugFile("getSchedule", rawSchedule);
      }
      throw e;
    }
  }
}
