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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Award;
import crewtools.flica.Proto.BaseMove;
import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.CrewPosition;
import crewtools.flica.Proto.Domicile;
import crewtools.flica.Proto.DomicileAward;
import crewtools.flica.Proto.KnownTermination;
import crewtools.flica.Proto.PeriodicAward;
import crewtools.flica.Proto.PeriodicAwards;
import crewtools.flica.Proto.Rank;
import crewtools.flica.Proto.ScheduleType;
import crewtools.flica.Proto.SeniorityList;
import crewtools.flica.Proto.Status;
import crewtools.flica.Proto.ThinLine;
import crewtools.flica.Proto.ThinLineList;
import crewtools.flica.stats.BaseList.Member;

public class SeniorityPredictor {
  private final Logger logger = Logger.getLogger(SeniorityPredictor.class.getName());

  private final static int DEFAULT_MONTHLY_LINE_INCREASE = 1;

  private final AwardDomicile awardDomicile;
  private final Domicile domicile;
  private final Rank rank;
  private final YearMonth startingYearMonth;
  private final int interestingEmployeeId;
  private final Multimap<YearMonth, DatedBaseMove> moves;
  private final List<BaseList> lists;
  private final int monthlyLineIncrease = DEFAULT_MONTHLY_LINE_INCREASE;
  private final Map<YearMonth, LineCount> lineCounts;
  private final DataReader dataReader;
  private final Multimap<YearMonth, Integer> terminations;

  public static void main(String args[]) throws Exception {
    new SeniorityPredictor(args).run();
  }

  public SeniorityPredictor(String args[]) throws IOException {
    if (args.length != 4) {
      System.err.println("SeniorityPredictor BASE CAPTAIN|FIRST_OFFICER "
          + "2019-01 00000");
      System.exit(-1);
    }
    this.awardDomicile = AwardDomicile.valueOf(args[0]);
    this.domicile = Domicile.valueOf(awardDomicile.name());
    this.rank = Rank.valueOf(args[1]);
    this.startingYearMonth = YearMonth.parse(args[2]);
    this.interestingEmployeeId = Integer.parseInt(args[3]);
    this.dataReader = new DataReader();
    PeriodicAwards periodicAwards = dataReader.readPeriodicAwards();
    this.moves = readCrewMoves(periodicAwards);
    this.terminations = readTerminations(periodicAwards);
    this.lists = new ArrayList<>();
    this.lineCounts = new HashMap<>();
  }

  private class DatedBaseMove {
    public DatedBaseMove(BaseMove baseMove, LocalDate localDate) {
      this.baseMove = baseMove;
      this.localDate = localDate;
    }

    public final BaseMove baseMove;
    public final LocalDate localDate;

    @Override
    public int hashCode() {
      return baseMove.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || !(o instanceof DatedBaseMove)) {
        return false;
      }
      DatedBaseMove that = (DatedBaseMove) o;
      return baseMove.equals(that.baseMove);
    }

    @Override
    public String toString() {
      return baseMove.toString();
    }
  }

  private Multimap<YearMonth, DatedBaseMove> readCrewMoves(
      PeriodicAwards periodicAwards) {
    Multimap<YearMonth, DatedBaseMove> result = ArrayListMultimap.create();
    for (PeriodicAward award : periodicAwards.getPeriodicAwardList()) {
      LocalDate effective = LocalDate.parse(award.getEffectiveDate());
      YearMonth yearMonth = new YearMonth(effective.getYear(),
          effective.getMonthOfYear());
      for (BaseMove baseMove : award.getBaseMoveList()) {
        result.put(yearMonth,
            new DatedBaseMove(baseMove, LocalDate.parse(award.getAwardDate())));
      }
    }
    return result;
  }

  private Multimap<YearMonth, Integer> readTerminations(PeriodicAwards periodicAwards) {
    Multimap<YearMonth, Integer> result = ArrayListMultimap.create();
    for (KnownTermination kt : periodicAwards.getKnownTerminationList()) {
      YearMonth yearMonth = YearMonth.parse(kt.getYearMonth());
      for (int employeeId : kt.getEmployeeIdList()) {
        result.put(yearMonth, employeeId);
      }
    }
    return result;
  }

  public void run() throws Exception {
    DataReader reader = new DataReader();
    SeniorityList seniorityListProto = reader.readSeniorityList(startingYearMonth);
    // Specified seniority list.
    Map<Integer, CrewMember> pilotsByEmployee = new HashMap<>();
    Map<Integer, CrewMember> pilotsBySeniority = new TreeMap<>();
    for (CrewMember crewMember : seniorityListProto.getCrewMemberList()) {
      pilotsByEmployee.put(crewMember.getEmployeeId(), crewMember);
      pilotsBySeniority.put(crewMember.getSeniorityId(), crewMember);
    }

    DomicileAward roundOneAward = dataReader.readAwards(
        startingYearMonth, awardDomicile, rank, FlicaService.BID_ROUND_ONE);
    DomicileAward roundTwoAward = dataReader.readAwards(
        startingYearMonth, awardDomicile, rank, FlicaService.BID_ROUND_TWO);
    ThinLineList roundTwoLines = dataReader.readLines(
        startingYearMonth, awardDomicile, rank, FlicaService.BID_ROUND_TWO);
    lineCounts.put(startingYearMonth,
        getLineCount(roundOneAward, roundTwoAward, roundTwoLines));

    BaseList seniorityList = createBaseListFromSeniority(
        startingYearMonth, pilotsBySeniority);
    lists.add(seniorityList);
    seniorityList.setCssClass(interestingEmployeeId, "interesting");

    BaseList awardList = createBaseListFromAward(
        startingYearMonth, pilotsByEmployee, pilotsBySeniority);
    addUnawardedPilots(awardList, seniorityList);
    lists.add(awardList);

    CrewMember interestingPilot = pilotsByEmployee.get(interestingEmployeeId);
    awardList.add(interestingEmployeeId, interestingPilot.getSeniorityId(),
        interestingPilot.getName());
    awardList.setCssClass(interestingEmployeeId, "interesting");

    YearMonth currentYearMonth = startingYearMonth.plusMonths(1);
    List<BaseList> colorize = new ArrayList<>();
    colorize.add(seniorityList);
    colorize.add(awardList);
    while (moves.containsKey(currentYearMonth)) {
      lineCounts.put(currentYearMonth,
          lineCounts.get(currentYearMonth.minusMonths(1)).increment(monthlyLineIncrease));
      BaseList previousList = colorize.get(colorize.size() - 1);
      BaseList currentList = previousList
          .copyWithoutStyles(currentYearMonth, "prediction");
      currentList.setCssClass(interestingEmployeeId, "interesting");
      lists.add(currentList);
      colorize.add(currentList);
      for (DatedBaseMove baseMove : moves.get(currentYearMonth)) {
        adjustBaseList(pilotsByEmployee, currentList, baseMove, currentYearMonth);
      }
      if (dataReader.doesAwardExist(currentYearMonth, awardDomicile, rank, 1)) {
        BaseList anotherAwardList = createBaseListFromAward(
            currentYearMonth, pilotsByEmployee, pilotsBySeniority);
        addUnawardedPilots(anotherAwardList, seniorityList);
        anotherAwardList.add(interestingEmployeeId, interestingPilot.getSeniorityId(),
            interestingPilot.getName());
        anotherAwardList.setCssClass(interestingEmployeeId, "interesting");
        lists.add(anotherAwardList);
      }
      currentYearMonth = currentYearMonth.plusMonths(1);
    }

    for (int i = 0; i < colorize.size() - 1; ++i) {
      colorizeDiff(colorize.get(i), colorize.get(i + 1));
    }

    new SeniorityRenderer(lists, lineCounts,
        startingYearMonth, domicile).render();
  }

  private void addUnawardedPilots(BaseList awardList, BaseList seniorityList) {
    int juniorAwardedSeniorityId = awardList.getJuniorSeniorityId();
    for (Member member : seniorityList.getMembers()) {
      if (member.seniorityId > juniorAwardedSeniorityId) {
        awardList.add(member);
      }
    }
  }

  /**
   * Adds all active, domiciled pilots to the list,
   * as well as the interesting employee id.
   */
  private BaseList createBaseListFromSeniority(
      YearMonth yearMonth,
      Map<Integer, CrewMember> pilotsBySeniority) {
    BaseList result = new BaseList(yearMonth, "SYSSEN");
    for (int seniorityId : pilotsBySeniority.keySet()) {
      CrewMember pilot = pilotsBySeniority.get(seniorityId);
      if (pilot.getEmployeeId() != interestingEmployeeId
          && (!pilot.getDomicile().equals(domicile)
              || !pilot.getCrewPosition().equals(CrewPosition.CA))) {
        continue;
      }
      if (pilot.getStatus().equals(Status.ACTIVE)
          || pilot.getStatus().equals(Status.TRAINING_STATUS)) {
        result.add(pilot.getEmployeeId(), seniorityId, pilot.getName());
      }
    }
    return result;
  }

  private BaseList createBaseListFromAward(
      YearMonth yearMonth,
      Map<Integer, CrewMember> pilotsByEmployee,
      Map<Integer, CrewMember> pilotsBySenioritys)
      throws FileNotFoundException, IOException {
    DomicileAward roundOneAwards = dataReader.readAwards(
        yearMonth, awardDomicile, rank, 1);
    DomicileAward roundTwoAwards = dataReader.doesAwardExist(
        yearMonth, awardDomicile, rank, 2)
            ? dataReader.readAwards(
                yearMonth, awardDomicile, rank, 2)
            : null;
    BaseList result = new BaseList(yearMonth, "award");
    for (CrewMember pilot : getCrewMembersFromAward(pilotsByEmployee, roundOneAwards)) {
      result.add(pilot);
    }
    if (roundTwoAwards != null) {
      for (CrewMember pilot : getCrewMembersFromAward(pilotsByEmployee, roundTwoAwards)) {
        result.add(pilot);
      }
    }
    return result;
  }

  private List<CrewMember> getCrewMembersFromAward(
      Map<Integer, CrewMember> pilotsByEmployee, DomicileAward domicileAward) {
    List<CrewMember> result = new ArrayList<>();
    for (Award award : domicileAward.getAwardList()) {
      int employeeId = award.getPilot().hasEmployeeId()
          ? award.getPilot().getEmployeeId()
          : award.getPilot().getSeniority();  // sic
      CrewMember pilot = pilotsByEmployee.get(employeeId);
      if (pilot == null) {
        System.err.println("Where did " + employeeId + " go ?");
      }
      result.add(pilot);
    }
    return result;
  }

  private void colorizeDiff(BaseList previousList, BaseList currentList) {
    for (int employeeId : previousList.getEmployeeIds()) {
      if (employeeId != interestingEmployeeId
          && !currentList.containsEmployeeId(employeeId)) {
        previousList.setCssClass(employeeId, "departed");
      }
    }
    for (int employeeId : currentList.getEmployeeIds()) {
      if (!previousList.containsEmployeeId(employeeId)) {
        currentList.setCssClass(employeeId, "arrived");
      }
    }
  }

  private void adjustBaseList(Map<Integer, CrewMember> pilotsByEmployee,
      BaseList baseList, DatedBaseMove datedBaseMove, YearMonth yearMonth) {
    if (datedBaseMove.baseMove.getFrom().equals(domicile)) {
      for (int employeeId : datedBaseMove.baseMove.getEmployeeIdList()) {
        if (employeeId == interestingEmployeeId) {
          continue;
        }
        if (baseList.containsEmployeeId(employeeId)) {
          baseList.remove(employeeId);
        } else {
          if (!terminations.containsKey(yearMonth)
              || !terminations.get(yearMonth).contains(employeeId)) {
            logger.warning(String.format("%d from %s award is not in %s for %s",
                employeeId, datedBaseMove.localDate, domicile, yearMonth));
          }
        }
      }
    } else if (datedBaseMove.baseMove.getTo().equals(domicile)) {
      for (int employeeId : datedBaseMove.baseMove.getEmployeeIdList()) {
        if (employeeId == interestingEmployeeId) {
          continue;
        }
        if (!baseList.containsEmployeeId(employeeId)) {
          CrewMember pilot = pilotsByEmployee.get(employeeId);
          if (pilot == null) {
            baseList.add(employeeId, 100000 + employeeId, "unknown");
          } else {
            baseList.add(pilot);
          }
        } else {
          logger.warning(String.format("%d from %s award is already in %s",
              employeeId, datedBaseMove.localDate, domicile));
        }
      }
    }
  }

  private LineCount getLineCount(
      DomicileAward roundOneAward,
      DomicileAward roundTwoAward,
      ThinLineList roundTwoLineList) {
    int numRoundOne = roundOneAward.getAwardCount();
    int numRoundTwo = 0;
    int numLongCall = 0;
    for (Award award : roundTwoAward.getAwardList()) {
      if (!award.getLine().startsWith("RES")) {
        numRoundTwo++;
      } else {
        boolean lineFound = false;
        for (ThinLine line : roundTwoLineList.getThinLineList()) {
          if (line.getLineName().equals(award.getLine())) {
            lineFound = true;
            if (line.getThinPairing(0)
                .getScheduleType(0) == ScheduleType.LONG_CALL_RESERVE) {
              numLongCall++;
            } else {
              // it is a SCR line
            }
            break;
          }
        }
        Preconditions.checkState(lineFound, "Line not found: " + award.getLine());
      }
    }
    return new LineCount(numRoundOne, numRoundTwo, numLongCall);
  }
}
