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
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.Rank;
import crewtools.flica.Proto.Trip;
import crewtools.flica.parser.PairingParser;
import crewtools.flica.stats.DataReader;
import crewtools.util.FlicaConfig;

public class PairingRetriever {
  private final Logger logger = Logger.getLogger(PairingRetriever.class.getName());

  public static void main(String args[]) throws Exception {
    YearMonth yearMonth = YearMonth.parse("2019-09");
    int round = 1;
    new PairingRetriever().writeOneBinary(yearMonth, round);
  }

  public void writeOneBinary(YearMonth yearMonth, int round) throws Exception {
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    //FlicaService service = new CachingFlicaService(connection);
    FlicaService service = new FlicaService(connection);
    service.connect();
    DataReader dataReader = new DataReader();

    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      File outputFile = new File(dataReader.getPairingFilename(yearMonth, awardDomicile));
      if (outputFile.exists()) {
        logger.info("SKIP " + outputFile + " as it exists");
        continue;
      }
      String pairings = service.getAllPairings(
          awardDomicile,
          Rank.CAPTAIN,
          round,
          yearMonth);
      PairingParser pairingParser = new PairingParser(pairings, yearMonth, true);
      PairingList pairingList = pairingParser.parse();

      PairingList.Builder builder = PairingList.newBuilder();
      // Fix bad data
      for (Trip trip : pairingList.getTripList()) {
        if (trip.getPairingName().equals("A2006")) {
          Preconditions.checkState(
              trip.getOperatesExcept().equals("Sep 11. Sep 18. Sep 23"),
              trip.getOperatesExcept());
          Trip.Builder tb = trip.toBuilder();
          tb.setOperatesExcept("Sep 11. Sep 18.");
          trip = tb.build();
        }
        builder.addTrip(trip);
      }
      pairingList = builder.build();

      FileOutputStream output = new FileOutputStream(outputFile);
      pairingList.writeTo(output);
      output.close();
      logger.info("WROTE " + outputFile);
    }
  }

  public void writeAllBinary(YearMonth start, int round) throws Exception {
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    // FlicaService service = new CachingFlicaService(connection);
    FlicaService service = new FlicaService(connection);
    service.connect();
    DataReader dataReader = new DataReader();

    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      for (int monthBack = 0; monthBack < 3; monthBack++) {
        YearMonth yearMonth = start.minusMonths(monthBack);
        File outputFile = new File(dataReader.getPairingFilename(yearMonth, awardDomicile));
        if (outputFile.exists()) {
          logger.info("SKIP " + outputFile + " as it exists");
          continue;
        }
        String pairings = service.getAllPairings(
            awardDomicile,
            Rank.FIRST_OFFICER,
            round,
            yearMonth);
        PairingParser pairingParser = new PairingParser(pairings, yearMonth, true);
        PairingList pairingList = pairingParser.parse();
        FileOutputStream output = new FileOutputStream(outputFile);
        pairingList.writeTo(output);
        output.close();
        logger.info("WROTE " + outputFile);
      }
    }
  }
}

