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

package crewtools.flica.bid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.pojo.Pairing;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.ThinLine;
import crewtools.flica.pojo.Trip;
import crewtools.flica.stats.DataReader;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.FileUtils;
import crewtools.util.Period;

/** Tool for inspecting scoring of arbitrary lines and trips. */
public class LineScoreDebug {
  private final BidConfig bidConfig;
  private final AwardDomicile awardDomicile;
  private final Rank rank;
  private final DataReader dataReader;
  private final Map<PairingKey, Trip> pairings;
  private final Map<String, ThinLine> lines;
  private final Set<LocalDate> vacationDays;

  public static void main(String args[]) throws Exception {
    if (args.length == 0) {
      System.err.println("lineScoreDebug.sh 101 102");
      System.exit(-1);
    }
    new LineScoreDebug().run(args);
  }

  public LineScoreDebug() throws Exception {
    this.bidConfig = FileUtils.readBidConfig();
    this.awardDomicile = AwardDomicile.valueOf(bidConfig.getAwardDomicile());
    this.rank = Rank.valueOf(bidConfig.getRank());
    YearMonth yearMonth = YearMonth.parse(bidConfig.getYearMonth());
    this.dataReader = new DataReader();
    this.pairings = getAllPairings(yearMonth);
    this.lines = getAllLines(yearMonth);
    this.vacationDays = bidConfig.getVacationDateList()
        .stream().map(s -> LocalDate.parse(s)).collect(Collectors.toSet());
  }

  public void run(String args[]) throws Exception {
    LineScore left = getLineScore(args[0]);
    LineScore right = getLineScore(args[1]);
    MonthlyBidStrategy strategy = new MonthlyBidStrategy(bidConfig);
    List<String> explanation = new ArrayList<>();
    strategy.setDebug(explanation);
    int result = strategy.compare(left, right);
    System.out.println("RESULT: " + result);
    for (String line : explanation) {
      System.out.println(line);
    }
  }

  private LineScore getLineScore(String lineName) {
    Map<PairingKey, Trip> trips = new HashMap<>();
    for (PairingKey key : lines.get(lineName).getPairingKeys()) {
      trips.put(key,
          Preconditions.checkNotNull(pairings.get(key),
              "Pairing not found: " + key));
    }
    Map<LocalDate, Period> carryInCredit = ImmutableMap.of();
    return new LineScore(lines.get(lineName), trips, bidConfig, carryInCredit,
        vacationDays);
  }

  private Map<PairingKey, Trip> getAllPairings(YearMonth yearMonth)
      throws Exception {
    Proto.PairingList pairingList = dataReader.readPairings(yearMonth, awardDomicile);
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

  private Map<String, ThinLine> getAllLines(YearMonth yearMonth) throws Exception {
    Proto.ThinLineList lineList;
    lineList = dataReader.readLines(yearMonth, awardDomicile,
        Rank.valueOf(bidConfig.getRank()), bidConfig.getRound());
    Map<String, ThinLine> result = new HashMap<>();
    lineList.getThinLineList()
        .forEach(line -> result.put(line.getLineName(), new ThinLine(line)));
    return result;
  }
}
