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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.CrewPosition;
import crewtools.flica.Proto.SeniorityList;
import crewtools.flica.Proto.Status;
import crewtools.flica.Proto.ThinLineList;
import crewtools.util.FlicaConfig;

public class Percent {
  private final Logger logger = Logger.getLogger(Percent.class.getName());

  public static void main(String args[]) throws Exception {
    new Percent().run();
  }
  
  private final DataReader dataReader;
  private final int employeeId;
  
  public Percent() throws IOException {
    this.dataReader = new DataReader();
    this.employeeId = Integer.parseInt(new FlicaConfig().getInterestingEmployeeId());
  }
  
  static class Columns {
    public Columns(int employeeId) {
      this.totalActiveCaptains = new Column<>("Active CAs");
      this.totalActiveFirstOfficers = new Column<>("Active FOs");
      this.captainSeniorityInBase = new Column<>(employeeId + " (CA)");
      this.firstOfficerSeniorityInBase = new Column<>(employeeId + " (FO)");
      this.numRoundOneLines = new Column<>("Round One");
    }

    Column<Integer> totalActiveCaptains;
    Column<Integer> totalActiveFirstOfficers;
    Column<Integer> captainSeniorityInBase;
    Column<Integer> firstOfficerSeniorityInBase;
    Column<Integer> numRoundOneLines;
    
    void addToGraphData(GraphData data) {
      data.add(totalActiveCaptains);
      data.add(totalActiveFirstOfficers);
      data.add(numRoundOneLines);
      data.add(firstOfficerSeniorityInBase);
      data.add(captainSeniorityInBase);
    }
  }

  public void run() throws Exception {
    Map<YearMonth, SeniorityList> seniorityLists = dataReader.readSeniorityLists();
    Map<YearMonth, Map<AwardDomicile, ThinLineList>> numLineLists = dataReader.readLines();
    Map<String, GraphData> tokenToDataMap = new HashMap<>();
    
    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      GraphData graphData = getGraphData(Optional.of(awardDomicile), seniorityLists, numLineLists);
      tokenToDataMap.put("$" + awardDomicile + "_SENIORITY_DATA", graphData);
    }
    
    GraphData graphData = getGraphData(Optional.absent(), seniorityLists, numLineLists);
    tokenToDataMap.put("$ALL_SENIORITY_DATA", graphData);

    new ChartRenderer(ImmutableMap.of(), tokenToDataMap, "percent.template",
        "/tmp/percent.html").render();
  }
  
  private GraphData getGraphData(Optional<AwardDomicile> awardDomicile,
      Map<YearMonth, SeniorityList> seniorityLists,
      Map<YearMonth, Map<AwardDomicile, ThinLineList>> numLineLists) {
    QuotedColumn<YearMonth> labelColumn = new QuotedColumn<>("Month");
    GraphData graphData = new GraphData();
    graphData.add(labelColumn);
    Columns columns = new Columns(employeeId);
    for (YearMonth yearMonth : seniorityLists.keySet()) {
      labelColumn.add(yearMonth);
      Integer numLines = getNumRoundOneLines(
          awardDomicile,
          numLineLists.get(yearMonth)); 
      populateForDomicile(columns,
          yearMonth,
          awardDomicile, 
          seniorityLists.get(yearMonth),
          numLines);
    }
    columns.addToGraphData(graphData);
    return graphData;
  }

  private Integer getNumRoundOneLines(Optional<AwardDomicile> awardDomicile,
      Map<AwardDomicile, ThinLineList> lines) {
    if (lines == null) {
      return null;
    }
    if (!awardDomicile.isPresent()) {
      int numLines = 0;
      for (ThinLineList thinLineList : lines.values()) {
        numLines += thinLineList.getThinLineCount();
      }
      return numLines;
    }
    if (!lines.containsKey(awardDomicile.get())) {
      return null; 
    }
    return lines.get(awardDomicile.get()).getThinLineCount();
  }
  
  private void populateForDomicile(Columns columns, YearMonth yearMonth, 
      Optional<AwardDomicile> awardDomicile, SeniorityList list, 
      Integer numRoundOneLines) {
    int totalFirstOfficersInBase = 0;
    int totalCaptainsInBase = 0;
    int firstOfficerSeniorityInBase = 0;
    int captainSeniorityInBase = 0;
    
    for (CrewMember member : list.getCrewMemberList()) {
      if (member.getEmployeeId() == employeeId) {
        if (firstOfficerSeniorityInBase == 0) {
          firstOfficerSeniorityInBase = totalFirstOfficersInBase;
        }
        if (captainSeniorityInBase == 0) {
          captainSeniorityInBase = totalCaptainsInBase; 
        }
      }
      if (!member.getStatus().equals(Status.ACTIVE)) {
        continue;
      }
      // old seniority lists didn't include domicile...
      if (awardDomicile.isPresent() && !member.hasDomicile()) {
        continue;
      }
      if (!awardDomicile.isPresent() 
          || member.getDomicile().name().equals(awardDomicile.get().name())) {
        if (member.getCrewPosition().equals(CrewPosition.FO)) {
          totalFirstOfficersInBase++;
        } else if (member.getCrewPosition().equals(CrewPosition.CA)) {
          totalCaptainsInBase++;
        } else {
          throw new IllegalStateException("Unexpected position: " + member);
        }
      }
    }
    
    columns.totalActiveCaptains.add(totalCaptainsInBase);
    columns.totalActiveFirstOfficers.add(totalFirstOfficersInBase);
    columns.numRoundOneLines.add(numRoundOneLines);
    columns.firstOfficerSeniorityInBase.add(firstOfficerSeniorityInBase);
    columns.captainSeniorityInBase.add(captainSeniorityInBase);
  }
}
