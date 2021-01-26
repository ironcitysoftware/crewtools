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

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
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
import crewtools.util.Calendar;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SeniorityPredictor {
  private final Logger logger = Logger.getLogger(SeniorityPredictor.class.getName());

  private final AwardDomicile awardDomicile;
  private final Domicile domicile;
  private final Rank rank;
  private final YearMonth startingYearMonth;
  private final int interestingEmployeeId;
  private final Multimap<YearMonth, DatedBasedMove> moves;
  private final List<BaseList> lists;
  private final DataReader dataReader;
  private final Multimap<YearMonth, Integer> terminations;

  public static void main(String[] args) throws Exception {
    new SeniorityPredictor(args).run();
  }

  public SeniorityPredictor(String[] args) throws IOException {
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
  }

  private static class DatedBasedMove {
    public DatedBasedMove(BaseMove baseMove, LocalDate effectiveDate, LocalDate awardDate) {
      this.baseMove = baseMove;
      this.effectiveDate = effectiveDate;
      this.awardDate = awardDate;
    }

    public final BaseMove baseMove;
    public final LocalDate effectiveDate;
    public final LocalDate awardDate;

    @Override
    public int hashCode() {
      return baseMove.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof DatedBasedMove)) {
        return false;
      }
      DatedBasedMove that = (DatedBasedMove) o;
      return baseMove.equals(that.baseMove);
    }

    @Override
    public String toString() {
      return baseMove.toString();
    }
  }

  private Multimap<YearMonth, DatedBasedMove> readCrewMoves(
      PeriodicAwards periodicAwards) {
    Multimap<YearMonth, DatedBasedMove> result = ArrayListMultimap.create();
    for (PeriodicAward award : periodicAwards.getPeriodicAwardList()) {
      LocalDate effective = LocalDate.parse(award.getEffectiveDate());
      YearMonth yearMonth = Calendar.getAssociatedYearMonth(effective);
      LocalDate awardDate = LocalDate.parse(award.getAwardDate());
      for (BaseMove baseMove : award.getBaseMoveList()) {
        result.put(yearMonth,
            new DatedBasedMove(baseMove, LocalDate.parse(award.getEffectiveDate()), awardDate));
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
    ThinLineList roundOneLines = dataReader.readLines(
        startingYearMonth, awardDomicile, rank, FlicaService.BID_ROUND_ONE);
    Optional<DomicileAward> roundTwoAward = Optional.empty();
    Optional<ThinLineList> roundTwoLines = Optional.empty();
    try {
      roundTwoAward = Optional.of(dataReader.readAwards(
          startingYearMonth, awardDomicile, rank, FlicaService.BID_ROUND_TWO));
      roundTwoLines = Optional.of(dataReader.readLines(
          startingYearMonth, awardDomicile, rank, FlicaService.BID_ROUND_TWO));
    } catch (Exception e) {
      logger.log(Level.INFO, "Unable to read round 2", e.getMessage());
    }
    LineInfo startingLineInfo =
        getLineInfo(roundOneAward, roundOneLines, roundTwoAward, roundTwoLines);

    BaseList seniorityList = createBaseListFromSeniority(
        startingYearMonth, pilotsBySeniority, startingLineInfo);
    lists.add(seniorityList);
    seniorityList.setCssClass(interestingEmployeeId, "interesting");

    BaseList awardList = createBaseListFromAward(
        startingYearMonth, pilotsByEmployee, pilotsBySeniority, startingLineInfo);
    // addUnawardedPilots(awardList, seniorityList);
    lists.add(awardList);

    CrewMember interestingPilot = pilotsByEmployee.get(interestingEmployeeId);
    awardList.addWithoutAward(interestingEmployeeId, interestingPilot.getSeniorityId(),
        interestingPilot.getName());
    awardList.setCssClass(interestingEmployeeId, "interesting");

    YearMonth currentYearMonth = startingYearMonth.plusMonths(1);
    List<BaseList> colorize = new ArrayList<>();
    colorize.add(seniorityList);
    colorize.add(awardList);

    YearMonth maxYearMonth = Ordering.natural().max(moves.keySet());
    while (!currentYearMonth.isAfter(maxYearMonth)) {
      BaseList previousList = colorize.get(colorize.size() - 1);
      BaseList currentList = previousList
          .copyWithoutStyles(currentYearMonth, "prediction");
      currentList.setCssClass(interestingEmployeeId, "interesting");
      lists.add(currentList);
      colorize.add(currentList);
      logger.info("Applying base moves for " + currentYearMonth + " ("
          + moves.get(currentYearMonth).size() + ")");
      for (DatedBasedMove baseMove : moves.get(currentYearMonth)) {
        adjustBaseList(pilotsByEmployee, currentList, baseMove, currentYearMonth);
      }
      currentList.removeUnnecessaryAwards();

      // TODO fix multiple award case.
      if (false && dataReader.doesAwardExist(currentYearMonth, awardDomicile, rank, 1)) {
        BaseList anotherAwardList = createBaseListFromAward(
            currentYearMonth, pilotsByEmployee, pilotsBySeniority,
            currentList.getLineInfo());
        anotherAwardList.addWithoutAward(interestingEmployeeId,
            interestingPilot.getSeniorityId(),
            interestingPilot.getName());
        anotherAwardList.setCssClass(interestingEmployeeId, "interesting");
        lists.add(anotherAwardList);
      }
      currentYearMonth = currentYearMonth.plusMonths(1);
    }

    for (int i = 0; i < colorize.size() - 1; ++i) {
      colorizeDiff(colorize.get(i), colorize.get(i + 1));
    }

    new SeniorityRenderer(
        lists, startingYearMonth, domicile).render();
  }

  /**
   * Adds all active, domiciled pilots to the list,
   * as well as the interesting employee id.
   */
  private BaseList createBaseListFromSeniority(
      YearMonth yearMonth,
      Map<Integer, CrewMember> pilotsBySeniority,
      LineInfo lineInfo) {
    CrewPosition position = rank == Rank.CAPTAIN ? CrewPosition.CA : CrewPosition.FO;
    BaseList result = new BaseList(yearMonth, "SYSSEN", lineInfo);
    for (int seniorityId : pilotsBySeniority.keySet()) {
      CrewMember pilot = pilotsBySeniority.get(seniorityId);
      if (pilot.getEmployeeId() != interestingEmployeeId
          && (!pilot.getDomicile().equals(domicile)
              || !pilot.getCrewPosition().equals(position))) {
        continue;
      }
      if (pilot.getStatus().equals(Status.ACTIVE)
          || pilot.getStatus().equals(Status.TRAINING_STATUS)) {
        result.addWithoutAward(pilot.getEmployeeId(), seniorityId, pilot.getName());
      }
    }
    return result;
  }

  private BaseList createBaseListFromAward(
      YearMonth yearMonth,
      Map<Integer, CrewMember> pilotsByEmployee,
      Map<Integer, CrewMember> pilotsBySenioritys,
      LineInfo lineInfo)
      throws FileNotFoundException, IOException {
    DomicileAward roundOneAwards = dataReader.readAwards(
        yearMonth, awardDomicile, rank, 1);
    DomicileAward roundTwoAwards = dataReader.doesAwardExist(
        yearMonth, awardDomicile, rank, 2)
            ? dataReader.readAwards(
                yearMonth, awardDomicile, rank, 2)
            : null;
    BaseList result = new BaseList(yearMonth, "award", lineInfo);
    addCrewMembersFromAward(pilotsByEmployee, roundOneAwards, lineInfo, result);
    if (roundTwoAwards != null) {
      addCrewMembersFromAward(pilotsByEmployee, roundTwoAwards, lineInfo, result);
    }
    result.removeUnnecessaryAwards();
    return result;
  }

  private void addCrewMembersFromAward(
      Map<Integer, CrewMember> pilotsByEmployee,
      DomicileAward domicileAward,
      LineInfo lineInfo,
      BaseList baseList) {
    for (Award award : domicileAward.getAwardList()) {
      int employeeId = award.getPilot().hasEmployeeId()
          ? award.getPilot().getEmployeeId()
          : award.getPilot().getSeniority();  // sic
      CrewMember pilot = pilotsByEmployee.get(employeeId);
      Preconditions.checkNotNull(pilot, "Missing employee id " + employeeId);
      AwardType awardType = lineInfo.getAwardType(award.getLine());
      Preconditions.checkNotNull(awardType, "Unknown line " + award.getLine());
      baseList.add(pilot, awardType);
    }
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
    for (int employeeId : currentList.getAwardOverrideEmployeeIds()) {
      currentList.setCssClass(employeeId, "override");
    }
  }

  private void adjustBaseList(Map<Integer, CrewMember> pilotsByEmployee,
      BaseList baseList, DatedBasedMove dateBasedMove, YearMonth yearMonth) {
    baseList.addAwardDate(dateBasedMove.awardDate);
    if (dateBasedMove.baseMove.getFrom().equals(domicile)) {
      // Rank doesn't matter.  The pilot is leaving the seat-domicile pair.
      for (int employeeId : dateBasedMove.baseMove.getEmployeeIdList()) {
        if (employeeId == interestingEmployeeId) {
          continue;
        }
        if (baseList.containsEmployeeId(employeeId)) {
          baseList.remove(employeeId);
        } else {
          if (!terminations.containsKey(yearMonth)
              || !terminations.get(yearMonth).contains(employeeId)) {
            logger.fine(String.format("%d from %s award is not in %s for %s",
                employeeId, dateBasedMove.effectiveDate, domicile, yearMonth));
          }
        }
      }
    } else if (dateBasedMove.baseMove.getTo().equals(domicile)
        && dateBasedMove.baseMove.getToRank().equals(rank)) {
      for (int employeeId : dateBasedMove.baseMove.getEmployeeIdList()) {
        if (employeeId == interestingEmployeeId) {
          continue;
        }
        if (!baseList.containsEmployeeId(employeeId)) {
          CrewMember pilot = pilotsByEmployee.get(employeeId);
          if (pilot == null) {
            baseList.addWithoutAward(employeeId, 100000 + employeeId, "unknown");
          } else {
            baseList.addWithoutAward(pilot.getEmployeeId(), pilot.getSeniorityId(),
                pilot.getName());
          }
        } else {
          logger.warning(String.format("%d from %s award is already in %s",
              employeeId, dateBasedMove.effectiveDate, domicile));
        }
      }
    }
  }

  private LineInfo getLineInfo(
      DomicileAward roundOneAward,
      ThinLineList roundOneLineList,
      Optional<DomicileAward> roundTwoAward,
      Optional<ThinLineList> roundTwoLineList) {
    Map<String, AwardType> lines = new HashMap<>();
    int numRoundOne = roundOneAward.getAwardCount();
    Preconditions.checkState(numRoundOne == roundOneLineList.getThinLineCount(),
        "Award count does not match line count");
    for (ThinLine line : roundOneLineList.getThinLineList()) {
      Preconditions.checkState(!lines.containsKey(line.getLineName()));
      lines.put(line.getLineName(), AwardType.ROUND1);
    }
    int numRoundTwo = 0;
    int numLongCall = 0;
    int numShortCall = 0;
    if (roundTwoAward.isPresent()) {
      for (Award award : roundTwoAward.get().getAwardList()) {
        if (!award.getLine().startsWith("RES")) {
          numRoundTwo++;
          Preconditions.checkState(!lines.containsKey(award.getLine()));
          lines.put(award.getLine(), AwardType.ROUND2);
        } else {
          boolean lineFound = false;
          for (ThinLine line : roundTwoLineList.get().getThinLineList()) {
            if (line.getLineName().equals(award.getLine())) {
              lineFound = true;
              if (line.getThinPairing(0)
                  .getScheduleType(0) == ScheduleType.LONG_CALL_RESERVE) {
                numLongCall++;
                Preconditions.checkState(!lines.containsKey(award.getLine()));
                lines.put(award.getLine(), AwardType.LCR);
              } else {
                numShortCall++;
                Preconditions.checkState(!lines.containsKey(award.getLine()));
                lines.put(award.getLine(), AwardType.SCR);
                // it is a SCR line
              }
              break;
            }
          }
          Preconditions.checkState(lineFound, "Line not found: " + award.getLine());
        }
      }
      // The numVars are award counts, which should be <= the original line counts.
      Preconditions
          .checkState(numRoundTwo + numLongCall + numShortCall <= roundTwoLineList
              .get().getThinLineCount(), String.format("RD2:%d LCR:%d SCR:%d != %d",
                  numRoundTwo, numLongCall, numShortCall,
                  roundTwoLineList.get().getThinLineCount()));
    }
    return new LineInfo(lines, numRoundOne, numRoundTwo, numLongCall);
  }
}
