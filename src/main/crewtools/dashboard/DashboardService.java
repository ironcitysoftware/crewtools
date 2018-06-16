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

import crewtools.aa.FlightStatusService;
import crewtools.aa.Proto.FlightStatusResponse;
import crewtools.flica.FlicaService;
import crewtools.flica.LegSelector;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.Leg;
import crewtools.flica.pojo.Schedule;
import crewtools.util.Clock;

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
    Schedule currentMonthSchedule = scheduleProvider.getCurrentMonthSchedule();

    LegSelector selector = new LegSelector(clock);
    Leg currentLeg = selector.getCurrentLeg(currentMonthSchedule);
    if (currentLeg == null) {
      currentLeg = selector.getPreviousLeg(scheduleProvider);
    }
    Leg nextLeg = selector.getNextLeg(scheduleProvider);
    FlightStatusResponse currentFlight = null;
    if (currentLeg != null) {
      currentFlight = flightStatusService.getFlightStatus(currentLeg.getFlightNumber(),
          currentLeg.getDepartureTime().toLocalDate());
    }
    FlightStatusResponse nextFlight = null;
    if (nextLeg != null) {
      nextFlight = flightStatusService.getFlightStatus(nextLeg.getFlightNumber(),
          nextLeg.getDepartureTime().toLocalDate());
    }

    return dashboardAdaptor.adapt(clock, currentLeg, currentFlight, nextLeg, nextFlight);
  }
}
