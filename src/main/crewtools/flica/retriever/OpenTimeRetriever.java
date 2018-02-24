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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.io.Files;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.flica.parser.OpenTimeParser;
import crewtools.util.FlicaConfig;

public class OpenTimeRetriever {
  private final Logger logger = Logger.getLogger(OpenTimeRetriever.class.getName());
  private final File protoTxtOutputFile;
  private final int round;
  private final YearMonth yearMonth;

  public static void main(String args[]) throws Exception {
    new OpenTimeRetriever(args).run();
  }

  public OpenTimeRetriever(String args[]) {
    if (args.length != 3) {
      System.err.println("OpenTimeRetriever line.io round yyyy-mm");
      System.err.println("not " + Arrays.asList(args));
      System.exit(1);
    }
    this.protoTxtOutputFile = new File(args[0]);
    this.round = Integer.parseInt(args[1]);
    this.yearMonth = YearMonth.parse(args[2]);
  }

  public void run() throws Exception {
    FlicaConnection connection = new FlicaConnection(new FlicaConfig());
    FlicaService service = new FlicaService(connection);
    service.connect();

    String openTime = service.getOpenTime(AwardDomicile.CLT, Rank.FIRST_OFFICER, round, yearMonth);
    OpenTimeParser openTimeParser = new OpenTimeParser(2017, openTime);
    FileWriter writer = new FileWriter(protoTxtOutputFile);
//    TextFormat.print(openTimeParser.parse(), writer);
    writer.write(openTime);
    writer.close();
  }

  public void runFromFile() throws Exception {
    String content = Files.toString(protoTxtOutputFile, StandardCharsets.UTF_8);
    OpenTimeParser openTimeParser = new OpenTimeParser(2017, content);
    System.out.println(openTimeParser.parse());
  }
}
