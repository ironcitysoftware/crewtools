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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;

import crewtools.flica.AwardDomicile;
import crewtools.flica.CachingFlicaService;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.adapters.ScheduleAdapter;
import crewtools.flica.parser.LineParser;
import crewtools.flica.parser.PairingParser;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.ScheduleParser;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.ThinLine;
import crewtools.flica.pojo.Trip;
import crewtools.flica.stats.DataReader;
import crewtools.rpc.Proto.BidConfig;
import crewtools.rpc.Proto.PairingOverride;
import crewtools.util.Calendar;
import crewtools.util.FileUtils;
import crewtools.util.FlicaConfig;
import crewtools.util.Period;
import okhttp3.Response;

public class MonthlyBidder {
  private final Logger logger = Logger.getLogger(MonthlyBidder.class.getName());
  private final MonthlyBidderCommandLineConfig cmdLine;
  private final BidConfig bidConfig;
  private final AwardDomicile awardDomicile;
  private final Rank rank;

  public static void main(String args[]) throws Exception {
    new MonthlyBidder(args).run();
  }

  public MonthlyBidder(String args[]) throws Exception {
    this.cmdLine = new MonthlyBidderCommandLineConfig(args);
    this.bidConfig = FileUtils.readBidConfig();
    this.awardDomicile = AwardDomicile.valueOf(bidConfig.getAwardDomicile());
    this.rank = Rank.valueOf(bidConfig.getRank());
  }

  public void run() throws Exception {
    runForMonthlyBid();
  }

  private void runForMonthlyBid() throws Exception {
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    FlicaService service;
    if (cmdLine.useCachingService()) {
      service = new CachingFlicaService(connection);
    } else {
      service = new FlicaService(connection);
      service.connect();
    }

    YearMonth yearMonth = YearMonth.parse(bidConfig.getYearMonth());
    Map<PairingKey, Trip> pairings = getAllPairings(service, yearMonth);
    logger.info(pairings.size() + " pairings read for " + yearMonth);
    List<ThinLine> lines = getAllLines(service, yearMonth);
    logger.info(lines.size() + " lines read for " + yearMonth);
    Map<String, ThinLine> linesByName = new HashMap<>();

    Schedule priorMonthSchedule = getSchedule(service, yearMonth.minusMonths(1));
    Map<LocalDate, Period> carryInCredit = priorMonthSchedule
        .getTripCreditInMonth(yearMonth);

    List<LineScore> lineScores = new ArrayList<>();
    for (ThinLine line : lines) {
      linesByName.put(line.getLineName(), line);
      Map<PairingKey, Trip> trips = new HashMap<>();
      for (PairingKey key : line.getPairingKeys()) {
        logger.fine("Line " + line.getLineName() + " key " + key);
        trips.put(key,
            Preconditions.checkNotNull(pairings.get(key),
                "Pairing not found: " + key));
      }
      LineScore lineScore = new LineScore(line, trips, bidConfig, carryInCredit);
      if (!cmdLine.desirableOnly() || lineScore.isDesirableLine()) {
        lineScores.add(lineScore);
      }
    }
    Collections.sort(lineScores, new MonthlyBidStrategy(bidConfig));

    logger.info("Computed bids:");

    List<LocalDate> datesInPeriod = new Calendar(yearMonth).getDatesInPeriod();
    System.out.println(header(datesInPeriod));
    LinkedList<String> bids = new LinkedList<>();
    for (LineScore lineScore : lineScores) {
      if (!bids.contains(lineScore.getLineName())) {
        bids.add(lineScore.getLineName());
        String text = formatLine(datesInPeriod, lineScore, pairings, yearMonth);
        System.out.println(text);
      }
    }

    if (cmdLine.submitBids()) {
      logger.info("Submitting bids!");
      Response response = service.submitLineBid(/* round */ 1, yearMonth, bids);
      Preconditions.checkState(response.code() == 200, response.toString());
      response.close();
    } else {
      logger.info("--submit was not specified, so bids not submitted.");
    }
  }

  private final String THIRTY_ONE_SPACES = "                               ";

  public String header(List<LocalDate> datesInPeriod) {
    StringBuilder result = new StringBuilder();
    result.append("    ");
    datesInPeriod.forEach(date -> result.append(date.getDayOfMonth() % 10));
    result.append("    (fav)");
    return result.toString();
  }

  private String formatLine(List<LocalDate> datesInPeriod, LineScore lineScore,
      Map<PairingKey, Trip> allPairings, YearMonth yearMonth) {
    StringBuilder result = new StringBuilder();
    ThinLine line = lineScore.getThinLine();

    result.append(line.getLineName());

    char dates[] = THIRTY_ONE_SPACES.substring(0, datesInPeriod.size()).toCharArray();
    List<String> supplement = new ArrayList<>();
    for (PairingKey key : line.getPairingKeys()) {
      Trip trip = allPairings.get(key);
      String layover;
      if (lineScore.getMinimumTripsThatMeetMinCredit().containsKey(trip)) {
        layover = "*";
      } else {
        layover = " ";
      }
      for (int i = 0; i < trip.getSections().size(); ++i) {
        Section section = trip.getSections().get(i);

        Proto.Section proto = trip.proto.getSection(i);
        if (datesInPeriod.contains(section.getDepartureDate())) {
          dates[datesInPeriod.indexOf(section.getDepartureDate())] = '.';
        }
        if (proto.hasLayoverAirportCode()) {
          if (layover.length() > 2) {
            layover += ",";
          }
          layover += proto.getLayoverAirportCode();
        }
      }
      TripScore tripScore = new TripScore(trip, bidConfig);
      layover += "/" + String.format("%4d", tripScore.getPoints());
      supplement.add(layover);
    }
    result.append("[");  // TODO fix blend
    result.append(dates);
    result.append("] ");  // fix blend
    result.append(lineScore.isDesirableLine() ? "D " : "U ");
    result.append(String.format("%4d", lineScore.getScore()));
    result.append("/");
    result.append(String.format("%2d", lineScore.getScoreAdjustmentPoints()));
    result.append(" top3:");
    result.append(lineScore.getNHighestCreditsPlusCarryIn());
    result.append("  200: ");
    result.append(lineScore.getNumEquipmentTwoHundredSegments());
    for (String s : supplement) {
      result.append(String.format("%19s ", s));
    }

    return result.toString();
  }

  private Map<PairingKey, Trip> getAllPairings(FlicaService service, YearMonth yearMonth) throws Exception {
    Proto.PairingList pairingList;
    if (!cmdLine.useProto()) {
      String rawPairings = service.getAllPairings(awardDomicile, rank,
          bidConfig.getRound(),
          yearMonth);
      PairingParser pairingParser = new PairingParser(rawPairings, yearMonth,
          cmdLine.parseCanceled());
      pairingList = pairingParser.parse();
    } else {
      DataReader dataReader = new DataReader();
      pairingList = dataReader.readPairings(yearMonth, awardDomicile);
    }

    PairingAdapter pairingAdapter = new PairingAdapter();
    Map<PairingKey, Trip> trips = new HashMap<>();
    for (Proto.Trip protoTrip : pairingList.getTripList()) {
      for (PairingOverride override : bidConfig.getPairingOverrideList()) {
        if (protoTrip.getPairingName().equals(override.getPairingName())) {
          protoTrip = Proto.Trip.newBuilder(protoTrip).setOperatesExcept(
              override.getOperatesExcept()).build();
          break;
        }
      }
      for (Trip trip : pairingAdapter.adaptPairing(protoTrip).getTrips()) {
        logger.fine("Adding pairing key " + trip.getPairingKey());
        trips.put(trip.getPairingKey(), trip);
      }
    }
    return trips;
  }

  private List<ThinLine> getAllLines(FlicaService service, YearMonth yearMonth) throws Exception {
    Proto.ThinLineList lineList;
    if (!cmdLine.useProto()) {
      String rawLines = service.getAllLines(awardDomicile, rank, bidConfig.getRound(),
          yearMonth);
      LineParser lineParser = new LineParser(rawLines);
      lineList = lineParser.parse();
    } else {
      DataReader dataReader = new DataReader();
      lineList = dataReader.readLines(yearMonth, awardDomicile,
          Rank.valueOf(bidConfig.getRank()), bidConfig.getRound());
    }

    List<ThinLine> result = new ArrayList<>();
    lineList.getThinLineList().forEach(line -> result.add(new ThinLine(line)));
    return result;
  }

  private Schedule getSchedule(FlicaService service, YearMonth yearMonth)
      throws IOException, ParseException {
    String scheduleText = service.getSchedule(yearMonth);
    ScheduleParser parser = new ScheduleParser(scheduleText);
    Proto.Schedule proto = parser.parse();
    ScheduleAdapter adapter = new ScheduleAdapter();
    return adapter.adapt(proto);
  }
}
