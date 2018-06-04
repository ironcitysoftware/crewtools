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
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Minutes;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import crewtools.aa.Proto.FisFlightStatus;
import crewtools.aa.Proto.Flight;
import crewtools.aa.Proto.FlightStatusResponse;
import crewtools.aa.Proto.PriorLegFlightInfo;
import crewtools.flica.pojo.Leg;

public class DashboardAdaptor {
  private static final Minutes SHOW_TIME_PRIOR_TO_DEPARTURE_MINUTES = Minutes.minutes(45);
  private static final DateTimeZone EASTERN_TIMEZONE = DateTimeZone
      .forID("America/New_York");

  public Dashboard adapt(DateTime now, Leg currentLeg, FlightStatusResponse currentFlight,
      Leg nextLeg,
      FlightStatusResponse nextFlight) {
    FlightInfo currentFlightInfo = buildFlightInfo(now, currentLeg, currentFlight);
    FlightInfo nextFlightInfo = buildFlightInfo(now, nextLeg, nextFlight);
    return new Dashboard(now, getZulu(now), currentFlightInfo, nextFlightInfo);
  }

  private FlightInfo buildFlightInfo(DateTime now, Leg leg,
      FlightStatusResponse statusWrapper) {
    if (statusWrapper == null) {
      return null;
    }
    if (!statusWrapper.hasFisFlightStatus()) {
      return null;
    }
    FisFlightStatus status = statusWrapper.getFisFlightStatus();
    if (status.getFlightCount() > 1 && leg == null) {
      return null;
    }
    Flight flight = status.getFlightCount() == 1
        ? status.getFlight(0)
        : findFlight(leg, status.getFlightList());
    TimeInfo timeInfo = buildTimeInfo(now, leg, flight);
    return new FlightInfo(
        flight.getOriginAirportCode(),
        flight.getFlightStatus().getOriginInfo().getGate(),
        flight.getDestinationAirportCode(),
        flight.getFlightStatus().getDestinationInfo().getGate(),
        getShortAircraftType(flight.getAircraftType()),
        timeInfo);
  }

  private TimeInfo buildTimeInfo(DateTime now, Leg leg, Flight flight) {
    // Company show time is FLICA departure time minus 45 minutes.
    DateTime companyShow = leg.getDepartureTime()
        .minus(SHOW_TIME_PRIOR_TO_DEPARTURE_MINUTES);

    // Actual departure time.
    // System.out.println("KRW " + flight);

    // Estimated show time is inbound arrival minus 45 minutes.
    if (flight.hasPriorLegFlightInfo()) {
      PriorLegFlightInfo prior = flight.getPriorLegFlightInfo();
      String stringTime = prior.hasArrivalActualTime()
          && !prior.getArrivalActualTime().isEmpty()
          ? prior.getArrivalActualTime()
          : prior.getArrivalEstimatedTime();
      // TODO: I believe the times are always local, but the zone is always Eastern.
      // Check this out.
      DateTime Time = DateTime.parse(stringTime).withZoneRetainFields(EASTERN_TIMEZONE);
    }

    return new TimeInfo(
        companyShow.isBefore(now) ? "" : getPrettyOffset(now, companyShow),
        companyShow.isBefore(now) ? "" : getZulu(companyShow),
        "",
        "");
  }

  private DateTimeFormatter HH_COLON_MM = DateTimeFormat.forPattern("HH:mm");

  private String getZulu(DateTime dateTime) {
    return HH_COLON_MM.print(dateTime.withZone(DateTimeZone.UTC)) + "Z";
  }

  private String getPrettyOffset(DateTime now, DateTime then) {
    Interval interval = new Interval(now, then);
    Period period = interval.toPeriod();
    int hours = period.getHours() + 24 * period.getDays();
    int minutes = period.getMinutes();
    if (hours > 0) {
      return String.format("%s%dh%dm",
          then.isBefore(now) ? "-" : "",
          hours,
          minutes);
    } else {
      return String.format("%s%dm",
          then.isBefore(now) ? "-" : "",
          minutes);
    }
  }

  /**
   * FisFlightStatus will have multiple flights if the same flight number
   * is used for the flight both to and from the outstation, for example.
   * Matches origin/destination to select the right one.
   */
  private Flight findFlight(Leg leg, List<Flight> flights) {
    for (Flight flight : flights) {
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
      default:
        return longAircraftType;
    }
  }
}
