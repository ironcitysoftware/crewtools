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

import com.google.common.base.Splitter;

import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.util.Clock;

public class AutoBidderCommandLineConfig {
  private enum Mode {
    OPENTIME,
    SAP,
    SBB
  }

  private static final Splitter EQUALS = Splitter.on('=').trimResults();

  private final Mode mode;
  private final boolean cache;
  private final boolean useProto;
  private final boolean debug;

  public AutoBidderCommandLineConfig(String args[]) {
    Iterator<String> argIterator = Arrays.asList(args).iterator();
    Mode mode = null;
    boolean cache = false;
    boolean useProto = false;
    boolean debug = false;
    while (argIterator.hasNext()) {
      List<String> parameter = EQUALS.splitToList(argIterator.next());
      String arg = parameter.get(0);
      String value = parameter.size() == 1 ? "" : parameter.get(1);
      if (arg.equals("round")) {
        if (value.equals("SAP")) {
          mode = Mode.SAP;
        } else if (value.equals("OPENTIME")) {
          mode = Mode.OPENTIME;
        } else if (value.equals("SBB")) {
          mode = Mode.SBB;
        } else {
          System.err.println("Round is SAP or OPENTIME or SBB");
          System.exit(-1);
        }
      } else if (arg.equals("cache")) {
        cache = true;
      } else if (arg.equals("proto")) {
        useProto = true;
      } else if (arg.equals("debug")) {
        debug = true;
      } else {
        System.err.println("Unrecognized argument " + arg);
        System.exit(-1);
      }
    }
    if (mode == null) {
      System.err.println("autobidder round=SAP|OPENTIME|SBB cache|proto|debug");
      System.exit(-1);
    }
    this.mode = mode;
    this.cache = cache;
    this.useProto = useProto;
    this.debug = debug;
  }

  public Duration getScheduleRefreshInterval() {
    switch (mode) {
      case SAP:
        return Duration.standardMinutes(15);
      case SBB:
        return Duration.standardHours(12);
      case OPENTIME:
        return Duration.standardHours(1);
    default:
        throw new IllegalStateException(
            "Define opentime refresh interval for mode " + mode);
    }
  }

  public Duration getOpentimeRequestRefreshInterval() {
    switch (mode) {
      case SAP:
        return Duration.standardMinutes(6);
      case SBB:
        return Duration.standardHours(12);
      case OPENTIME:
        return Duration.standardHours(1);
    default:
        throw new IllegalStateException(
            "Define opentime request refresh interval for mode " + mode);
    }
  }

  public Duration getOpentimeRefreshInterval() {
    switch (mode) {
      case SAP:
        return Duration.standardMinutes(45);
      case SBB:
      case OPENTIME:
        return Duration.standardHours(3);
    default:
        throw new IllegalStateException(
            "Define opentime refresh interval for mode " + mode);
    }
  }

  public Duration getInitialDelay(Clock clock, Rank rank) {
    switch (mode) {
      case SAP: {
        DateTime biddingStartTime;
        if (rank == Rank.CAPTAIN) {
          biddingStartTime = new DateTime().withTimeAtStartOfDay().withDayOfMonth(14)
              .withHourOfDay(17);
        } else if (rank == Rank.FIRST_OFFICER) {
          biddingStartTime = new DateTime().withTimeAtStartOfDay().withDayOfMonth(16)
              .withHourOfDay(19);
        } else {
          throw new IllegalStateException("Rank " + rank + "?");
        }
        return new Duration(clock.now(), biddingStartTime);
      }
      case SBB: {
        DateTime biddingStartTime = new DateTime().withTimeAtStartOfDay()
            .withDayOfMonth(24).withHourOfDay(17);
        return new Duration(clock.now(), biddingStartTime);
      }
      case OPENTIME:
        // 28th at 5pm until 1st at 5pm.
        DateTime otOpens = new DateTime().withTimeAtStartOfDay().withDayOfMonth(28)
            .withHourOfDay(17);
        // return new Duration(clock.now(), otOpens);
        return Duration.ZERO;
      default:
        return Duration.ZERO;
    }
  }

  public int getRound(Rank rank) {
    if (rank == Rank.CAPTAIN) {
      switch (mode) {
        // case SAP: return FlicaService.BID_SAP;
        // case SBB: return FlicaService.BID_SENIORITY_BASED;
        // case OPENTIME: return FlicaService.BID_OPENTIME;
      }
    } else if (rank == Rank.FIRST_OFFICER) {
      switch (mode) {
        case SAP:
          return FlicaService.BID_SAP;
        case SBB:
          return FlicaService.BID_SENIORITY_BASED;
        case OPENTIME:
          return FlicaService.BID_OPENTIME;
      }
    }
    throw new IllegalStateException("unprepared for mode " + mode + ", rank " + rank);
  }

  public boolean useCache() {
    return cache;
  }

  public boolean getUseProto() {
    return useProto;
  }

  public boolean isDebug() {
    return debug;
  }
}
