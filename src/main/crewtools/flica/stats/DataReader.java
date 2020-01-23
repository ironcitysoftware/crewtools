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

package crewtools.flica.stats;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.protobuf.TextFormat;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.DomicileAward;
import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.PeriodicAwards;
import crewtools.flica.Proto.Rank;
import crewtools.flica.Proto.SeniorityList;
import crewtools.flica.Proto.ThinLineList;
import crewtools.rpc.Proto.FlightListFile;
import crewtools.util.FlicaConfig;

public class DataReader {
  private final Logger logger = Logger.getLogger(DataReader.class.getName());
  private final String dataDir;

  public DataReader() throws IOException {
    dataDir = FlicaConfig.readConfig().getDataDirectory();
  }

  public String getSeniorityFilename(YearMonth yearMonth) {
    return dataDir + "seniority-" + yearMonth + ".io";
  }

  public String getPeriodicAwardFilename() {
    return dataDir + "periodic-awards.txt";
  }

  public String getTimetableFilename(YearMonth yearMonth) {
    return dataDir + "timetable-" + yearMonth + ".io";
  }

  public String getLineFilename(YearMonth yearMonth, AwardDomicile awardDomicile,
      Rank rank, int round) {
    String rankText = round == 1 ? "" : "-" + rank.name().toLowerCase();
    return dataDir
        + "lines-"
        + awardDomicile.name().toLowerCase()
        + "-" + yearMonth
        + rankText
        + "-rd" + round
        + ".io";
  }

  public String getPairingFilename(YearMonth yearMonth, AwardDomicile awardDomicile) {
    return dataDir
        + "pairings-"
        + awardDomicile.name().toLowerCase()
        + "-"+ yearMonth + ".io";
  }

  public String getAwardFilename(YearMonth yearMonth, AwardDomicile awardDomicile,
      Rank rank, int round) {
    return dataDir
        + "award-"
        + awardDomicile.name().toLowerCase()
        + "-" + yearMonth
        + "-" + rank.name().toLowerCase()
        + "-rd" + round
        + ".io";
  }

  public boolean doesAwardExist(YearMonth yearMonth, AwardDomicile awardDomicile,
      Rank rank, int round) {
    return new File(getAwardFilename(yearMonth, awardDomicile, rank, round)).exists();
  }

  private static YearMonth MAXIMUM_SENIORITY_LIST = YearMonth.parse("2023-12");

  public Map<YearMonth, SeniorityList> readSeniorityLists() throws Exception {
    Map<YearMonth, SeniorityList> lists = new TreeMap<>();
    for (YearMonth yearMonth = YearMonth.parse("2017-1");
        !yearMonth.equals(MAXIMUM_SENIORITY_LIST.plusMonths(1));
        yearMonth = yearMonth.plusMonths(1)) {
      File pdf = new File(getSeniorityFilename(yearMonth));
      if (!pdf.exists()) {
        continue;
      }
      logger.info("Reading " + pdf.getAbsolutePath());
      SeniorityList.Builder builder = SeniorityList.newBuilder();
      builder.mergeFrom(new FileInputStream(pdf));
      lists.put(yearMonth, builder.build());
    }
    return lists;
  }

  private static final int MONTHS_IN_PAST_CUTOFF = 60;
  private static final int MAX_STRIKES = 2;

  public Map<YearMonth, Map<AwardDomicile, ThinLineList>> readLines() throws Exception {
    Map<YearMonth, Map<AwardDomicile, ThinLineList>> result = new HashMap<>();
    YearMonth startingMonth = YearMonth.now().plusMonths(1);
    int monthsInPast = 0;
    int strikes = 0;
    while (strikes <= MAX_STRIKES
        && monthsInPast < MONTHS_IN_PAST_CUTOFF) {
      YearMonth yearMonth = startingMonth.minusMonths(monthsInPast);
      boolean anyFound = false;
      for (AwardDomicile awardDomicile : AwardDomicile.values()) {
        // TODO round 2
        for (int round = 1; round < 2; ++round) {
          File line = new File(
              getLineFilename(yearMonth, awardDomicile, Rank.CAPTAIN, round));
          if (!line.exists()) {
            continue;
          }
          anyFound = true;
          logger.info("Reading " + line.getAbsolutePath());
          ThinLineList.Builder builder = ThinLineList.newBuilder();
          builder.mergeFrom(new FileInputStream(line));
          if (!result.containsKey(yearMonth)) {
            result.put(yearMonth, new HashMap<>());
          }
          result.get(yearMonth).put(awardDomicile, builder.build());
        }
      }
      if (!anyFound) {
        strikes++;
      } else {
        strikes = 0;
      }
      monthsInPast++;
    }
    return result;
  }

  public Map<YearMonth, Map<AwardDomicile, PairingList>> readPairings() throws Exception {
    Map<YearMonth, Map<AwardDomicile, PairingList>> result = new HashMap<>();
    YearMonth startingMonth = YearMonth.now().plusMonths(1);
    int monthsInPast = 0;
    int strikes = 0;
    while (strikes <= MAX_STRIKES
        && monthsInPast < MONTHS_IN_PAST_CUTOFF) {
      YearMonth yearMonth = startingMonth.minusMonths(monthsInPast);
      boolean anyFound = false;
      for (AwardDomicile awardDomicile : AwardDomicile.values()) {
        File line = new File(getPairingFilename(yearMonth, awardDomicile));
        if (!line.exists()) {
          continue;
        }
        anyFound = true;
        logger.info("Reading " + line.getAbsolutePath());
        PairingList.Builder builder = PairingList.newBuilder();
        builder.mergeFrom(new FileInputStream(line));
        if (!result.containsKey(yearMonth)) {
          result.put(yearMonth, new HashMap<>());
        }
        result.get(yearMonth).put(awardDomicile, builder.build());
      }
      if (!anyFound) {
        strikes++;
      } else {
        strikes = 0;
      }
      monthsInPast++;
    }
    return result;
  }

  public ThinLineList readLines(YearMonth yearMonth, AwardDomicile awardDomicile,
      Rank rank, int round)
      throws FileNotFoundException, IOException {
    File line = new File(getLineFilename(yearMonth, awardDomicile, rank, round));
    Preconditions.checkState(line.exists(),
        "File doesn't exist: " + line.getAbsolutePath());
    logger.info("Reading " + line.getAbsolutePath());
    ThinLineList.Builder builder = ThinLineList.newBuilder();
    builder.mergeFrom(new FileInputStream(line));
    return builder.build();
  }

  public PairingList readPairings(YearMonth yearMonth, AwardDomicile awardDomicile)
      throws FileNotFoundException, IOException {
    File pairing = new File(getPairingFilename(yearMonth, awardDomicile));
    Preconditions.checkState(pairing.exists(),
        "File doesn't exist: " + pairing.getAbsolutePath());
    logger.info("Reading " + pairing.getAbsolutePath());
    PairingList.Builder builder = PairingList.newBuilder();
    builder.mergeFrom(new FileInputStream(pairing));
    return builder.build();
  }

  public DomicileAward readAwards(YearMonth yearMonth, AwardDomicile awardDomicile,
      Rank rank, int round)
      throws FileNotFoundException, IOException {
    File award = new File(getAwardFilename(yearMonth, awardDomicile, rank, round));
    Preconditions.checkState(award.exists(),
        "File doesn't exist: " + award.getAbsolutePath());
    logger.info("Reading " + award.getAbsolutePath());
    DomicileAward.Builder builder = DomicileAward.newBuilder();
    builder.mergeFrom(new FileInputStream(award));
    return builder.build();
  }

  public SeniorityList readSeniorityList(YearMonth yearMonth)
      throws FileNotFoundException, IOException {
    File seniority = new File(getSeniorityFilename(yearMonth));
    Preconditions.checkState(seniority.exists(),
        "File doesn't exist: " + seniority.getAbsolutePath());
    logger.info("Reading " + seniority.getAbsolutePath());
    SeniorityList.Builder builder = SeniorityList.newBuilder();
    builder.mergeFrom(new FileInputStream(seniority));
    return builder.build();
  }

  public PeriodicAwards readPeriodicAwards() throws FileNotFoundException, IOException {
    File periodicAward = new File(getPeriodicAwardFilename());
    Preconditions.checkState(periodicAward.exists(),
        "File doesn't exist: " + periodicAward.getAbsolutePath());
    logger.info("Reading " + periodicAward.getAbsolutePath());
    PeriodicAwards.Builder builder = PeriodicAwards.newBuilder();
    TextFormat.getParser().merge(
        Files.toString(periodicAward, StandardCharsets.UTF_8), builder);
    return builder.build();
  }

  public FlightListFile readTimetable(YearMonth yearMonth)
      throws FileNotFoundException, IOException {
    File flightList = new File(getTimetableFilename(yearMonth));
    Preconditions.checkState(flightList.exists(),
        "File doesn't exist: " + flightList.getAbsolutePath());
    logger.info("Reading " + flightList.getAbsolutePath());
    try (InputStream input = java.nio.file.Files.newInputStream(flightList.toPath())) {
      return FlightListFile.parseFrom(input);
    }
  }
}
