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

import java.util.logging.Logger;

public class MonthlyBidderCommandLineConfig {
  private final Logger logger = Logger.getLogger(MonthlyBidderCommandLineConfig.class.getName());

  private final boolean submitBids;
  private final boolean useCachingService;
  private final boolean parseCanceled;
  private final boolean desirable;
  private final boolean useProto;

  public MonthlyBidderCommandLineConfig(String args[]) {
    boolean submitBids = false;
    boolean useCachingService = false;
    boolean parseCanceled = false;
    boolean desirable = false;
    boolean useProto = false;
    for (String arg : args) {
      if (arg.equals("--submit")) {
        submitBids = true;
      } else if (arg.equals("--cache")) {
        useCachingService = true;
      } else if (arg.equals("--canceled")) {
        parseCanceled = true;
      } else if (arg.equals("--desirable")) {
        desirable = true;
      } else if (arg.equals("--proto")) {
        useProto = true;
      } else {
        System.err.println("Unrecognized argument " + arg);
        System.exit(-1);
      }
    }
    this.submitBids = submitBids;
    this.useCachingService = useCachingService;
    this.parseCanceled = parseCanceled;
    this.desirable = desirable;
    this.useProto = useProto;
    logger.info("Submit bids    (--submit)    : " + submitBids);
    logger.info("Use cache      (--cache)     : " + useCachingService);
    logger.info("Parse canceled (--canceled)  : " + parseCanceled);
    logger.info("Desirable only (--desirable) : " + desirable);
    logger.info("Use proto      (--proto)     : " + useProto);
  }

  public boolean submitBids() {
    return submitBids;
  }

  public boolean useCachingService() {
    return useCachingService;
  }

  public boolean parseCanceled() {
    return parseCanceled;
  }

  public boolean desirableOnly() {
    return desirable;
  }

  public boolean useProto() {
    return useProto;
  }
}
