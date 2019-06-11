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

package crewtools.flica.bid;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import com.google.common.base.Joiner;

import crewtools.flica.FlicaService;
import crewtools.flica.parser.SwapResponseParser;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;

public class Worker implements Runnable {
  private final Logger logger = Logger.getLogger(Worker.class.getName());

  private final BidConfig bidConfig;
  private final YearMonth yearMonth;
  private final FlicaService service;
  private final Clock clock;
  private final TripDatabase tripDatabase;
  private final List<String> swaps = new ArrayList<>();
  private final Collector collector;
  private Duration opentimeRefreshInterval = Duration.standardMinutes(6);
  private final Comparator<Solution> comparator = new MinimizeWorkMaximizeFunComparator();
  private final boolean isDebug;

  public Worker(BidConfig bidConfig, YearMonth yearMonth, Collector collector,
      FlicaService service, Clock clock, TripDatabase tripDatabase, boolean isDebug) {
    this.bidConfig = bidConfig;
    this.yearMonth = yearMonth;
    this.collector = collector;
    this.service = service;
    this.clock = clock;
    this.tripDatabase = tripDatabase;
    this.isDebug = isDebug;
  }

  public Duration getOpentimeRefreshInterval() {
    return opentimeRefreshInterval;
  }

  private static final int MAX_SWAPS_PER_RUN = 10;

  @Override
  public void run() {
    collector.beginWork();
    logger.info("------------------ Worker run -----------------------");
    Schedule schedule = collector.getCurrentSchedule();

    Solver solver = new Solver(schedule, collector.getCurrentTasks(), yearMonth,
        bidConfig, tripDatabase, clock);
    List<Solution> solutions = solver.solve();
    Collections.sort(solutions, comparator);
    logger.info("| Found " + solutions.size() + " solutions");
    int count = 0;
    for (Solution solution : solutions) {
      if (count++ > MAX_SWAPS_PER_RUN) {
        break;
      }
      swap(solution.getProposedSchedule().getTransition());
    }
  }

  private boolean swap(Transition transition) {
    return swap(transition.getAddKeys(), transition.getDropKeys());
  }

  private boolean swap(List<PairingKey> adds, List<PairingKey> drops) {
    String addStr = Joiner.on(",").join(adds);
    String dropStr = Joiner.on(",").join(drops);
    String key = dropStr + "->" + addStr;
    if (swaps.contains(key)) {
      return false;
    }
    swaps.add(key);
    logger.info("SWAP!!!! DROP " + drops + " for " + adds);
    if (isDebug) {
      logger.info("Not actually swapping due to debug mode");
      return true;
    }
    try {
      String html = service.submitSwap(bidConfig.getRound(), yearMonth, clock.today(), adds,
          drops);
      logger.info("Result from SWAP: " + html);
      SwapResponseParser swapResponseParser = new SwapResponseParser(html);
      if (swapResponseParser.parse() == SwapResponseParser.Status.DUPLICATE) {
        logger.info("Ignoring duplicate swap request");
        return false;
      }
      return true;
    } catch (IOException | URISyntaxException ioe) {
      logger.log(Level.INFO, "Error swapping", ioe);
    }
    return false;
  }
}
