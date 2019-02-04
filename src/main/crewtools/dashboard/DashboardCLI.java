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

import crewtools.aa.FlightStatusService;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.util.FlicaConfig;
import crewtools.util.SystemClock;

public class DashboardCLI {
  public static void main(String args[]) throws Exception {
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    FlicaService flicaService = new FlicaService(connection);

    FlightStatusService flightStatusService = new FlightStatusService();

    DashboardService dashboardService = new DashboardService(flicaService,
        flightStatusService);
    System.out.println(dashboardService.getDashboard(new SystemClock()));
  }
}
