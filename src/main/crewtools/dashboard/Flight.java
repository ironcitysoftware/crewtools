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
import org.joda.time.Interval;

import crewtools.aa.Proto;

public class Flight {
  private final Proto.Flight flight;

  public Flight(Proto.Flight flight) {
    this.flight = flight;
  }

  public boolean hasActualDepartureTime() {
    return flight.getFlightStatus().getOriginInfo().hasActualTime()
        && !flight.getFlightStatus().getOriginInfo().getActualTime().isEmpty();
  }

  public DateTime getActualDepartureTime() {
    return DateTime.parse(flight.getFlightStatus().getOriginInfo().getActualTime());
  }

  public boolean hasActualArrivalTime() {
    return flight.getFlightStatus().getDestinationInfo().hasActualTime()
        && !flight.getFlightStatus().getDestinationInfo().getActualTime().isEmpty();
  }

  public DateTime getActualArrivalTime() {
    return DateTime.parse(flight.getFlightStatus().getDestinationInfo().getActualTime());
  }

  public DateTime getScheduledDepartureTime() {
    return DateTime.parse(flight.getDepartDate());
  }

  public DateTime getScheduledArrivalTime() {
    // Scheduled arrival seems to be adjusted for actual departure time.
    // Try "un-adjusting".
    DateTime adjustedArrivalTime = DateTime.parse(flight.getArrivalDate());
    if (hasActualDepartureTime()) {
      DateTime scheduledDeparture = getScheduledDepartureTime();
      DateTime actualDeparture = getActualDepartureTime();
      if (actualDeparture.isAfter(scheduledDeparture)) {
        Interval adjustment = new Interval(scheduledDeparture, actualDeparture);
        return adjustedArrivalTime.minus(adjustment.toPeriod());
      }
    }
    return adjustedArrivalTime;
  }
}
