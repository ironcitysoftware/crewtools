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

package crewtools.flica.bid;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.parser.IndividualPairingParser;
import crewtools.flica.pojo.Pairing;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Trip;
import crewtools.flica.stats.DataReader;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.FileUtils;
import crewtools.util.FlicaConfig;

/** Tool for inspecting scoring of arbitrary lines and trips. */
public class Scorer {
  public static void main(String args[]) throws Exception {
    if (args.length == 0) {
      System.err.println("scorer.sh 2018-1-1:L1234 [2018-1-1:L1234]");
      System.exit(-1);
    }
    Map<PairingKey, Trip> pairings = getAllPairings(YearMonth.parse("2019-6"));
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    FlicaService service = new FlicaService(connection);
    BidConfig bidConfig = FileUtils.readBidConfig();
    Trip left = getTrip(args[0], pairings, service);
    Trip right = null;
    if (args.length > 1) {
      right = getTrip(args[1], pairings, service);
    }
    TripScore leftScore = new TripScore(left, bidConfig);
    TripScore rightScore = null;
    if (args.length > 1) {
      rightScore = new TripScore(right, bidConfig);
    }
    displayScoreExplanation(left.getPairingName(), leftScore.getScoreExplanation());
    if (args.length > 1) {
      System.out.println("----------------------------------------------------");
      displayScoreExplanation(right.getPairingName(), rightScore.getScoreExplanation());
    }
  }

  private static Trip getTrip(String keyString, Map<PairingKey, Trip> allPairings,
      FlicaService service) throws Exception {
    PairingKey key = PairingKey.parse(keyString);
    if (allPairings.containsKey(key)) {
      return allPairings.get(key);
    }
    String rawPairingDetail = service.getPairingDetail(key.getPairingName(),
        key.getPairingDate());
    IndividualPairingParser parser = new IndividualPairingParser(key, rawPairingDetail);
    Proto.Trip trip = parser.parse();
    PairingAdapter pairingAdapter = new PairingAdapter();
    return pairingAdapter.adaptTrip(trip);
  }

  private static Map<PairingKey, Trip> getAllPairings(YearMonth yearMonth)
      throws Exception {
    String filename = new DataReader().getPairingFilename(yearMonth, AwardDomicile.CLT);
    Proto.PairingList.Builder builder = Proto.PairingList.newBuilder();
    FileInputStream inputStream = new FileInputStream(new File(filename));
    builder.mergeFrom(inputStream);
    Proto.PairingList pairingList = builder.build();
    PairingAdapter pairingAdapter = new PairingAdapter();
    Map<PairingKey, Trip> trips = new HashMap<>();
    for (Proto.Trip protoTrip : pairingList.getTripList()) {
      Pairing pairing = pairingAdapter.adaptPairing(protoTrip);
      for (Trip trip : pairing.getTrips()) {
        trips.put(trip.getPairingKey(), trip);
      }
    }
    return trips;
  }

  private static void displayScoreExplanation(String pairingName,
      List<String> explanations) {
    System.out.println(pairingName + " score diagnostic:");
    for (String line : explanations) {
      System.out.println(line);
    }
  }
}
