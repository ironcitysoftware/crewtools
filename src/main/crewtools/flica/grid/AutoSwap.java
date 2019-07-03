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

import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.flica.bid.TripDatabase;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.FileUtils;
import crewtools.util.FlicaConfig;
import crewtools.util.SystemClock;

public class AutoSwap {
  private final Logger logger = Logger.getLogger(AutoSwap.class.getName());

  private static final Duration SCHEDULE_LOAD_INTERVAL = Duration.standardMinutes(60);
  private static final Duration GRID_LOAD_INTERVAL = Duration.standardMinutes(15);

  private AwardDomicile fromDomicile;
  private AwardDomicile toDomicile;
  private Rank rank;
  private YearMonth yearMonth;
  private FlicaService service;

  public static void main(String args[]) throws Exception {
    if (args.length == 0) {
      System.err.println("AutoSwap PHL TYS CAPTAIN 2019-06");
      System.exit(-1);
    }
    new AutoSwap(args).run();
  }

  public AutoSwap(String args[]) throws Exception {
    fromDomicile = AwardDomicile.valueOf(args[0]);
    toDomicile = AwardDomicile.valueOf(args[1]);
    rank = Rank.valueOf(args[2]);
    yearMonth = YearMonth.parse(args[3]);
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    // service = new CachingFlicaService(connection);
    service = new FlicaService(connection);
  }

  public void run() throws Exception {
    logger.info(String.format("Welcome to AutoSwap for %s->%s %s %s",
        fromDomicile, toDomicile, rank, yearMonth));
    service.connect();
    BidConfig bidConfig = FileUtils.readBidConfig();
    TripDatabase tripDatabase = new TripDatabase(service);
    Processor processor = new Processor(new SystemClock(), yearMonth,
        service, fromDomicile, toDomicile, bidConfig, tripDatabase);
    processor.start();
    new ScheduleLoaderThread(SCHEDULE_LOAD_INTERVAL, yearMonth, service, processor)
        .start();
    new GridObserverationThread(GRID_LOAD_INTERVAL, yearMonth, service,
        fromDomicile, rank, processor).start();
    new GridObserverationThread(GRID_LOAD_INTERVAL, yearMonth, service,
        toDomicile, rank, processor).start();
  }
}
