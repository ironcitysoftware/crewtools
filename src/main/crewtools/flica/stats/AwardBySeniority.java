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

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.Award;
import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.Domicile;
import crewtools.flica.Proto.DomicileAward;
import crewtools.flica.Proto.Rank;
import crewtools.flica.Proto.SeniorityList;

public class AwardBySeniority {
  public static void main(String args[]) throws Exception {
    AwardDomicile awardDomicile = AwardDomicile.valueOf(args[0]);
    Rank rank = Rank.valueOf(args[1]);
    YearMonth yearMonth = YearMonth.parse(args[2]);
    Integer interestingEmployeeId = Integer.parseInt(args[3]);

    DataReader reader = new DataReader();
    DomicileAward awards = reader.readAwards(
        yearMonth, awardDomicile, rank, 1);

    SeniorityList seniorityList = reader.readSeniorityList(yearMonth);
    Map<Integer, CrewMember> pilots = new HashMap<>();
    Map<Integer, CrewMember> pilotsBySeniority = new HashMap<>();
    for (CrewMember crewMember : seniorityList.getCrewMemberList()) {
      pilots.put(crewMember.getEmployeeId(), crewMember);
      pilotsBySeniority.put(crewMember.getSeniorityId(), crewMember);
    }

    Map<Integer, CrewMember> sortedAward = new TreeMap<>();
    for (Award award : awards.getAwardList()) {
      int employeeId = award.getPilot().getSeniority();  // sic
      CrewMember pilot = pilots.get(employeeId);
      if (pilot == null) {
        System.err.println("Where did " + employeeId + " go ?");
      }
      sortedAward.put(pilot.getSeniorityId(), pilot);
    }

    // ---

    CrewMember interestingEmployee = pilots.get(interestingEmployeeId);
    boolean printed = false;
    boolean awarded = false;
    int interestingSeniorityId = interestingEmployee.getSeniorityId();
    int lastSeniorityId = -1;
    int countFromBottom = 1;

    for (CrewMember pilot : sortedAward.values()) {
      if (awarded) {
        countFromBottom++;
      }
      int theirSeniorityId = pilot.getSeniorityId();
      if (theirSeniorityId == interestingSeniorityId) {
        printed = true;
        awarded = true;
      }
      if (!printed && theirSeniorityId > interestingSeniorityId) {
        System.out.printf("%5d %4d <interesting employee id>\n",
            interestingEmployeeId, interestingSeniorityId);
        printed = true;
        awarded = true;
        countFromBottom++;
      }
      System.out.printf("%5d %4d %s\n",
          pilot.getEmployeeId(), theirSeniorityId, pilot.getName());
      lastSeniorityId = theirSeniorityId;
    }

    int countBelowBottom = 1;
    if (!printed) {
      System.out.println("---");
      for (int i = lastSeniorityId + 1; i < interestingSeniorityId; ++i) {
        CrewMember pilot = pilotsBySeniority.get(i);
        if (pilot.getDomicile().equals(Domicile.valueOf(awardDomicile.name()))) {
          countBelowBottom++;
          System.out.printf("%5d %4d %s <no award>\n", pilot.getEmployeeId(),
              pilot.getSeniorityId(), pilot.getName());
        } else {
          // System.out.printf("%5d %4d %s based in %s\n", pilot.getEmployeeId(),
          // pilot.getSeniorityId(), pilot.getName(), pilot.getDomicile());
        }
      }
      System.out.printf("%5d %4d <interesting employee id>\n",
          interestingEmployeeId, interestingSeniorityId);
    }
    System.out.printf("%d total round 1 lines\n", awards.getAwardList().size());
    if (awarded) {
      System.out.printf(
          "%d from bottom (1=last; this many lines would have to disappear "
              + "to lose round 1)\n",
          countFromBottom);
    } else {
      System.out.printf("%d away from line (need this many lines to win round 1)\n",
          countBelowBottom);
    }
  }
}
