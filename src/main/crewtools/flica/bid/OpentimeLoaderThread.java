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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.flica.parser.OpenTimeParser;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.FlicaTask;
import crewtools.rpc.Proto.BidConfig;

public class OpentimeLoaderThread extends PeriodicDaemonThread {
  private final Logger logger = Logger.getLogger(OpentimeLoaderThread.class.getName());

  private final YearMonth yearMonth;
  private final AutoBidderCommandLineConfig cmdLine;
  private final FlicaService service;
  private final TripDatabase tripDatabase;
  private final List<FlicaTask> taskList;
  private final RuntimeStats stats;
  private final BidConfig config;
  private final Worker worker;

  public OpentimeLoaderThread(YearMonth yearMonth, Duration initialDelay,
      AutoBidderCommandLineConfig cmdLine, FlicaService service,
      TripDatabase tripDatabase, List<FlicaTask> taskList, RuntimeStats stats,
      BidConfig config, Worker worker) {
    super(initialDelay, worker.getOpentimeRefreshInterval());
    this.yearMonth = yearMonth;
    this.cmdLine = cmdLine;
    this.service = service;
    this.tripDatabase = tripDatabase;
    this.taskList = taskList;
    this.stats = stats;
    this.config = config;
    this.worker = worker;
    this.setName("OpenTimeLoader");
    this.setDaemon(false);
  }

  @Override
  public void doInitialWork() {
    try {
      service.connect();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error performing initial work", e);
    }
  }

  @Override
  public WorkResult doPeriodicWork() {
    logger.info("Refreshing opentime");
    try {
      List<FlicaTask> trips = getOpentimeTrips(service, yearMonth,
          cmdLine.getRound(Rank.valueOf(config.getRank())));
      if (trips == null) {
        logger.info("Opentime not yet published");
        return WorkResult.INCOMPLETE;
      }
      taskList.addAll(trips);
      worker.run();
      interval = worker.getOpentimeRefreshInterval();
      return WorkResult.COMPLETE;
    } catch (URISyntaxException | IOException | ParseException e) {
      logger.severe(e.getMessage());
      e.printStackTrace();
      logger.log(Level.SEVERE, "Error refreshing opentime", e);
      return WorkResult.INCOMPLETE;
    }
  }

  /** Each work unit will be retried every 10 seconds this many times */
  @Override
  protected int getMaximumNumFailuresBeforeSleeping() {
    return 18;  // 3 minutes
  }

  private List<FlicaTask> getOpentimeTrips(FlicaService service, YearMonth yearMonth,
      int round) throws URISyntaxException, IOException, ParseException {
    String rawOpenTime = service.getOpenTime(
        AwardDomicile.CLT, Rank.CAPTAIN, round, yearMonth);
    OpenTimeParser openTimeParser = new OpenTimeParser(
        yearMonth.getYear(), rawOpenTime);
    List<FlicaTask> tasks = openTimeParser.parse();
    if (!openTimeParser.isPublished()) {
      return null;
    }
    return tasks;
  }
}
