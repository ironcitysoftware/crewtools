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
import java.util.HashSet;
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
import crewtools.util.PeriodicDaemonThread;

public class OpentimeLoaderThread extends PeriodicDaemonThread {
  private final Logger logger = Logger.getLogger(OpentimeLoaderThread.class.getName());

  private final YearMonth yearMonth;
  private final FlicaService service;
  private final Collector collector;
  private final BidConfig config;
  private final Worker worker;

  public OpentimeLoaderThread(YearMonth yearMonth, Duration initialDelay,
      FlicaService service, Collector collector, BidConfig config, Worker worker) {
    super(initialDelay, worker.getOpentimeRefreshInterval());
    this.yearMonth = yearMonth;
    this.service = service;
    this.collector = collector;
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
          AwardDomicile.valueOf(config.getAwardDomicile()),
          Rank.valueOf(config.getRank()),
          config.getRound());
      if (trips == null) {
        logger.info("Opentime not yet published");
        return WorkResult.INCOMPLETE;
      }
      collector.offer(new HashSet<>(trips));
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
      AwardDomicile domicile, Rank rank, int round) throws URISyntaxException, IOException, ParseException {
    String rawOpenTime = service.getOpenTime(domicile, rank,
        round, yearMonth);
    OpenTimeParser openTimeParser = new OpenTimeParser(
        yearMonth.getYear(), rawOpenTime);
    List<FlicaTask> tasks = openTimeParser.parse();
    if (!openTimeParser.isPublished()) {
      return null;
    }
    return tasks;
  }
}
