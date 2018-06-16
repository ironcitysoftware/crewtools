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

package crewtools.dashboard;

import org.joda.time.DateTime;

import crewtools.util.Clock;

public class TimeInfo {
  private final Clock clock;
  private final DateTime companyShow;
  private final DateTime estimatedShow;
  private final DateTime scheduledDeparture;
  private final DateTime actualDeparture;
  private final DateTime scheduledArrival;
  private final DateTime actualArrival;
  private final Formatter formatter = new Formatter();

  public TimeInfo(
      Clock clock,
      DateTime companyShow,
      DateTime estimatedShow,
      DateTime scheduledDeparture,
      DateTime actualDeparture,
      DateTime scheduledArrival,
      DateTime actualArrival) {
    this.clock = clock;
    this.companyShow = companyShow;
    this.estimatedShow = estimatedShow;
    this.scheduledDeparture = scheduledDeparture;
    this.actualDeparture = actualDeparture;
    this.scheduledArrival = scheduledArrival;
    this.actualArrival = actualArrival;
  }

  /** Relative show time from now, as scheduled by the company. */
  public String getCompanyShowOffset() {
    return formatter.getPrettyOffset(companyShow, clock.now());
  }

  /** Absolute show time from now, as scheduled by the company. */
  public String getCompanyShowZulu() {
    return formatter.getZulu(companyShow);
  }

  public boolean hasEstimatedShow() {
    return estimatedShow != null;
  }

  /** Relative show time from now, as estimated from the inbound. */
  public String getEstimatedShowOffset() {
    return formatter.getPrettyOffset(estimatedShow, clock.now());
  }

  /** Absolute show time from now, as estimated from the inbound. */
  public String getEstimatedShowZulu() {
    return formatter.getZulu(estimatedShow);
  }

  public boolean hasDeparture() {
    return actualDeparture != null;
  }

  public String getDepartureOffset() {
    return formatter.getPrettyOffset(scheduledDeparture, actualDeparture);
  }

  public String getDepartureZulu() {
    return formatter.getZulu(actualDeparture);
  }

  public String getScheduledDepartureOffset() {
    return formatter.getPrettyOffset(scheduledDeparture, clock.now());
  }

  public String getScheduledDepartureZulu() {
    return formatter.getZulu(scheduledDeparture);
  }

  public boolean hasArrival() {
    return actualArrival != null;
  }

  public String getArrivalOffset() {
    return formatter.getPrettyOffset(scheduledArrival, actualArrival);
  }

  public String getArrivalZulu() {
    return formatter.getZulu(actualArrival);
  }
}
