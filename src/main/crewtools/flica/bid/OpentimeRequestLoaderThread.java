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

import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.flica.parser.OpentimeRequestParser;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.OpentimeRequest;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.PeriodicDaemonThread;

public class OpentimeRequestLoaderThread extends PeriodicDaemonThread {
  private final Logger logger = Logger.getLogger(OpentimeRequestLoaderThread.class.getName());

  private final YearMonth yearMonth;
  private final FlicaService service;
  private final ScheduleWrapperTree tree;
  private final AutoBidderCommandLineConfig cmdLine;
  private final BidConfig config;

  public OpentimeRequestLoaderThread(YearMonth yearMonth,
      Duration initialDelay,
      AutoBidderCommandLineConfig cmdLine,
      FlicaService service,
      ScheduleWrapperTree tree,
      BidConfig config) {
    super(initialDelay, cmdLine.getOpentimeRequestRefreshInterval());
    this.yearMonth = yearMonth;
    this.service = service;
    this.tree = tree;
    this.cmdLine = cmdLine;
    this.config = config;
    setName("OpentimeRequestLoader");
    setDaemon(true);
  }

  @Override
  public WorkResult doPeriodicWork() {
    logger.info("Refreshing opentime requests");
    try {
      String raw = service.getOpentimeRequests(cmdLine.getRound(
          Rank.valueOf(config.getRank())), yearMonth);
      List<OpentimeRequest> requests = new OpentimeRequestParser(raw).parse();
      for (OpentimeRequest request : requests) {
        switch (request.getStatus()) {
          case OpentimeRequest.APPROVED:
            tree.markApproved(request.getTransition());
            break;
          case OpentimeRequest.DENIED:
            tree.markDenied(request.getTransition());
            break;
          case OpentimeRequest.PENDING:
          case OpentimeRequest.PROCESSING:
            break;
          default:
            logger.info("FIXME: handle request status " + request.getStatus());
        }
      }
      return WorkResult.COMPLETE;
    } catch (URISyntaxException | IOException | ParseException e) {
      logger.log(Level.SEVERE, "Error refreshing opentime requests", e);
      return WorkResult.INCOMPLETE;
    }
  }
}
