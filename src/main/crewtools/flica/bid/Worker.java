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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.YearMonth;

import crewtools.flica.FlicaService;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.SwapResponseParser;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;

public class Worker {
  private final Logger logger = Logger.getLogger(Worker.class.getName());

  private final BidConfig bidConfig;
  private final YearMonth yearMonth;
  private final FlicaService service;
  private final Clock clock;
  private final TripDatabase tripDatabase;
  private final Set<Transition> swaps = new HashSet<>();
  private final Collector collector;
  private final ReplayManager replayManager;
  private Duration opentimeRefreshInterval = Duration.standardMinutes(6);
  private final Comparator<Solution> comparator = new MinimizeWorkMaximizeFunComparator();
  private final boolean isDebug;
  private final boolean isNoSwap;

  public Worker(BidConfig bidConfig, YearMonth yearMonth, Collector collector,
      FlicaService service, Clock clock, TripDatabase tripDatabase,
      ReplayManager replayManager, boolean isDebug, boolean isNoSwap) {
    this.bidConfig = bidConfig;
    this.yearMonth = yearMonth;
    this.collector = collector;
    this.service = service;
    this.clock = clock;
    this.tripDatabase = tripDatabase;
    this.replayManager = replayManager;
    this.isDebug = isDebug;
    this.isNoSwap = isNoSwap;
  }

  public Duration getOpentimeRefreshInterval() {
    return opentimeRefreshInterval;
  }

  private static final int MAX_SWAPS_PER_RUN = 10;

  // If true, this parameter means the program was started before the
  // bid period opened.
  public void run(boolean blockUntilBidPeriodOpens)
      throws ParseException, IOException, URISyntaxException {
    collector.beginWork(blockUntilBidPeriodOpens);
    logger.info("------------------ Worker run -----------------------");
    Schedule schedule = collector.getCurrentSchedule();

    Set<FlicaTaskWrapper> tasks = new HashSet<>();
    collector.getCurrentTasks().forEach(t -> tasks.add(new FlicaTaskWrapper(t)));
    Solver solver = new Solver(schedule, tasks, yearMonth,
        bidConfig, tripDatabase, clock);
    List<Solution> solutions = solver.solve();
    Collections.sort(solutions, comparator);
    logger.info("| Found " + solutions.size() + " solutions");
    int count = 0;
    for (Solution solution : solutions) {
      if (count++ > MAX_SWAPS_PER_RUN) {
        break;
      }
      Transition transition = solution.getProposedSchedule().getTransition();
      swap(transition);
    }
  }

  private void swap(Transition transition) {
    if (collector.hasTransition(transition)) {
      logger.info("Ignoring previous run's solution " + transition);
    } else if (swaps.contains(transition)) {
      logger.info("Ignoring previous solution " + transition);
    }
    swaps.add(transition);
    replayManager.recordSwap(transition);
    if (isDebug) {
      logger.info("[debug] ignoring solution " + transition);
    } else if (replayManager.isReplaying()) {
      logger.info("[replay] ignoring solution " + transition);
    } else if (isNoSwap) {
      logger.info("[noswap] ignoring solution " + transition);
    } else {
      swap(transition.getAddKeys(), transition.getDropKeys());
    }
  }

  private boolean swap(List<PairingKey> adds, List<PairingKey> drops) {
    logger.info("SWAP!!!! DROP " + drops + " for " + adds);
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
