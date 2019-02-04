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
import java.util.Arrays;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.TextFormat;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.DomicileAwards;
import crewtools.flica.Proto.Rank;
import crewtools.flica.parser.AwardParser;
import crewtools.util.FlicaConfig;

/** Retrieves all combinations of FO/Captain domicile awards */
public class AwardRetriever {
  private final Logger logger = Logger.getLogger(AwardRetriever.class.getName());
  private final File awardProtoTxtOutputFile;
  private final YearMonth yearMonth;

  public static void main(String args[]) throws Exception {
    new AwardRetriever(args).run();
  }

  public AwardRetriever(String args[]) {
    if (args.length != 2) {
      System.err.println("AwardRetriever award.io yyyy-mm");
      System.err.println("not " + Arrays.asList(args));
      System.exit(1);
    }
    this.awardProtoTxtOutputFile = new File(args[0]);
    this.yearMonth = YearMonth.parse(args[1]);
  }

  public void run() throws Exception {
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    connection.connect();
    String session = connection.getSession();
    System.out.println("Session: " + session);
    connection.setSession(session);
    if (true)
      return;

    FlicaService service = new FlicaService(connection);
    service.connect();

    DomicileAwards.Builder domicileAwards = DomicileAwards.newBuilder();
    for (AwardDomicile domicile : AwardDomicile.values()) {
      for (Rank rank : ImmutableList.of(Rank.CAPTAIN, Rank.FIRST_OFFICER)) {
        for (int round : ImmutableList.of(1, 2)) {
          String award = service.getBidAward(domicile, rank, round, yearMonth);
          AwardParser parser = new AwardParser(award, domicile, rank, round);
          domicileAwards.addDomicileAward(parser.parse());
        }
      }
    }

    FileWriter writer = new FileWriter(awardProtoTxtOutputFile);
    TextFormat.print(domicileAwards, writer);
    writer.close();
  }
}
