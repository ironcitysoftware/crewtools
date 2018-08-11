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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import crewtools.aa.FlightStatusService;
import crewtools.flica.FlicaService;
import crewtools.flica.LegSelector;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.Leg;
import crewtools.util.Clock;
import crewtools.util.ListAndIndex;

public class DashboardService {
  private final FlicaService flicaService;
  private final FlightStatusService flightStatusService;
  private final DashboardAdaptor dashboardAdaptor;

  public DashboardService(FlicaService flicaService,
      FlightStatusService flightStatusService) {
    this.flicaService = flicaService;
    this.flightStatusService = flightStatusService;
    this.dashboardAdaptor = new DashboardAdaptor();
  }

  public Dashboard getDashboard(Clock clock) throws IOException, ParseException {
    ScheduleProvider scheduleProvider = new ScheduleProvider(clock, flicaService);
    LegSelector selector = new LegSelector(clock, scheduleProvider);
    ListAndIndex<Leg> legs = selector.getRelevantLegs();
    List<FlightStatusResponseWrapper> statuses = legs.list
        .stream()
        .map(leg -> getStatus(leg))
        .collect(Collectors.toList());
    return dashboardAdaptor.adapt(clock, legs, statuses);
  }

  private FlightStatusResponseWrapper getStatus(Leg leg) {
    try {
      return new FlightStatusResponseWrapper(
          flightStatusService.getFlightStatus(
              leg.getFlightNumber(),
              leg.getDepartureTime().toLocalDate()));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
