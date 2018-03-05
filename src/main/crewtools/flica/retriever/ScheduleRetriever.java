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

import java.io.File;
import java.io.FileWriter;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.protobuf.TextFormat;

import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.parser.ScheduleParser;
import crewtools.util.FlicaConfig;

public class ScheduleRetriever {
  private final Logger logger = Logger.getLogger(ScheduleRetriever.class.getName());
  private final YearMonth yearMonth;
  private final File scheduleProtoTxtOutputFile;

  public static void main(String args[]) throws Exception {
    new ScheduleRetriever(args).run();
  }

  public ScheduleRetriever(String args[]) {
    if (args.length != 2) {
      System.err.println("ScheduleRetriever year-month schedule.io");
      System.exit(1);
    }
    this.yearMonth = YearMonth.parse(args[0]);
    this.scheduleProtoTxtOutputFile = new File(args[1]);
  }

  public void run() throws Exception {
    FlicaConnection connection = new FlicaConnection(new FlicaConfig());
    FlicaService service = new FlicaService(connection);
    service.connect();

    String rawSchedule = service.getSchedule(yearMonth);
    ScheduleParser scheduleParser = new ScheduleParser(rawSchedule);
    FileWriter writer = new FileWriter(scheduleProtoTxtOutputFile);
    TextFormat.print(scheduleParser.parse(), writer);
    writer.close();
  }
}
