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

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Minutes;

import crewtools.util.Clock;

public class Dashboard {
  private final Formatter formatter = new Formatter();
  private final Clock clock;
  private final DateTime retrievedTime;
  private final List<FlightInfo> flights;
  private final int currentOrNextFlightIndex;

  Dashboard(Clock clock, List<FlightInfo> flightInfos, int currentOrNextFlightIndex) {
    this.clock = clock;
    this.retrievedTime = clock.now();
    this.flights = flightInfos;
    this.currentOrNextFlightIndex = currentOrNextFlightIndex;
  }

  public DateTime getRetrievedTime() {
    return retrievedTime;
  }

  public String getPrettyRetrievedTime() {
    if (Minutes.minutesBetween(retrievedTime, clock.now()).getMinutes() > 0) {
      return String.format("%s (aged %s)",
          formatter.getZulu(retrievedTime),
          formatter.getPrettyOffset(retrievedTime, clock.now()));
    } else {
      return formatter.getZulu(retrievedTime);
    }
  }

  public int getCurrentFlightIndex() {
    return currentOrNextFlightIndex;
  }

  public List<FlightInfo> getFlights() {
    return flights;
  }

  @Override
  public String toString() {
    String result = "At " + getPrettyRetrievedTime() + "\n";

    for (int i = 0; i < flights.size(); ++i) {
      FlightInfo flight = flights.get(i);
      if (i == currentOrNextFlightIndex) {
        result += "***************** Current/Next Flight:\n";
      } else {
        result += "Flight:\n";
      }
      result += toString(flight, i == currentOrNextFlightIndex);
      if (i < flights.size() - 1) {
        result += "--------------------------";
      }
    }
    return result;
  }

  private String toString(FlightInfo flight, boolean isCurrentOrNext) {
    String result = flight.getFlightNumber();
    if (flight.isCanceled()) {
      result += " : CANCELLED";
    }
    result += "\n";
    result += String.format("%3s  ->   %3s\n", flight.getOriginAirport(),
        flight.getDestinationAirport());
    result += String.format("%3s  %3s  %3s\n", flight.getOriginGate(),
        flight.getAircraftType(),
        flight.getDestinationGate());

    if (isCurrentOrNext && !flight.getTimeInfo().hasActualArrival()) {
      result += String.format("Company show: %s %s\n",
          flight.getTimeInfo().getCompanyShowOffset(),
          flight.getTimeInfo().getCompanyShowZulu());
      if (flight.getTimeInfo().hasEstimatedShow()) {
        result += String.format("Inbound arrv: %s\n",
            flight.getTimeInfo().getEstimatedShowOffset(),
            flight.getTimeInfo().getEstimatedShowZulu());
      }
    }

    if (flight.getTimeInfo().hasActualDeparture()) {
      result += String.format("Departed %s %s\n",
          flight.getTimeInfo().getDepartureOffset(),
          flight.getTimeInfo().getDepartureZulu());
    } else {
      result += String.format("Departs %s%s\n",
          isCurrentOrNext ? flight.getTimeInfo().getScheduledDepartureOffset() + " " : "",
          flight.getTimeInfo().getScheduledDepartureZulu());
    }
    if (flight.getTimeInfo().hasActualArrival()) {
      result += String.format("Arrived %s %s\n",
          flight.getTimeInfo().getArrivalOffset(),
          flight.getTimeInfo().getArrivalZulu());
    } else {
      result += String.format("Arrives %s%s\n",
          isCurrentOrNext ? flight.getTimeInfo().getScheduledArrivalOffset() + " " : "",
          flight.getTimeInfo().getScheduledArrivalZulu());
    }
    return result;
  }
}
