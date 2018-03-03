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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.YearMonth;

import com.google.common.base.Splitter;

import crewtools.util.Clock;

public class AutoBidderConfig {
  public static final int OPENTIME = 5;
  public static final int FO_SAP = 10;
  public static final int FO_SBB = 4;

  private static final Splitter EQUALS = Splitter.on('=').trimResults();
  
  private final YearMonth yearMonth;
  private final int round;
  private final boolean cache;
  
  public AutoBidderConfig(String args[]) {
    Iterator<String> argIterator = Arrays.asList(args).iterator();
    YearMonth yearMonth = null;
    int round = 0;
    boolean cache = false;
    while (argIterator.hasNext()) {
      List<String> parameter = EQUALS.splitToList(argIterator.next());
      String arg = parameter.get(0);
      String value = parameter.size() == 1 ? "" : parameter.get(1);
      if (arg.equals("month")) {
        yearMonth = YearMonth.parse(value);
      } else if (arg.equals("round")) {
        if (value.equals("SAP")) {
          round = FO_SAP;
        } else if (value.equals("OPENTIME")) {
          round = OPENTIME;
        } else if (value.equals("SBB")) {
          round = FO_SBB;
        } else {
          System.err.println("Round is SAP or OPENTIME");
          System.exit(-1);
        }
      } else if (arg.equals("cache")) {
        cache = true;        
      } else {
        System.err.println("Unrecognized argument " + arg);
        System.exit(-1);
      }
    }
    if (yearMonth == null || round == 0) {
      System.err.println("autobidder month=2018-01 round=SAP|OPENTIME cache");
      System.exit(-1);
    }
    this.yearMonth = yearMonth;
    this.round = round;
    this.cache = cache;
  }

  public Duration getScheduleRefreshInterval() {
    switch (getRound()) {
    case AutoBidderConfig.FO_SAP:
      return Duration.standardMinutes(15);
    case AutoBidderConfig.FO_SBB:
      return Duration.standardHours(12);
    case AutoBidderConfig.OPENTIME:
      return Duration.standardHours(1);
    default:
      throw new IllegalStateException("Define opentime refresh interval for round " + getRound());
    }
  }

  public Duration getOpentimeRequestRefreshInterval() {
    switch (getRound()) {
    case AutoBidderConfig.FO_SAP:
      return Duration.standardMinutes(6);
    case AutoBidderConfig.FO_SBB:
      return Duration.standardHours(12);
    case AutoBidderConfig.OPENTIME:
      return Duration.standardHours(1);
    default:
      throw new IllegalStateException("Define opentime refresh interval for round " + getRound());
    }
  }

  public Duration getOpentimeRefreshInterval() {
    switch (getRound()) {
    case AutoBidderConfig.FO_SAP:
      return Duration.standardMinutes(45);
    case AutoBidderConfig.FO_SBB:
    case AutoBidderConfig.OPENTIME:
      return Duration.standardHours(3);
    default:
      throw new IllegalStateException("Define opentime refresh interval for round " + getRound());
    }
  }

  public Duration getInitialDelay(Clock clock) {
    switch (getRound()) {
    case AutoBidderConfig.FO_SAP:
      DateTime sapOpens = new DateTime().withTimeAtStartOfDay().withDayOfMonth(16).withHourOfDay(19);
      return new Duration(clock.now(), sapOpens);
    case AutoBidderConfig.FO_SBB:
      DateTime sbbOpens = new DateTime().withTimeAtStartOfDay().withDayOfMonth(24).withHourOfDay(17);
      return new Duration(clock.now(), sbbOpens);
    case AutoBidderConfig.OPENTIME:
      // 28th at 5pm until 1st at 5pm.
      DateTime otOpens = new DateTime().withTimeAtStartOfDay().withDayOfMonth(28).withHourOfDay(17);
      // return new Duration(clock.now(), otOpens);
      return Duration.ZERO;
    default:
      return Duration.ZERO;
    }
  }
  
  public YearMonth getYearMonth() {
    return yearMonth;
  }
  
  public int getRound() {
    return round;
  }
  
  public boolean useCache() {
    return cache;
  }
}
