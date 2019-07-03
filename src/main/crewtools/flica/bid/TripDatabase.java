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
import crewtools.rpc.Proto.BidConfig;

public class TripDatabase {
  private final Logger logger = Logger.getLogger(TripDatabase.class.getName());

  private final FlicaService service;
  private final boolean useProto;
  private final Map<PairingKey, Trip> trips;
  private final ReplayManager replayManager;
  private final BidConfig bidConfig;

  public TripDatabase(FlicaService service) throws IOException {
    this.service = service;
    this.trips = new HashMap<>();
    this.useProto = false;
    this.replayManager = new ReplayManager(false /* not replaying */, null);
    this.bidConfig = null;
  }

  public TripDatabase(FlicaService service, boolean useProto, YearMonth yearMonth,
      BidConfig bidConfig, ReplayManager replayManager)
      throws IOException, URISyntaxException, ParseException {
    this.service = service;
    this.useProto = useProto;
    this.bidConfig = bidConfig;
    this.replayManager = replayManager;

    Map<PairingKey, Trip> allTripsLastMonth;
    if (replayManager.isReplaying()) {
      trips = adapt(replayManager.readPairingList(yearMonth));
      allTripsLastMonth = adapt(replayManager.readPairingList(yearMonth.minusMonths(1)));
    } else {
      // The same pairing name in adjacent months refer to totally different trips,
      // so we use a key class which combines the date of the trip.
      Proto.PairingList thisMonthPairings = getAllPairings(yearMonth);
      replayManager.writePairingList(yearMonth, thisMonthPairings);
      trips = adapt(thisMonthPairings);
      logger.info("Loaded " + trips.size() + " trips this month");

      Proto.PairingList lastMonthPairings = getAllPairings(yearMonth.minusMonths(1));
      replayManager.writePairingList(yearMonth.minusMonths(1), lastMonthPairings);
      allTripsLastMonth = adapt(lastMonthPairings);
      logger.info("Loaded " + allTripsLastMonth.size() + " trips last month");
    }
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

  public void addTrip(Trip trip) {
    trips.put(trip.getPairingKey(), trip);
  }

  public Trip getTrip(PairingKey key)
      throws URISyntaxException, IOException, ParseException {
    if (!trips.containsKey(key)) {
      Trip trip = getIndividualPairingDetails(key);
      trips.put(key, trip);
    }
    return trips.get(key);
  }

  private Proto.PairingList getAllPairings(YearMonth yearMonth)
      throws IOException, URISyntaxException, ParseException {
    Proto.PairingList pairingList;
    if (!useProto) {
      String rawPairings = service.getAllPairings(
          AwardDomicile.valueOf(bidConfig.getAwardDomicile()),
          Rank.valueOf(bidConfig.getRank()),
          bidConfig.getRound(),
          yearMonth);
      PairingParser pairingParser = new PairingParser(
          rawPairings,
          yearMonth,
          false /* cancelled */);
      pairingList = pairingParser.parse();
    } else {
      String filename = new DataReader().getPairingFilename(yearMonth, AwardDomicile.CLT);
      Proto.PairingList.Builder builder = Proto.PairingList.newBuilder();
      FileInputStream inputStream = new FileInputStream(new File(filename));
      builder.mergeFrom(inputStream);
      pairingList = builder.build();
    }
    return pairingList;
  }

  private Map<PairingKey, Trip> adapt(Proto.PairingList pairingList) {
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
    String rawPairingDetail;
    if (replayManager.isReplaying()) {
      rawPairingDetail = replayManager.readPairing(key);
    } else {
      rawPairingDetail = service.getPairingDetail(key.getPairingName(),
          key.getPairingDate());
      replayManager.writePairing(key, rawPairingDetail);
    }
    IndividualPairingParser parser = new IndividualPairingParser(key, rawPairingDetail);
    PairingAdapter pairingAdapter = new PairingAdapter();
    Proto.Trip trip = parser.parse();
    return pairingAdapter.adaptTrip(trip);
  }
}
