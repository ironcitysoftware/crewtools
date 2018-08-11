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

import com.google.common.base.Preconditions;

import crewtools.aa.Proto;
import crewtools.aa.Proto.AirportInfo;
import crewtools.aa.Proto.PriorLegFlightInfo;
import crewtools.flica.pojo.Leg;
import crewtools.util.Clock;
import crewtools.util.ListAndIndex;

public class DashboardAdaptor {
  private static final Minutes SHOW_TIME_PRIOR_TO_DEPARTURE_MINUTES = Minutes.minutes(45);

  public Dashboard adapt(
      Clock clock,
      ListAndIndex<Leg> legs,
      List<FlightStatusResponseWrapper> statuses) {
    Preconditions.checkState(legs.list.size() == statuses.size());
    int currentIndex = legs.index;
    List<FlightInfo> flightInfos = new ArrayList<>();
    for (int i = 0; i < legs.list.size(); ++i) {
      Leg leg = legs.list.get(i);
      Proto.Flight flight = statuses.get(i).getFlight(leg);
      TimeInfo timeInfo = buildFlightTimeInfo(clock, leg, flight);
      FlightInfo flightInfo = buildFlightInfo(timeInfo, flight);
      if (flightInfo != null) {
        flightInfos.add(flightInfo);
      } else {
        currentIndex = -1;  // TODO
      }
    }
    return new Dashboard(clock, flightInfos, currentIndex);
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

  private TimeInfo buildFlightTimeInfo(Clock clock, Leg leg,
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
