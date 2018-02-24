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
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.flica.Proto.ThinLineList;
import crewtools.flica.parser.LineParser;
import crewtools.flica.stats.DataReader;
import crewtools.util.FlicaConfig;

/** Retrieves all combinations of FO/Captain domicile awards */
public class LineRetriever {
  private final Logger logger = Logger.getLogger(LineRetriever.class.getName());
  
  private final AwardDomicile awardDomicile;
  private final Rank rank;
  private final File outputFile;
  private final int round;
  private final YearMonth yearMonth;

  public static void main(String args[]) throws Exception {
    new LineRetriever(args).run();
  }

  public LineRetriever(String args[]) {
    if (args.length != 5) {
      System.err.println("LineRetriever CLT FIRST_OFFICER round yyyy-mm output.io");
      System.err.println("not " + Arrays.asList(args));
      System.exit(1);
    }
    int i = 0;
    this.awardDomicile = AwardDomicile.valueOf(args[i++]);
    this.rank = Rank.valueOf(args[i++]);
    this.round = Integer.parseInt(args[i++]);
    this.yearMonth = YearMonth.parse(args[i++]);
    this.outputFile = new File(args[i++]);
  }

  public void run2() throws Exception {
    FlicaConnection connection = new FlicaConnection(new FlicaConfig());
    FlicaService service = new FlicaService(connection);
    service.connect();

    String lines = service.getAllLines(awardDomicile, rank, round, yearMonth);

    LineParser lineParser = new LineParser(lines);
    ThinLineList lineList = lineParser.parse();
    
    System.out.println(lineList);

    FileOutputStream output = new FileOutputStream(outputFile);
    lineList.writeTo(output);
    output.close();
  }
  
  public void run() throws Exception {
    FlicaConnection connection = new FlicaConnection(new FlicaConfig());
    FlicaService service = new FlicaService(connection);
    service.connect();

    DataReader dataReader = new DataReader();

    YearMonth start = YearMonth.parse("2018-3");
    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      for (int monthBack = 0; monthBack < 3; monthBack++) {
        YearMonth yearMonth = start.minusMonths(monthBack);
        File outputFile = new File(dataReader.getLineFilename(yearMonth, awardDomicile));
        if (outputFile.exists()) {
          logger.info("SKIP " + outputFile + " as it exists");
          continue;
        }
        String lines = service.getAllLines(awardDomicile, Rank.FIRST_OFFICER, 1, yearMonth);
        LineParser lineParser = new LineParser(lines);
        ThinLineList lineList = lineParser.parse();
        FileOutputStream output = new FileOutputStream(outputFile);
        lineList.writeTo(output);
        output.close();
        logger.info("WROTE " + outputFile);
      }
    }
  }
}
