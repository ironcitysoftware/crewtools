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

import org.joda.time.DateTime;
import org.joda.time.Duration;

import crewtools.flica.FlicaService;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Clock;

public class Referee {
  private final BidConfig bidConfig;

  public Referee(BidConfig bidConfig) {
    this.bidConfig = bidConfig;
  }

  public Duration getScheduleRefreshInterval() {
    switch (bidConfig.getRound()) {
      case FlicaService.BID_CA_SAP:
      case FlicaService.BID_FO_SAP:
        return Duration.standardMinutes(15);
      case FlicaService.BID_CA_SBB:
        return Duration.standardHours(12);
      case FlicaService.BID_FIRST_COME:
        return Duration.standardHours(1);
      default:
        throw new IllegalStateException(
            "Define opentime refresh interval for round " + bidConfig.getRound());
    }
  }

  public Duration getOpentimeRequestRefreshInterval() {
    switch (bidConfig.getRound()) {
      case FlicaService.BID_CA_SAP:
      case FlicaService.BID_FO_SAP:
        return Duration.standardMinutes(6);
      case FlicaService.BID_CA_SBB:
        return Duration.standardHours(12);
      case FlicaService.BID_FIRST_COME:
        return Duration.standardHours(1);
      default:
        throw new IllegalStateException(
            "Define opentime request refresh interval for round " + bidConfig.getRound());
    }
  }

  public Duration getOpentimeRefreshInterval() {
    switch (bidConfig.getRound()) {
      case FlicaService.BID_CA_SAP:
      case FlicaService.BID_FO_SAP:
        return Duration.standardMinutes(2);
      case FlicaService.BID_CA_SBB:
      case FlicaService.BID_FIRST_COME:
        return Duration.standardHours(3);
      default:
        throw new IllegalStateException(
            "Define opentime refresh interval for round " + bidConfig.getRound());
    }
  }

  public Duration getInitialDelay(Clock clock) {
    switch (bidConfig.getRound()) {
      case FlicaService.BID_CA_SAP: {
        DateTime biddingStartTime = new DateTime().withTimeAtStartOfDay()
            .withDayOfMonth(14)
            .withHourOfDay(17);
        return new Duration(clock.now(), biddingStartTime);
      }
      case FlicaService.BID_FO_SAP: {
        DateTime biddingStartTime = new DateTime().withTimeAtStartOfDay()
            .withDayOfMonth(16)
            .withHourOfDay(19);
        return new Duration(clock.now(), biddingStartTime);
      }
      case FlicaService.BID_CA_SBB: {
        DateTime biddingStartTime = new DateTime().withTimeAtStartOfDay()
            .withDayOfMonth(24).withHourOfDay(17);
        return new Duration(clock.now(), biddingStartTime);
      }
      case FlicaService.BID_FIRST_COME:
        // 28th at 5pm until 1st at 5pm.
        DateTime otOpens = new DateTime().withTimeAtStartOfDay().withDayOfMonth(28)
            .withHourOfDay(17);
        // return new Duration(clock.now(), otOpens);
        return Duration.ZERO;
      default:
        return Duration.ZERO;
    }
  }
}
