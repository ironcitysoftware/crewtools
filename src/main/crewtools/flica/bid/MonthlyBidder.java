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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;

import crewtools.flica.AwardDomicile;
import crewtools.flica.CachingFlicaService;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto;
import crewtools.flica.Proto.Rank;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.parser.LineParser;
import crewtools.flica.parser.PairingParser;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Section;
import crewtools.flica.pojo.ThinLine;
import crewtools.flica.pojo.Trip;
import crewtools.flica.stats.DataReader;
import crewtools.util.FlicaConfig;

public class MonthlyBidder {
  private final Logger logger = Logger.getLogger(MonthlyBidder.class.getName());
  private final boolean submitBids;
  private final boolean useCachingService;
  private final boolean parseCanceled;
  private final boolean softDaysOff;
  private final YearMonth yearMonth;
  private final boolean useProto;
  private final MonthlyBidderConfig config;

  public static void main(String args[]) throws Exception {
    new MonthlyBidder(args).run();
  }

  public MonthlyBidder(String args[]) {
    boolean submitBids = false;
    boolean useCachingService = false;
    boolean parseCanceled = false;
    boolean softDaysOff = false;
    boolean useProto = false;
    YearMonth yearMonth = null;
    for (String arg : args) {
      if (arg.equals("--submit")) {
        submitBids = true;
      } else if (arg.equals("--cache")) {
        useCachingService = true;
      } else if (arg.equals("--canceled")) {
        parseCanceled = true;
      } else if (arg.equals("--softDaysOff")) {
        softDaysOff = true;
      } else if (arg.equals("--proto")) {
        useProto = true;
      } else if (arg.startsWith("--ym")) {
        yearMonth = YearMonth.parse(arg.split("=")[1]);
      }
    }
    this.submitBids = submitBids;
    this.useCachingService = useCachingService;
    this.parseCanceled = parseCanceled;
    this.softDaysOff = softDaysOff;
    this.yearMonth = Preconditions.checkNotNull(yearMonth, "Need --ym=");
    this.useProto = useProto;
    // TODO move everything to config.
    this.config = new MonthlyBidderConfig();
    logger.info("Submit bids    (--submit)     : " + submitBids);
    logger.info("Use cache      (--cache)      : " + useCachingService);
    logger.info("Parse canceled (--canceled)   : " + parseCanceled);
    logger.info("Soft days off  (--softDaysOff): " + softDaysOff); 
    logger.info("year month     (--ym=yyyy-mm) : " + yearMonth);
    logger.info("Use proto      (--proto)      : " + useProto);
  }
  
  public void run() throws Exception {
    runForMonthlyBid();
  }

  private void runForMonthlyBid() throws Exception {
    FlicaConnection connection = new FlicaConnection(new FlicaConfig());
    FlicaService service;
    if (useCachingService) {
      service = new CachingFlicaService(connection);
    } else {
      service = new FlicaService(connection);
      service.connect();
    }

    int lastDateOfMonth = yearMonth.toLocalDate(1).dayOfMonth().getMaximumValue(); 
    Map<PairingKey, Trip> pairings = getAllPairings(service, yearMonth);
    logger.info(pairings.size() + " pairings read for " + yearMonth);
    List<ThinLine> lines = getAllLines(service, yearMonth);
    logger.info(lines.size() + " lines read for " + yearMonth);
    Map<String, ThinLine> linesByName = new HashMap<>();

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
      LineScore lineScore = new LineScore(config, yearMonth, line, trips);
      if (softDaysOff || lineScore.isDesirableLine(config)) {
        lineScores.add(lineScore);
      }
    }
    Collections.sort(lineScores, new MonthlyBidStrategy(config));

    logger.info("Computed bids:");
    
    System.out.println(header(lastDateOfMonth));
    LinkedList<String> bids = new LinkedList<>();
    for (LineScore lineScore : lineScores) {
      if (!bids.contains(lineScore.getLineName())) {
        bids.add(lineScore.getLineName());
        String text = formatLine(lastDateOfMonth, lineScore, pairings, yearMonth);
        System.out.println(text);
      }
    }

    if (submitBids) {
      logger.info("Submitting bids!");
      service.submitLineBid(/* round */ 1, yearMonth, bids);
    } else {
      logger.info("--submit was not specified, so bids not submitted.");
    }
  }

  private final String THIRTY_ONE_SPACES = "                               ";

  public String header(int lastDateOfMonth) {
    StringBuilder result = new StringBuilder();
    result.append("    ");
    for (int i = 1; i < lastDateOfMonth + 1; ++i) {
      result.append(i % 10);
    }
    result.append("     (GSP) need  (RON)");
    return result.toString();
  }

  private String formatLine(int lastDateOfMonth, LineScore lineScore,
      Map<PairingKey, Trip> allPairings, YearMonth yearMonth) {
    StringBuilder result = new StringBuilder();
    ThinLine line = lineScore.getThinLine();
    
    result.append(line.getLineName());
    
    char dates[] = THIRTY_ONE_SPACES.substring(0, lastDateOfMonth).toCharArray();
    List<String> supplement = new ArrayList<>();
    for (PairingKey key : line.getPairingKeys()) {
      Trip trip = allPairings.get(key);
      String layover;
      if (lineScore.getMinimumTripsThatMeetMinCredit().containsKey(trip)) {
        layover = "*";
      } else {
        layover = " ";
      }
      for (int i = 0; i < trip.sections.size(); ++i) {
        Section section = trip.sections.get(i);

        Proto.Section proto = trip.proto.getSection(i);
        if (section.getDepartureDate().getMonthOfYear() == 
            yearMonth.getMonthOfYear()) {
          dates[section.getDepartureDate().getDayOfMonth() - 1] = '.';
        }
        if (proto.hasLayoverAirportCode()) {
          if (layover.length() > 2) {
            layover += ",";
          }
          layover += proto.getLayoverAirportCode();
        }
      }
      layover += "/" + trip.credit;
      supplement.add(layover);
    }
    result.append("[");  // TODO fix blend
    result.append(dates);
    result.append("]  ");  // fix blend
    result.append(lineScore.isDesirableLine(config) ? "D " : "U ");
    result.append(lineScore.getGspCredit());
    result.append(" ");
    result.append(config.getRequiredCredit().minus(lineScore.getGspCredit()));
    result.append(" ");
    result.append(lineScore.getGspOvernightPeriod());
    result.append("  ");
    if (lineScore.hasEquipmentTwoHundredSegments()) {
      result.append("2");
    } else {
      result.append(" ");
    }
    for (String s : supplement) {
      result.append(String.format("%19s ", s));
    }
    
    return result.toString();
  }
  
  private Map<PairingKey, Trip> getAllPairings(FlicaService service, YearMonth yearMonth) throws Exception {
    Proto.PairingList pairingList;
    if (!useProto) {
      String rawPairings = service.getAllPairings(AwardDomicile.CLT, Rank.FIRST_OFFICER, 1, yearMonth);
      PairingParser pairingParser = new PairingParser(rawPairings, yearMonth, parseCanceled);
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
      for (Trip trip : pairingAdapter.adaptPairing(protoTrip).getTrips()) {
        logger.fine("Adding pairing key " + trip.getPairingKey());
        trips.put(trip.getPairingKey(), trip);
      }
    }
    return trips;
  }

  private List<ThinLine> getAllLines(FlicaService service, YearMonth yearMonth) throws Exception {
    Proto.ThinLineList lineList;
    if (!useProto) {
      String rawLines = service.getAllLines(AwardDomicile.CLT, Rank.FIRST_OFFICER, 1, yearMonth);
      LineParser lineParser = new LineParser(rawLines);
      lineList = lineParser.parse();
    } else {
      String filename = new DataReader().getLineFilename(yearMonth, AwardDomicile.CLT);
      Proto.ThinLineList.Builder builder = Proto.ThinLineList.newBuilder();
      FileInputStream inputStream = new FileInputStream(new File(filename));
      builder.mergeFrom(inputStream);
      lineList = builder.build();
    }

    List<ThinLine> result = new ArrayList<>();
    for (Proto.ThinLine protoThinLine : lineList.getThinLineList()) {
      result.add(new ThinLine(protoThinLine));
    }
    return result;
  }
}
