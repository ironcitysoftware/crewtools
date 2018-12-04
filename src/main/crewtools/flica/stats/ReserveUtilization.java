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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.Award;
import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.DomicileAward;
import crewtools.flica.Proto.Pilot;
import crewtools.flica.Proto.Rank;
import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.Proto.SeniorityList;
import crewtools.flica.Proto.ThinLine;
import crewtools.flica.Proto.ThinLineList;
import crewtools.flica.Proto.ThinPairing;
import crewtools.util.PilotMatcher;

public class ReserveUtilization {
  private final Logger logger = Logger.getLogger(ReserveUtilization.class.getName());

  public static void main(String args[]) throws Exception {
    new ReserveUtilization(args).run();
  }

  private final YearMonth yearMonth;
  private final AwardDomicile awardDomicile;
  private final ScheduleType scheduleType;

  private static final Map<ScheduleType, String> ABBREVIATIONS = ImmutableMap.of(
      ScheduleType.SHORT_CALL_RESERVE, "SCR",
      ScheduleType.LONG_CALL_RESERVE, "LCR");

  public ReserveUtilization(String args[]) throws FileNotFoundException, IOException {
    if (args.length < 2) {
      System.err.println("reserveUtilization.sh 2018-12 CLT [LONG_CALL_RESERVE]");
      System.exit(-1);
    }
    this.yearMonth = YearMonth.parse(args[0]);
    this.awardDomicile = AwardDomicile.valueOf(args[1]);
    if (args.length == 2) {
      this.scheduleType = ScheduleType.LONG_CALL_RESERVE;
    } else {
      this.scheduleType = ScheduleType.valueOf(args[2]);
    }
  }

  public void run() throws Exception {
    DataReader dataReader = new DataReader();
    ThinLineList roundTwo = dataReader.readLines(yearMonth, awardDomicile,
        Rank.CAPTAIN, 2);
    DomicileAward awardContainer = dataReader.readAwards(yearMonth, awardDomicile,
        Rank.CAPTAIN, 2);
    SeniorityList list = dataReader.readSeniorityList(yearMonth);
    PilotMatcher matcher = new PilotMatcher(list);

    Map<Integer, String> info = new TreeMap<>();
    for (ThinLine line : roundTwo.getThinLineList()) {
      if (!isScheduleType(line)) {
        continue;
      }
      Award award = findAward(awardContainer, line.getLineName());
      if (award == null) {
        logger.warning("Line " + line.getLineName() + " not awarded");
        continue;
      }
      Pilot pilot = award.getPilot();
      CrewMember member = Preconditions.checkNotNull(matcher.matchCrewMember(pilot));
      String message = String.format("%s %s %d %s", line.getLineName(),
          getName(scheduleType), member.getEmployeeId(), member.getName());
      info.put(member.getEmployeeId(), message);
    }

    for (int employeeId : info.keySet()) {
      logger.info(info.get(employeeId));
    }
  }

  private String getName(ScheduleType scheduleType) {
    if (ABBREVIATIONS.containsKey(scheduleType)) {
      return ABBREVIATIONS.get(scheduleType);
    } else {
      return scheduleType.name();
    }
  }

  private boolean isScheduleType(ThinLine line) {
    for (ThinPairing pairing : line.getThinPairingList()) {
      for (ScheduleType scheduleType : pairing.getScheduleTypeList()) {
        if (scheduleType.equals(this.scheduleType)) {
          return true;
        }
      }
    }
    return false;
  }

  private Award findAward(DomicileAward awardContainer, String line) {
    for (Award award : awardContainer.getAwardList()) {
      if (award.getLine().equals(line)) {
        return award;
      }
    }
    return null;
  }
}