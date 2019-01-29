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

package crewtools.optimize;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.pojo.Pairing;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.ThinLine;
import crewtools.flica.pojo.Trip;
import crewtools.flica.stats.DataReader;
import crewtools.util.Period;
import jline.internal.Preconditions;

public class ComputeBlockPerDuty {
  public static void main(String args[]) throws Exception {
    new ComputeBlockPerDuty().run();
  }

  public void run() throws Exception {
    YearMonth yearMonth = YearMonth.parse("2019-2");
    Map<PairingKey, Trip> pairings = new HashMap<>();
    List<ThinLine> lines = new ArrayList<>();
    for (AwardDomicile domicile : AwardDomicile.values()) {
      pairings.putAll(getAllPairings(yearMonth, domicile));
      lines.addAll(getAllLines(yearMonth, domicile));
    }

    int numDutyDays = 0;
    Period totalBlock = Period.ZERO;

    // For each line, for each pairing in the line,
    // for each segment,
    // include block time in average.
    for (ThinLine line : lines) {
      for (PairingKey key : line.getPairingKeys()) {
        Trip trip = Preconditions.checkNotNull(pairings.get(key));
        for (Section section : trip.getSections()) {
          numDutyDays++;
          totalBlock = totalBlock.plus(section.block);
        }
      }
    }

    System.out.printf("%d duty days with a total block of %s\n", numDutyDays, totalBlock);
    if (numDutyDays > 0) {
      int averageBlockPerDutyMinutes = totalBlock.getTotalMinutes() / numDutyDays;
      Period averageBlockPerDuty = Period.minutes(averageBlockPerDutyMinutes);
      System.out.printf("Average block per duty: %s\n", averageBlockPerDuty);
    }
  }

  private Map<PairingKey, Trip> getAllPairings(
      YearMonth yearMonth, AwardDomicile domicile) throws Exception {
    String filename = new DataReader().getPairingFilename(yearMonth, domicile);
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

  private static final int ROUND_ONE = 1;

  private List<ThinLine> getAllLines(YearMonth yearMonth, AwardDomicile domicile)
      throws Exception {
    Proto.ThinLineList lineList;
    DataReader dataReader = new DataReader();
    lineList = dataReader.readLines(yearMonth, domicile, Rank.FIRST_OFFICER, ROUND_ONE);
    List<ThinLine> result = new ArrayList<>();
    lineList.getThinLineList().forEach(line -> result.add(new ThinLine(line)));
    return result;
  }
}
