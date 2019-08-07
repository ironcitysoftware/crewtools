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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.ThinLineList;
import crewtools.flica.Proto.Trip;
import crewtools.util.Period;

public class BlockReport {
  private final Logger logger = Logger.getLogger(BlockReport.class.getName());

  public static void main(String args[]) throws Exception {
    new BlockReport().run();
  }

  private final DataReader dataReader;

  public BlockReport() throws IOException {
    this.dataReader = new DataReader();
  }

  public void run() throws Exception {
    Map<YearMonth, Map<AwardDomicile, ThinLineList>> lineLists = dataReader.readLines();
    Map<YearMonth, Map<AwardDomicile, PairingList>> pairingLists = dataReader
        .readPairings();
    Map<String, GraphData> lineTokenToDataMap = new HashMap<>();
    Map<String, GraphData> blockTokenToDataMap = new HashMap<>();

    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      lineTokenToDataMap.put("$" + awardDomicile + "_DATA",
          getLineGraphData(Optional.of(awardDomicile), lineLists));
      blockTokenToDataMap.put("$" + awardDomicile + "_DATA",
          getBlockGraphData(Optional.of(awardDomicile), lineLists, pairingLists));
    }

    lineTokenToDataMap.put("$ALL_DATA",
        getLineGraphData(Optional.absent(), lineLists));
    blockTokenToDataMap.put("$ALL_DATA",
        getBlockGraphData(Optional.absent(), lineLists, pairingLists));

    new ChartRenderer(ImmutableMap.of("$TITLE_SUFFIX", "Round One lines"),
        lineTokenToDataMap, "generic.template", "/tmp/line.html").render();
    new ChartRenderer(ImmutableMap.of("$TITLE_SUFFIX", "Block hours"),
        blockTokenToDataMap, "generic.template", "/tmp/block.html").render();
  }

  private GraphData getLineGraphData(Optional<AwardDomicile> awardDomicile,
      Map<YearMonth, Map<AwardDomicile, ThinLineList>> thinLineLists) {
    QuotedColumn<YearMonth> labelColumn = new QuotedColumn<>("Month");
    GraphData graphData = new GraphData();
    graphData.add(labelColumn);
    WorkaroundColumn<Integer> column = new WorkaroundColumn<>("Round One Lines");
    List<YearMonth> sortedMonths = new ArrayList<>(thinLineLists.keySet());
    Collections.sort(sortedMonths);
    for (YearMonth yearMonth : sortedMonths) {
      labelColumn.add(yearMonth);
      Integer numLines = getNumRoundOneLines(
          awardDomicile,
          thinLineLists.get(yearMonth));
      column.add(numLines);
    }
    graphData.add(column);
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

  private GraphData getBlockGraphData(Optional<AwardDomicile> awardDomicile,
      Map<YearMonth, Map<AwardDomicile, ThinLineList>> thinLineLists,
      Map<YearMonth, Map<AwardDomicile, PairingList>> pairingLists) {
    QuotedColumn<YearMonth> labelColumn = new QuotedColumn<>("Month");
    GraphData graphData = new GraphData();
    graphData.add(labelColumn);
    Column<Integer> column = new WorkaroundColumn<>("Block hours");
    List<YearMonth> sortedMonths = new ArrayList<>(thinLineLists.keySet());
    Collections.sort(sortedMonths);
    for (YearMonth yearMonth : sortedMonths) {
      labelColumn.add(yearMonth);
      Period block = getBlockTime(
          awardDomicile,
          pairingLists.get(yearMonth));
      column.add(block == null ? null : block.getHours());
    }
    graphData.add(column);
    return graphData;
  }

  private Period getBlockTime(Optional<AwardDomicile> targetAwardDomicile,
      Map<AwardDomicile, PairingList> pairingLists) {
    if (pairingLists == null) {
      return null;
    }
    if (targetAwardDomicile.isPresent()
        && !pairingLists.containsKey(targetAwardDomicile.get())) {
      return null;
    }
    Period result = Period.ZERO;
    for (AwardDomicile awardDomicile : pairingLists.keySet()) {
      if (targetAwardDomicile.isPresent() &&
          !targetAwardDomicile.get().equals(awardDomicile)) {
        continue;
      }
      PairingList list = pairingLists.get(awardDomicile);
      for (Trip trip : list.getTripList()) {
        result = result.plus(Period.fromText(trip.getBlockDuration()));
      }
    }
    return result;
  }
}
