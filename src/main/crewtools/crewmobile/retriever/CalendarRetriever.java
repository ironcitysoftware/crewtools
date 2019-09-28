/**
 * Copyright 2019 Iron City Software LLC
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

package crewtools.crewmobile.retriever;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.common.io.Files;
import com.google.protobuf.TextFormat;

import crewtools.crewmobile.ConfigReader;
import crewtools.crewmobile.CrewmobileService;
import crewtools.crewmobile.Proto.CalendarDataFeed;
import crewtools.crewmobile.Proto.CrewmobileConfig;

public class CalendarRetriever {
  private final Logger logger = Logger.getLogger(CalendarRetriever.class.getName());

  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err.println("CalendarRetriever destination-file.txt");
      System.exit(-1);
    }
    new CalendarRetriever().run(new File(args[0]));
  }

  public void run(File output) throws Exception {
    CrewmobileConfig config = ConfigReader.readConfig();
    CrewmobileService service = new CrewmobileService(config);
    service.connect();
    CalendarDataFeed feed = service.getCalendarDataFeed();
    Files.write(TextFormat.printToString(feed), output, StandardCharsets.UTF_8);
  }
}
