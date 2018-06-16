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
import org.joda.time.Hours;
import org.joda.time.Minutes;

import crewtools.aa.Proto;
import crewtools.aa.Proto.AirportInfo;
import crewtools.aa.Proto.FisFlightStatus;
import crewtools.aa.Proto.FlightStatusResponse;
import crewtools.aa.Proto.PriorLegFlightInfo;
import crewtools.flica.pojo.Leg;
import crewtools.util.Clock;

public class DashboardAdaptor {
  private static final Minutes SHOW_TIME_PRIOR_TO_DEPARTURE_MINUTES = Minutes.minutes(45);
  private static final Hours PREVIOUS_FLIGHT_EXPIRY = Hours.hours(6);

  public Dashboard adapt(
      Clock clock,
      Leg currentLeg,
      FlightStatusResponse currentFlightStatusResponse,
      Leg nextLeg,
      FlightStatusResponse nextFlightStatusResponse) {
    Proto.Flight currentFlight = getFlight(currentLeg, currentFlightStatusResponse);
    Flight currentFlightPojo = new Flight(currentFlight);
    if (currentFlightPojo.hasActualArrivalTime()
        && currentFlightPojo.getActualArrivalTime()
            .isBefore(clock.now().minus(PREVIOUS_FLIGHT_EXPIRY))) {
      // Previous flight is old enough that we don't care about it.
      currentLeg = null;
      currentFlightStatusResponse = null;
      currentFlight = null;
    }
    Proto.Flight nextFlight = getFlight(nextLeg, nextFlightStatusResponse);
    TimeInfo currentFlightTimeInfo = buildCurrentFlightTimeInfo(clock, currentLeg,
        currentFlight);
    TimeInfo nextFlightTimeInfo = buildNextFlightTimeInfo(clock, currentLeg, nextLeg,
        currentFlight, nextFlight);
    FlightInfo currentFlightInfo = buildFlightInfo(currentFlightTimeInfo, currentFlight);
    FlightInfo nextFlightInfo = buildFlightInfo(nextFlightTimeInfo, nextFlight);
    return new Dashboard(clock.now(), currentFlightInfo, nextFlightInfo);
  }

  private Proto.Flight getFlight(Leg leg, FlightStatusResponse flightStatusResponse) {
    if (flightStatusResponse == null) {
      return null;
    }
    if (!flightStatusResponse.hasFisFlightStatus()) {
      return null;
    }
    FisFlightStatus status = flightStatusResponse.getFisFlightStatus();
    if (status.getFlightCount() > 1 && leg == null) {
      return null;
    }
    return status.getFlightCount() == 1
        ? status.getFlight(0)
        : findFlight(leg, status.getFlightList());
  }

  private FlightInfo buildFlightInfo(TimeInfo timeInfo, Proto.Flight flight) {
    if (flight == null) {
      return null;
    }
    return new FlightInfo(
        String.format("JIA%s", flight.getFlightNumber()),
        flight.getOriginAirportCode(),
        getGate(flight.getFlightStatus().getOriginInfo()),
        flight.getDestinationAirportCode(),
        getGate(flight.getFlightStatus().getDestinationInfo()),
        getShortAircraftType(flight.getAircraftType()),
        flight.getFlightStatus().getCancelled(),
        timeInfo);
  }

  private String getGate(AirportInfo info) {
    if (info.hasTerminal()) {
      return String.format("%s/%s", info.getTerminal(), info.getGate());
    } else {
      return info.getGate();
    }
  }

  private TimeInfo buildCurrentFlightTimeInfo(Clock clock, Leg leg,
      Proto.Flight protoFlight) {
    if (protoFlight == null) {
      return null;
    }
    // Company show time is FLICA departure time minus 45 minutes.
    DateTime companyShow = leg.getDepartureTime()
        .minus(SHOW_TIME_PRIOR_TO_DEPARTURE_MINUTES);

    // Estimated show time is inbound arrival.
    DateTime estimatedShow = null;
    if (protoFlight.hasPriorLegFlightInfo()) {
      PriorLegFlightInfo prior = protoFlight.getPriorLegFlightInfo();
      String stringTime = prior.hasArrivalActualTime()
          && !prior.getArrivalActualTime().isEmpty()
              ? prior.getArrivalActualTime()
              : prior.getArrivalEstimatedTime();
      DateTime inboundArrival = DateTime.parse(stringTime);
      if (inboundArrival.isAfter(companyShow)) {
        estimatedShow = inboundArrival;
      }
    }

    Flight flight = new Flight(protoFlight);
    return new TimeInfo(
        clock,
        companyShow,
        estimatedShow,
        flight.getScheduledDepartureTime(),
        flight.hasActualDepartureTime() ? flight.getActualDepartureTime() : null,
        flight.getScheduledArrivalTime(),
        flight.hasActualArrivalTime() ? flight.getActualArrivalTime() : null);
  }

  private TimeInfo buildNextFlightTimeInfo(Clock clock, Leg currentLeg, Leg nextLeg,
      Proto.Flight currentFlight, Proto.Flight nextFlight) {
    if (nextFlight == null) {
      return null;
    }
    // Company show time is FLICA departure time minus 45 minutes.
    DateTime companyShow = nextLeg.getDepartureTime()
        .minus(SHOW_TIME_PRIOR_TO_DEPARTURE_MINUTES);

    // Estimated show time is inbound arrival.
    DateTime estimatedShow = null;
    if (nextFlight.hasPriorLegFlightInfo()) {
      PriorLegFlightInfo prior = nextFlight.getPriorLegFlightInfo();
      String stringTime = prior.hasArrivalActualTime()
          && !prior.getArrivalActualTime().isEmpty()
              ? prior.getArrivalActualTime()
              : prior.getArrivalEstimatedTime();
      DateTime inboundArrival = DateTime.parse(stringTime);
      if (inboundArrival.isAfter(companyShow)) {
        estimatedShow = inboundArrival;
      }
    }

    Flight flight = new Flight(nextFlight);
    return new TimeInfo(
        clock,
        companyShow,
        estimatedShow,
        flight.getScheduledDepartureTime(),
        flight.hasActualDepartureTime() ? flight.getActualDepartureTime() : null,
        flight.getScheduledArrivalTime(),
        flight.hasActualArrivalTime() ? flight.getActualArrivalTime() : null);
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

  private String getShortAircraftType(String longAircraftType) {
    switch (longAircraftType) {
      case "Canadair Regional Jet 900":
        return "RJ9";
      case "Canadair Regional Jet 700":
        return "RJ7";
      case "Canadair Regional Jet":
        return "RJ2";
      default:
        return longAircraftType;
    }
  }
}
