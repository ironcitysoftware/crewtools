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
import java.util.logging.Logger;

import org.joda.time.YearMonth;

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

public class ReserveUtilization {
  private final Logger logger = Logger.getLogger(ReserveUtilization.class.getName());

  public static void main(String args[]) throws Exception {
    new ReserveUtilization(args).run();
  }

  private final YearMonth yearMonth;
  private final AwardDomicile awardDomicile;

  public ReserveUtilization(String args[]) throws FileNotFoundException, IOException {
    yearMonth = YearMonth.parse(args[0]);
    awardDomicile = AwardDomicile.valueOf(args[1]);
  }

  public void run() throws Exception {
    DataReader dataReader = new DataReader();
    ThinLineList roundTwo = dataReader.readLines(yearMonth, awardDomicile,
        Rank.CAPTAIN, 2);
    DomicileAward awardContainer = dataReader.readAwards(yearMonth, awardDomicile,
        Rank.CAPTAIN, 2);
    SeniorityList list = dataReader.readSeniorityList(yearMonth);
    for (ThinLine line : roundTwo.getThinLineList()) {
      if (!isLongCallReserve(line)) {
        continue;
      }
      Award award = findAward(awardContainer, line.getLineName());
      if (award == null) {
        logger.warning("Line " + line.getLineName() + " not awarded");
        continue;
      }
      Pilot pilot = award.getPilot();
      CrewMember member = findCrewMember(list, pilot);
      logger.info(
          line.getLineName() + " LCR " + member.getEmployeeId() + " "
              + member.getName());
    }
  }

  private boolean isLongCallReserve(ThinLine line) {
    for (ThinPairing pairing : line.getThinPairingList()) {
      for (ScheduleType scheduleType : pairing.getScheduleTypeList()) {
        if (scheduleType.equals(ScheduleType.LONG_CALL_RESERVE)) {
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

  private CrewMember findCrewMember(SeniorityList seniorityList, Pilot pilot) {
    String formattedName = (pilot.getFirstMiddleName()
        + " " + pilot.getLastNameSuffix()).toUpperCase();
    for (CrewMember member : seniorityList.getCrewMemberList()) {
      if (member.getName().equals(formattedName)) {
        return member;
      }
    }
    throw new IllegalStateException(formattedName + " not in seniority list");
  }
}