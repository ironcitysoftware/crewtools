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

package crewtools.flica.stats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import crewtools.flica.AwardDomicile;
import crewtools.flica.CachingFlicaService;
import crewtools.flica.FlicaConnection;
import crewtools.flica.Proto;
import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.CrewPosition;
import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.parser.PairingParser;
import crewtools.flica.pojo.Leg;
import crewtools.flica.pojo.Pairing;
import crewtools.flica.pojo.Trip;
import crewtools.util.FlicaConfig;

public class FindPairing {
  private final Logger logger = Logger.getLogger(FindPairing.class.getName());

  public static void main(String args[]) throws Exception {
    if (args.length != 3 && args.length != 4) {
      System.err.println("findPairing 2020-12-1 CLT . [2020-11]");
      System.exit(-1);
    }
    new FindPairing(args[0], args[1], args[2], args.length < 4 ? null : args[3]).run();
  }

  private final DataReader dataReader;
  private final LocalDate date;
  private final String origin;
  private final String destination;
  private final PairingAdapter pairingAdapter;
  private final YearMonth yearMonth;

  public FindPairing(String date, String origin, String destination, String yearMonth)
      throws IOException {
    this.dataReader = new DataReader();
    this.date = date.equals(".") ? null : LocalDate.parse(date);
    this.origin = origin;
    this.destination = destination;
    this.pairingAdapter = new PairingAdapter();
    this.yearMonth = yearMonth == null
        ? new YearMonth(this.date.getYear(), this.date.getMonthOfYear())
        : YearMonth.parse(yearMonth);
  }

  private void run() throws Exception {
    List<PairingList> pairingLists = retrieveAllPairings(yearMonth);

    List<Result> results = new ArrayList<>();
    for (PairingList pairingList : pairingLists) {
      results.addAll(search(pairingList));
    }
    Collections.sort(results, new Comparator<Result>() {
      @Override
      public int compare(Result o1, Result o2) {
        return o1.leg.getDepartureTime().compareTo(o2.leg.getDepartureTime());
      }
    });
    for (Result result : results) {
      output(result);
    }
  }

  class Result {
    String pairingName;
    String firstOrLastCode;
    String captain;
    Leg leg;

    public Result(String pairingName, String firstOrLastCode,
        String captain, Leg leg) {
      this.pairingName = pairingName;
      this.firstOrLastCode = firstOrLastCode;
      this.captain = captain;
      this.leg = leg;
    }
  }

  private final DateTimeFormatter dtf = DateTimeFormat.forPattern("HH:mm");

  private void output(Result result) {
    System.out.printf("%s %s %s %s %s-%s %s %s %s\n",
        result.firstOrLastCode,
        result.pairingName,
        result.leg.getDepartureTime().toLocalDate(),
        result.leg.getFlightNumber(),
        result.leg.getDepartureAirportCode(),
        result.leg.getArrivalAirportCode(),
        dtf.print(result.leg.getDepartureLocalTime()),
        dtf.print(result.leg.getArrivalLocalTime()),
        result.captain);
  }

  private List<Result> search(PairingList pairingList) {
    List<Result> results = new ArrayList<>();
    for (Proto.Trip protoTrip : pairingList.getTripList()) {
      Pairing pairing = pairingAdapter.adaptPairing(protoTrip);
      for (Trip trip : pairing.getTrips()) {
        for (Leg leg : trip.getLegs()) {
          if ((origin.equals(".")
              || leg.getDepartureAirportCode().equals(origin))
              && (destination.equals(".")
                  || leg.getArrivalAirportCode().equals(destination))
              && (date == null || leg.getDate().equals(date))) {
            String captain = "?";
            for (CrewMember member : protoTrip.getCrewList()) {
              if (member.getCrewPosition().equals(CrewPosition.CA)) {
                captain = member.getName() + "/" + member.getEmployeeId();
              }
            }
            String firstOrLastCode = " ";
            if (leg.equals(trip.getLegs().get(0))) {
              firstOrLastCode = "F";
            } else if (leg.equals(trip.getLegs().get(trip.getLegs().size() - 1))) {
              firstOrLastCode = "L";
            }
            results.add(new Result(
                protoTrip.getStartDate() + ":" + protoTrip.getPairingName(),
                firstOrLastCode,
                captain,
                leg));
          }
        }
      }
    }
    return results;
  }

  private List<PairingList> retrieveAllPairings(YearMonth yearMonth) throws Exception {
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    CachingFlicaService service = new CachingFlicaService(connection);
    List<PairingList> result = new ArrayList<>();
    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      String raw = service.getAllPairings(awardDomicile, Rank.CAPTAIN, 1, yearMonth);
      PairingParser pairingParser = new PairingParser(raw, yearMonth, true);
      result.add(pairingParser.parse());
    }
    return result;
  }
}
