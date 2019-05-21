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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import crewtools.flica.CachingFlicaService;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.flica.pojo.FlicaTask;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;
import crewtools.util.FileUtils;
import crewtools.util.FlicaConfig;
import crewtools.util.SystemClock;

// Runs during SAP or opentime, receives email alerts and swaps trips as necessary.
public class AutoBidder {
  private final Logger logger = Logger.getLogger(AutoBidder.class.getName());

  private final int SMTP_PORT = 25000;

  public static void main(String args[]) throws Exception {
    new AutoBidder().run(args);
  }

  public void run(String args[]) throws Exception {
    run(new AutoBidderCommandLineConfig(args));
  }

  private void run(AutoBidderCommandLineConfig cmdLine) throws Exception {
    BidConfig bidConfig = FileUtils.readBidConfig();
    Rank rank = Rank.valueOf(bidConfig.getRank());
    YearMonth yearMonth = YearMonth.parse(bidConfig.getYearMonth());
    logger.info("Welcome to AutoBidder for " + yearMonth);

    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    FlicaService service;
    if (cmdLine.useCache()) {
      service = new CachingFlicaService(connection);
    } else {
      service = new FlicaService(connection);
      service.connect();
    }

    Clock clock = new SystemClock();
    ScheduleWrapperTree tree = new ScheduleWrapperTree(bidConfig);

    RuntimeStats stats = new RuntimeStats(clock, tree);

    TripDatabase tripDatabase = new TripDatabase(service, cmdLine.getUseProto(), yearMonth);

    StatusService statusService = new StatusService(stats, tripDatabase, bidConfig);
    statusService.start();

    ScheduleLoaderThread scheduleLoaderThread = new ScheduleLoaderThread(
        cmdLine.getScheduleRefreshInterval(), yearMonth,
        tree, tripDatabase, service, bidConfig);
    scheduleLoaderThread.start();
    scheduleLoaderThread.blockCurrentThreadUntilInitialRunCompletes();

    Duration initialDelay = cmdLine.getInitialDelay(clock, rank);

    List<FlicaTask> taskList = new ArrayList<FlicaTask>();
    Worker worker = new Worker(bidConfig, yearMonth, tree, taskList, service,
        cmdLine.getRound(rank), clock, tripDatabase);

    OpentimeLoaderThread opentimeLoader = new OpentimeLoaderThread(
        yearMonth,
        initialDelay,
        cmdLine,
        service,
        tripDatabase,
        taskList,
        stats,
        bidConfig,
        worker);
    opentimeLoader.start();

    /*
     * if (cmdLine.isDebug()) {
     * DebugInjector debugTripProvider = new DebugInjector(queue, tree);
     * debugTripProvider.start();
     * return;
     * }
     * 
     * SMTPServer smtpServer = new SMTPServer(
     * (context) -> {
     * return new FlicaMessageHandler(context, yearMonth, queue, stats);
     * });
     * smtpServer.setPort(SMTP_PORT);
     * logger.info("Listening for SMTP on " + SMTP_PORT);
     * smtpServer.start();
     */
  }
}
