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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.SeniorityList;
import crewtools.flica.Proto.ThinLineList;
import crewtools.util.FlicaConfig;

public class DataReader {
  private final Logger logger = Logger.getLogger(DataReader.class.getName());
  private final String dataDir;
  
  public DataReader() throws IOException {
    dataDir = new FlicaConfig().getDataDirectory();
  }
  
//  public static void main(String args[]) throws Exception {
//    DataReader dataReader = new DataReader();
//    File f = new File(dataReader.getPairingFilename(YearMonth.parse("2018-01"), AwardDomicile.CLT));
//    PairingList.Builder builder = PairingList.newBuilder();
//    builder.mergeFrom(new FileInputStream(f));
//    PairingList list = builder.build();
//    for (Trip trip : list.getTripList()) {
//      if (trip.getPairingName().equals("L7073")) {
//        System.out.println(trip);
//      }
//    }
//  }
  
  public String getSeniorityFilename(YearMonth yearMonth) {
    return dataDir + "seniority-" + yearMonth + ".io";
  }
  
  public String getLineFilename(YearMonth yearMonth, AwardDomicile awardDomicile) {
    return dataDir
        + "lines-" 
        + awardDomicile.name().toLowerCase() 
        + "-"+ yearMonth + ".io";
  }

  public String getPairingFilename(YearMonth yearMonth, AwardDomicile awardDomicile) {
    return dataDir 
        + "pairings-" 
        + awardDomicile.name().toLowerCase() 
        + "-"+ yearMonth + ".io";
  }

  public Map<YearMonth, SeniorityList> readSeniorityLists() throws Exception {
    Map<YearMonth, SeniorityList> lists = new TreeMap<>();
    for (YearMonth yearMonth = YearMonth.parse("2017-1");
        !yearMonth.equals(YearMonth.parse("2018-12").plusMonths(1));
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
        File line = new File(getLineFilename(yearMonth, awardDomicile));
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
}
