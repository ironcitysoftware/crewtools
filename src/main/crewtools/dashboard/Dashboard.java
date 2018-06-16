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

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Minutes;

import crewtools.util.Clock;

public class Dashboard {
  private final Formatter formatter = new Formatter();
  private final Clock clock;
  private final DateTime retrievedTime;
  private final FlightInfo currentFlight;
  private final FlightInfo nextFlight;
  private final List<FlightInfo> flights;

  Dashboard(Clock clock, FlightInfo currentFlight,
      FlightInfo nextFlight) {
    this.clock = clock;
    this.retrievedTime = clock.now();
    this.currentFlight = currentFlight;
    this.nextFlight = nextFlight;
    this.flights = new ArrayList<>();
    if (currentFlight != null) {
      flights.add(currentFlight);
    }
    if (nextFlight != null) {
      flights.add(nextFlight);
    }
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

  public FlightInfo getCurrentFlight() {
    return currentFlight;
  }

  public FlightInfo getNextFlight() {
    return nextFlight;
  }

  public List<FlightInfo> getFlights() {
    return flights;
  }

  @Override
  public String toString() {
    String result = "At " + getPrettyRetrievedTime() + "\n";
    result += "Current/Previous flight:\n";
    if (currentFlight != null) {
      result += currentFlight.getFlightNumber();
      if (currentFlight.isCanceled()) {
        result += " : CANCELLED";
      }
      result += "\n";
      result += String.format("%3s  ->   %3s\n", currentFlight.getOriginAirport(),
          currentFlight.getDestinationAirport());
      result += String.format("%3s  %3s  %3s\n", currentFlight.getOriginGate(),
          currentFlight.getAircraftType(),
          currentFlight.getDestinationGate());
      if (currentFlight.getTimeInfo().hasDeparture()) {
        result += String.format("Depart %s %s\n",
            currentFlight.getTimeInfo().getDepartureOffset(),
            currentFlight.getTimeInfo().getDepartureZulu());
      }
      if (currentFlight.getTimeInfo().hasArrival()) {
        result += String.format("Arrive %s %s\n",
            currentFlight.getTimeInfo().getArrivalOffset(),
            currentFlight.getTimeInfo().getArrivalZulu());
      }
    }
    result += "---------------------\nNext flight:\n";
    if (nextFlight != null) {
      result += nextFlight.getFlightNumber();
      if (nextFlight.isCanceled()) {
        result += " : CANCELLED";
      }
      result += "\n";
      result += String.format("%3s  ->   %3s\n", nextFlight.getOriginAirport(),
          nextFlight.getDestinationAirport());
      result += String.format("%3s  %3s  %3s\n", nextFlight.getOriginGate(),
          nextFlight.getAircraftType(),
          nextFlight.getDestinationGate());
      result += String.format("AA  show: %s %s\n",
          nextFlight.getTimeInfo().getCompanyShowOffset(),
          nextFlight.getTimeInfo().getCompanyShowZulu());
      if (nextFlight.getTimeInfo().hasEstimatedShow()) {
        result += String.format("Est show: %s\n",
            nextFlight.getTimeInfo().getEstimatedShowOffset(),
            nextFlight.getTimeInfo().getEstimatedShowZulu());
      }
      if (nextFlight.getTimeInfo().hasDeparture()) {
        result += String.format("Departed %s %s\n",
            nextFlight.getTimeInfo().getDepartureOffset(),
            nextFlight.getTimeInfo().getDepartureZulu());
      } else {
        result += String.format("Departs %s %s\n",
            nextFlight.getTimeInfo().getScheduledDepartureOffset(),
            nextFlight.getTimeInfo().getScheduledDepartureZulu());
      }
    }
    return result;
  }
}
