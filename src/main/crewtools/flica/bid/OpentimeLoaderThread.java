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

import org.apache.http.client.ClientProtocolException;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.YearMonth;

import com.google.common.io.Files;

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

  private final AutoBidderConfig config;
  private final FlicaService service;
  private final TripDatabase tripDatabase;
  private final BlockingQueue<Trip> queue;
  
  public OpentimeLoaderThread(Duration initialDelay, Duration interval, 
      AutoBidderConfig config, FlicaService service,
      TripDatabase tripDatabase, BlockingQueue<Trip> queue) {
    super(initialDelay, interval);
    this.config = config;
    this.service = service;
    this.tripDatabase = tripDatabase;
    this.queue = queue;
    this.setName("OpenTimeLoader");
    this.setDaemon(true);
  }
  
  @Override
  public void doPeriodicWork() {
    logger.info("Refreshing opentime");
    try {
      List<PairingKey> trips = getOpentimeTrips(service, config.getYearMonth(), config.getRound());
      for (PairingKey tripKey : trips) {
        Trip trip = tripDatabase.getTrip(tripKey);
        logger.info("Adding " + tripKey + " from opentime refresh");
        queue.add(trip);
      }
    } catch (URISyntaxException | IOException | ParseException e) {
      logger.severe(e.getMessage());
      e.printStackTrace();
      logger.log(Level.SEVERE, "Error refreshing opentime", e);
    }
  }
  
  private List<PairingKey> getOpentimeTrips(FlicaService service, YearMonth yearMonth,
      int round) throws ClientProtocolException, URISyntaxException, IOException, ParseException {
    String rawOpenTime = service.getOpenTime(
        AwardDomicile.CLT, Rank.FIRST_OFFICER, round, yearMonth);
    OpenTimeParser openTimeParser = new OpenTimeParser(
        yearMonth.getYear(), rawOpenTime);
    List<FlicaTask> tasks = openTimeParser.parse();
    List<PairingKey> openTimeTrips = new ArrayList<>();
    for (FlicaTask task : tasks) {
      openTimeTrips.add(new PairingKey(task.pairingDate, task.pairingName));
    }
    return openTimeTrips;
  }
}
