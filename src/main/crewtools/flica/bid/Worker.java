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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import crewtools.flica.FlicaService;
import crewtools.flica.parser.SwapResponseParser;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;

public class Worker extends Thread {
  private static final int MAX_SWAPS = 50;

  private final Logger logger = Logger.getLogger(Worker.class.getName());
  private final BlockingQueue<Trip> queue;
  private final FlicaService service;
  private final ScheduleWrapperTree tree;
  private final YearMonth yearMonth;
  private final int round;
  private final Clock clock;
  private final RuntimeStats stats;
  private final BidConfig bidConfig;
  private final boolean debug;
  private int numSwaps;

  public Worker(BlockingQueue<Trip> queue, FlicaService service,
      ScheduleWrapperTree tree, YearMonth yearMonth,
      int round, Clock clock, RuntimeStats stats, BidConfig bidConfig,
      boolean debug) {
    this.queue = queue;
    this.service = service;
    this.tree = tree;
    this.yearMonth = yearMonth;
    this.round = round;
    this.clock = clock;
    this.stats = stats;
    this.bidConfig = bidConfig;
    this.debug = debug;
    this.numSwaps = 0;
    this.setName("Autobidder Worker");
    this.setDaemon(false);
  }

  public void run() {
    try {
      while (true) {
        doWork();
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  void doWork() {
    try {
      Trip trip = queue.take();
      if (trip.getFirstSection().date.getMonthOfYear() < yearMonth.getMonthOfYear()) {
        logger.info("Ignoring " + trip.getPairingKey() + " from previous month");
        return;
      }
      if (tree.shouldProcess(trip.getPairingKey())) {
        logger.info("Considering " + trip.getPairingKey());
        tree.visit(new TripProcessor(tree, trip));
        tree.markProcessed(trip.getPairingKey());
      }
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "Error processing", e);
    }
  }

  private class TripProcessor extends ScheduleWrapperTree.Visitor {
    private final Trip trip;

    TripProcessor(ScheduleWrapperTree tree, Trip trip) {
      tree.super();
      this.trip = trip;
    }

    public void visit(ScheduleWrapper wrapper) {
      if (!wrapper.meetsMinimumCredit(trip, yearMonth)) {
        logger.info("Discarding " + trip.getPairingName() + " due to credit");
        return;
      }

      ScheduleWrapper.OverlapEvaluation overlap = wrapper.evaluateOverlap(trip);
      if (overlap.overlapsUndroppable) {
        logger.info("Discarding " + trip.getPairingName()
            + " due to overlap with an undroppable event or required day off");
        return;
      }

      if (!overlap.noOverlap && overlap.droppable.size() > 1) {
        logger.info("Discarding " + trip.getPairingName() + " because it overlaps with multiple existing trips, "
            + "and we don't know how to handle that yet.");
        return;
      }

      for (Trip scheduledTrip : overlap.droppable) {
        if (trip.equals(scheduledTrip)) {
          continue;
        }
        if (!wrapper.meetsMinimumCredit(scheduledTrip.getPairingKey(), trip, yearMonth)) {
          logger.info("Unable to swap with scheduled trip "
              + scheduledTrip.getPairingName() + " due to MinCredit");
          continue;
        }
        TripScore potentialNewTrip = new TripScore(trip, bidConfig);
        TripScore existingTrip = new TripScore(scheduledTrip, bidConfig);
        boolean newTripIsBetter = potentialNewTrip.compareTo(existingTrip) > 0;
        boolean scheduleHasBaggage = !wrapper.getBaggage().isEmpty();
        logger.info("Trip " + trip.getPairingName() + ": better=" + newTripIsBetter
            + "; scheduleHasBaggage=" + scheduleHasBaggage
            + " (" + wrapper.getBaggage() + ")");
        if (newTripIsBetter ||
            (bidConfig.getDiscardBaggageRegardlessOfScore()
                && scheduleHasBaggage)) {
          logger.info("Trip " + trip.getPairingName()
          + " (" + potentialNewTrip.getPoints() + ") is better than "
          + scheduledTrip.getPairingName() + " (" + existingTrip.getPoints() + ")");
          logger.info("SWAP!!!! DROP " + scheduledTrip.getPairingName()
              + " and any baggage " + wrapper.getBaggage() + " FOR "
              + trip.getPairingName());
          List<PairingKey> adds = ImmutableList.of(trip.getPairingKey());

          List<PairingKey> drops = ImmutableList.<PairingKey>builder()
              .addAll(wrapper.getBaggage())
              .add(scheduledTrip.getPairingKey())
              .build();

          Transition transition = new Transition(adds, drops);

          try {
            if (!debug) {
              String html = service.submitSwap(round, yearMonth, clock.today(), adds,
                  drops);
              logger.info("Result from SWAP " + numSwaps + ": " + html);
              SwapResponseParser swapResponseParser = new SwapResponseParser(html);
              if (swapResponseParser.parse() == SwapResponseParser.Status.DUPLICATE) {
                logger.info("Ignoring duplicate swap request");
                return;
              }
              logger.info("Swap response parsed.");
            }
            stats.recordSwap(transition.toString());
            add(wrapper, transition, wrapper.mutate(ImmutableList.of(trip), drops));
            numSwaps++;
          } catch (Throwable e) {
            e.printStackTrace();
            logger.warning(e.toString());
            logger.log(Level.WARNING, "Swap error", e);
          }
          Preconditions.checkState(numSwaps < MAX_SWAPS, "Too many swaps.");
        }
      }
    }
  }
}
