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

import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import crewtools.util.Period;

public class MonthlyBidderConfig {
  private final Logger logger = Logger.getLogger(MonthlyBidderConfig.class.getName());

  private final boolean submitBids;
  private final boolean useCachingService;
  private final boolean parseCanceled;
  private final boolean softDaysOff;
  private final boolean useProto;
  private final YearMonth yearMonth;

  public MonthlyBidderConfig(String args[]) {
    boolean submitBids = false;
    boolean useCachingService = false;
    boolean parseCanceled = false;
    boolean softDaysOff = false;
    boolean useProto = false;
    YearMonth yearMonth = null;
    for (String arg : args) {
      if (arg.equals("--submit")) {
        submitBids = true;
      } else if (arg.equals("--cache")) {
        useCachingService = true;
      } else if (arg.equals("--canceled")) {
        parseCanceled = true;
      } else if (arg.equals("--softDaysOff")) {
        softDaysOff = true;
      } else if (arg.equals("--proto")) {
        useProto = true;
      } else if (arg.startsWith("--ym")) {
        yearMonth = YearMonth.parse(arg.split("=")[1]);
      }
    }
    this.submitBids = submitBids;
    this.useCachingService = useCachingService;
    this.parseCanceled = parseCanceled;
    this.softDaysOff = softDaysOff;
    this.yearMonth = Preconditions.checkNotNull(yearMonth, "Need --ym=");
    this.useProto = useProto;
    logger.info("Submit bids    (--submit)     : " + submitBids);
    logger.info("Use cache      (--cache)      : " + useCachingService);
    logger.info("Parse canceled (--canceled)   : " + parseCanceled);
    logger.info("Soft days off  (--softDaysOff): " + softDaysOff);
    logger.info("year month     (--ym=yyyy-mm) : " + yearMonth);
    logger.info("Use proto      (--proto)      : " + useProto);
  }

  // @formatter:off
  public static final Set<Integer> SAP_DAYS_OFF = ImmutableSet.of(
      1);

  public static final Set<Integer> VACATION_DAYS_OFF = ImmutableSet.of(
      );
  // @formatter:on

  public Set<Integer> getSapDaysOff() {
    return SAP_DAYS_OFF;
  }

  public Set<Integer> getVacationDaysOff() {
    return VACATION_DAYS_OFF;
  }

  // maximum?
  public int getMinimumNumberOfTrips() {
    return 3;
  }

  public Period getRequiredCredit() {
    return Period.hours(65);
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

  public boolean softDaysOff() {
    return softDaysOff;
  }

  public boolean useProto() {
    return useProto;
  }

  public YearMonth getYearMonth() {
    return yearMonth;
  }
}
