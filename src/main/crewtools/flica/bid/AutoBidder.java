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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.subethamail.smtp.server.SMTPServer;

import com.google.common.collect.ImmutableList;

import crewtools.flica.CachingFlicaService;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Trip;
import crewtools.util.Clock;
import crewtools.util.FlicaConfig;
import crewtools.util.SystemClock;

// Runs during SAP or opentime, receives email alerts and swaps trips as necessary.
public class AutoBidder {
  private final Logger logger = Logger.getLogger(AutoBidder.class.getName());

  private final int SMTP_PORT = 25000;

  private static final List<PairingKey> BAGGAGE_TRIPS = ImmutableList.of();
//      new PairingKey(LocalDate.parse("2018-3-11"), "L2003"),
//      new PairingKey(LocalDate.parse("2018-3-28"), "L2095"));

  public static void main(String args[]) throws Exception {
    new AutoBidder().run(args);
  }

  public void run(String args[]) throws Exception {
    run(new AutoBidderConfig(args));
  }

  private void run(AutoBidderConfig config) throws Exception {
    logger.info("Welcome to AutoBidder for " + config.getYearMonth());
    FlicaConnection connection = new FlicaConnection(new FlicaConfig());
    FlicaService service;
    if (config.useCache()) {
      service = new CachingFlicaService(connection);
    } else {
      service = new FlicaService(connection);
      service.connect();
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        logger.info("Logging out of FLICA");
        service.logout();
        logger.info("Logged out of FLICA");
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Error logging out", e);
      }
    }));

    Clock clock = new SystemClock();
    ScheduleWrapperTree tree = new ScheduleWrapperTree();

    RuntimeStats stats = new RuntimeStats(clock, tree);

    StatusService statusService = new StatusService(stats);
    statusService.start();

    TripDatabase trips = new TripDatabase(service, config.getYearMonth());
    ScheduleLoaderThread scheduleLoaderThread = new ScheduleLoaderThread(
        config.getScheduleRefreshInterval(), config.getYearMonth(), 
        tree, trips, service, BAGGAGE_TRIPS);
    scheduleLoaderThread.start();
    scheduleLoaderThread.blockCurrentThreadUntilInitialRunIsComplete();

    BlockingQueue<Trip> queue = new LinkedBlockingQueue<Trip>();
    Worker worker = new Worker(queue, service, tree, 
        config.getYearMonth(), config.getRound(), clock, stats);
    worker.start();
    
    SMTPServer smtpServer = new SMTPServer(
        (context) -> {
          return new FlicaMessageHandler(context, config.getYearMonth(), queue, stats);
        });
    smtpServer.setPort(SMTP_PORT);
    logger.info("Listening for SMTP on " + SMTP_PORT);
    smtpServer.start();

    Duration initialDelay = config.getInitialDelay(clock);
    
    OpentimeLoaderThread opentimeLoader = new OpentimeLoaderThread(
        initialDelay,
        config,
        service,
        trips,
        queue, stats);
    opentimeLoader.start();
    
    OpentimeRequestLoaderThread opentimeRequestLoader = new OpentimeRequestLoaderThread(
        initialDelay,
        config,
        service,
        tree);
    opentimeRequestLoader.start();
  }
}
