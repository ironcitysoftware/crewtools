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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Trip;

public class OpentimeLoaderThread extends PeriodicDaemonThread {
  private final Logger logger = Logger.getLogger(OpentimeLoaderThread.class.getName());

  private final YearMonth yearMonth;
  private final AutoBidderCommandLineConfig cmdLine;
  private final FlicaService service;
  private final TripDatabase tripDatabase;
  private final BlockingQueue<Trip> queue;
  private final RuntimeStats stats;
  
  public OpentimeLoaderThread(YearMonth yearMonth, Duration initialDelay,
      AutoBidderCommandLineConfig cmdLine, FlicaService service,
      TripDatabase tripDatabase, BlockingQueue<Trip> queue, RuntimeStats stats) {
    super(initialDelay, cmdLine.getOpentimeRefreshInterval());
    this.yearMonth = yearMonth;
    this.cmdLine = cmdLine;
    this.service = service;
    this.tripDatabase = tripDatabase;
    this.queue = queue;
    this.stats = stats;
    this.setName("OpenTimeLoader");
    this.setDaemon(true);
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
      List<PairingKey> trips = getOpentimeTrips(service, yearMonth, cmdLine.getRound());
      if (trips == null) {
        logger.info("Opentime not yet published");
        return WorkResult.INCOMPLETE;
      }
      for (PairingKey tripKey : trips) {
        Trip trip = tripDatabase.getTrip(tripKey);
        logger.info("Adding " + tripKey + " from opentime refresh");
        stats.incrementOpentimeTrip();
        queue.add(trip);
      }
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

  private List<PairingKey> getOpentimeTrips(FlicaService service, YearMonth yearMonth,
      int round) throws URISyntaxException, IOException, ParseException {
    String rawOpenTime = service.getOpenTime(
        AwardDomicile.CLT, Rank.FIRST_OFFICER, round, yearMonth);
    OpenTimeParser openTimeParser = new OpenTimeParser(
        yearMonth.getYear(), rawOpenTime);
    List<FlicaTask> tasks = openTimeParser.parse();
    if (!openTimeParser.isPublished()) {
      return null;
    }
    List<PairingKey> openTimeTrips = new ArrayList<>();
    for (FlicaTask task : tasks) {
      openTimeTrips.add(new PairingKey(task.pairingDate, task.pairingName));
    }
    return openTimeTrips;
  }
}
