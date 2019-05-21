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
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.parser.IndividualPairingParser;
import crewtools.flica.parser.PairingParser;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.Pairing;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.flica.stats.DataReader;

public class TripDatabase {
  private final Logger logger = Logger.getLogger(TripDatabase.class.getName());

  private final FlicaService service;
  private final boolean useProto;
  private final Map<PairingKey, Trip> trips;

  public TripDatabase(FlicaService service) {
    this.service = service;
    this.trips = new HashMap<>();
    this.useProto = false;
  }

  public TripDatabase(FlicaService service, boolean useProto, YearMonth yearMonth) throws IOException, URISyntaxException, ParseException {
    this.service = service;
    this.useProto = useProto;
    // The same pairing name in adjacent months refer to totally different trips,
    // so we use a key class which combines the date of the trip.
    logger.info("Loading this month's pairings");
    trips = getAllPairings(yearMonth);
    logger.info("Loading last month's pairings");
    Map<PairingKey, Trip> allTripsLastMonth = getAllPairings(yearMonth.minusMonths(1));
    allTripsLastMonth.forEach((key, value) ->
      trips.merge(key, value, (k, v) ->
        { throw new AssertionError("duplicate values for key: " + key); }));
  }

  public void addTripsFromSchedule(Schedule schedule) {
    // Modified trips don't show up in 'getAllPairings'.  Wonder why?
    schedule.getTrips().forEach((key, value) ->
      { if (!trips.containsKey(key)) {
        logger.info("Adding trip " + key + " from schedule");
        trips.put(key, value); } }
    );
  }

  public Trip getTrip(PairingKey key)
      throws URISyntaxException, IOException, ParseException {
    if (!trips.containsKey(key)) {
      Trip trip = getIndividualPairingDetails(key);
      trips.put(key, trip);
    }
    return trips.get(key);
  }

  private Map<PairingKey, Trip> getAllPairings(YearMonth yearMonth) throws IOException, URISyntaxException, ParseException {
    Proto.PairingList pairingList;
    if (!useProto) {
      String rawPairings = service.getAllPairings(AwardDomicile.CLT, Rank.FIRST_OFFICER,
          1, yearMonth);
      PairingParser pairingParser = new PairingParser(rawPairings, yearMonth, false /*
                                                                                     * cancelled
                                                                                     */);
      pairingList = pairingParser.parse();
    } else {
      String filename = new DataReader().getPairingFilename(yearMonth, AwardDomicile.CLT);
      Proto.PairingList.Builder builder = Proto.PairingList.newBuilder();
      FileInputStream inputStream = new FileInputStream(new File(filename));
      builder.mergeFrom(inputStream);
      pairingList = builder.build();
    }

    PairingAdapter pairingAdapter = new PairingAdapter();
    Map<PairingKey, Trip> trips = new HashMap<>();
    for (Proto.Trip protoTrip : pairingList.getTripList()) {
      Pairing pairing = pairingAdapter.adaptPairing(protoTrip);
      for (Trip trip: pairing.getTrips()) {
        trips.put(trip.getPairingKey(), trip);
      }
    }
    return trips;
  }

  private Trip getIndividualPairingDetails(PairingKey key)
      throws URISyntaxException, IOException, ParseException {
    String rawPairingDetail = service.getPairingDetail(key.getPairingName(), key.getPairingDate());
    IndividualPairingParser parser = new IndividualPairingParser(key, rawPairingDetail);
    PairingAdapter pairingAdapter = new PairingAdapter();
    Proto.Trip trip = parser.parse();
    return pairingAdapter.adaptTrip(trip);
  }
}
