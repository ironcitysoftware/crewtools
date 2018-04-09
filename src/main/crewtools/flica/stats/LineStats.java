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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.Section;
import crewtools.flica.Proto.ThinLineList;
import crewtools.flica.Proto.Trip;

// You can't use lines for this because CI days are not populated in lines.
// You have to instead use pairings.
public class LineStats {
  private final Logger logger = Logger.getLogger(LineStats.class.getName());

  public static void main(String args[]) throws Exception {
    new LineStats().run();
  }
  
  private final DataReader dataReader;
  
  public LineStats() throws IOException {
    this.dataReader = new DataReader();
  }

  static class Overnight {
    final String station;
    final Map<AwardDomicile, Integer> awardDomiciles;
    
    public Overnight(String station) {
      this.station = station;
      this.awardDomiciles = new HashMap<>();
    }
    
    public int get(AwardDomicile awardDomicile) {
      return awardDomiciles.containsKey(awardDomicile)
          ? awardDomiciles.get(awardDomicile)
          : 0;
    }
    
    public void addOvernight(AwardDomicile awardDomicile) {
      if (!awardDomiciles.containsKey(awardDomicile)) {
        awardDomiciles.put(awardDomicile, 0);
      }
      awardDomiciles.put(awardDomicile, awardDomiciles.get(awardDomicile) + 1);
    }
  }
  
  public void run() throws Exception {
    Map<YearMonth, Map<AwardDomicile, ThinLineList>> allLineLists = dataReader.readLines();
    // only read these until we are confident in the data
    Map<YearMonth, Map<AwardDomicile, PairingList>> allPairings = dataReader.readPairings();
    Map<AwardDomicile, ThinLineList> lineLists = 
        allLineLists.get(YearMonth.parse("2018-05"));
    Map<AwardDomicile, PairingList> pairingLists =
        allPairings.get(YearMonth.parse("2018-05"));
    Map<String, Overnight> overnightCounts = getOvernightCounts(pairingLists);
    
    GraphData graphData = new GraphData();
    Column<String> stationColumn = new QuotedColumn<>("Stations");
    graphData.add(stationColumn);

    Map<AwardDomicile, Column<Integer>> columns = new HashMap<>();
    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      Column<Integer> column = new Column<>(awardDomicile.name());
      columns.put(awardDomicile, column);
      graphData.add(column);
    }
    
    List<String> stations = new ArrayList<>(overnightCounts.keySet());
    Collections.sort(stations);
    
    for (String station : stations) {
      stationColumn.add(station);
      for (AwardDomicile awardDomicile : AwardDomicile.values()) {
        int count = overnightCounts.get(station).get(awardDomicile);
        columns.get(awardDomicile).add(count);
      }
    }

    Map<String, GraphData> tokenToDataMap = new HashMap<>();
    tokenToDataMap.put("$OVERNIGHT_DATA", graphData);
    
    new ChartRenderer(tokenToDataMap, "line.template", "/tmp/line.html").render();
  }
  
  private Map<String, Overnight> getOvernightCounts(Map<AwardDomicile, PairingList> lines) {
    Map<String, Overnight> result = new HashMap<>();
    for (AwardDomicile awardDomicile : lines.keySet()) {
      for (Trip trip : lines.get(awardDomicile).getTripList()) {
        for (Section section : trip.getSectionList()) {
          if (section.hasLayoverAirportCode()) {
            String station = section.getLayoverAirportCode();
            if (!result.containsKey(station)) {
              result.put(station, new Overnight(station));
            }
            result.get(station).addOvernight(awardDomicile);
          }
        }
      }
    }
    return result;
  }
}
