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

package crewtools.flica.retriever;

import java.util.Arrays;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.util.FlicaConfig;

public class OpentimeRequestRetriever {
  private final Logger logger = Logger.getLogger(OpentimeRequestRetriever.class.getName());
  private final int round;
  private final YearMonth yearMonth;

  public static void main(String args[]) throws Exception {
    new OpentimeRequestRetriever(args).run();
  }

  public OpentimeRequestRetriever(String args[]) {
    if (args.length != 2) {
      System.err.println("OpenTimeRequestRetriever round yyyy-mm");
      System.err.println("not " + Arrays.asList(args));
      System.exit(1);
    }
    this.round = Integer.parseInt(args[0]);
    this.yearMonth = YearMonth.parse(args[1]);
  }

  public void run() throws Exception {
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    FlicaService service = new FlicaService(connection);
    service.connect();
    System.out.println(service.getOpentimeRequests(round, yearMonth));
  }
}
