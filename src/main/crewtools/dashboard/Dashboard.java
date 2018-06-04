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

public class Dashboard {
  private final DateTime retrievedTime;
  private final String prettyRetrievedTime;
  private final FlightInfo currentFlight;
  private final FlightInfo nextFlight;

  Dashboard(DateTime retrievedTime, String prettyRetrievedTime, FlightInfo currentFlight,
      FlightInfo nextFlight) {
    this.retrievedTime = retrievedTime;
    this.prettyRetrievedTime = prettyRetrievedTime;
    this.currentFlight = currentFlight;
    this.nextFlight = nextFlight;
  }

  public DateTime getRetrievedTime() {
    return retrievedTime;
  }

  public String getPrettyRetrievedTime() {
    return prettyRetrievedTime;
  }

  public FlightInfo getCurrentFlight() {
    return currentFlight;
  }

  public FlightInfo getNextFlight() {
    return nextFlight;
  }

  @Override
  public String toString() {
    String result = "At " + prettyRetrievedTime + "\n";
    result += "Current/Previous flight:\n";
    if (currentFlight != null) {
      result += String.format("%3s  ->   %3s\n", currentFlight.getOriginAirport(),
          currentFlight.getDestinationAirport());
      result += String.format("%3s  %3s  %3s\n", currentFlight.getOriginGate(),
          currentFlight.getAircraftType(),
          currentFlight.getDestinationGate());
    }
    result += "---------------------\nNext flight:\n";
    if (nextFlight != null) {
      result += String.format("%3s  ->   %3s\n", nextFlight.getOriginAirport(),
          nextFlight.getDestinationAirport());
      result += String.format("%3s  %3s  %3s\n", nextFlight.getOriginGate(),
          nextFlight.getAircraftType(),
          nextFlight.getDestinationGate());
      result += String.format("AA  show: %s %s\n",
          nextFlight.getTimeInfo().getCompanyShowOffset(),
          nextFlight.getTimeInfo().getCompanyShowZulu());
      // result += String.format("Est show: %s\n", nextFlight.getComputedShow();
    }
    return result;
  }
}
