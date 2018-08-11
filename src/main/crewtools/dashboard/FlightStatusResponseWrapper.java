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

import crewtools.aa.Proto;
import crewtools.aa.Proto.FisFlightStatus;
import crewtools.aa.Proto.FlightStatusResponse;
import crewtools.flica.pojo.Leg;

public class FlightStatusResponseWrapper {
  private final FlightStatusResponse response;

  public FlightStatusResponseWrapper(FlightStatusResponse response) {
    this.response = response;
  }

  public Proto.Flight getFlight(Leg leg) {
    if (response == null) {
      return null;
    }
    if (!response.hasFisFlightStatus()) {
      return null;
    }
    FisFlightStatus status = response.getFisFlightStatus();
    if (status.getFlightCount() > 1 && leg == null) {
      return null;
    }
    return status.getFlightCount() == 1
        ? status.getFlight(0)
        : findFlight(leg, status.getFlightList());
  }

  /**
   * FisFlightStatus will have multiple flights if the same flight number
   * is used for the flight both to and from the outstation, for example.
   * Matches origin/destination to select the right one.
   */
  private Proto.Flight findFlight(Leg leg, List<Proto.Flight> flights) {
    for (Proto.Flight flight : flights) {
      if (flight.getOriginAirportCode().equals(leg.getDepartureAirportCode())
          && flight.getDestinationAirportCode().equals(leg.getArrivalAirportCode())) {
        return flight;
      }
    }
    return null;
  }
}
