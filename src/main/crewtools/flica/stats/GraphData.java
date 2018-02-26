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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Joiner;

//  RowLabel          Column.getLabel()   Column.getLabel()
//  RowLabels.get(0)  Column.getDatum(0)  Column.getDatum(0)
//  RowLabels.get(1)  Column.getDatum(1)  Column.getDatum(1)
//
//  "Month"           "Round One"         "Active CAs"
//  2017-01           123                 234
//  2017-02           124                 235

public class GraphData {
  private List<Column> columns;
  
  public GraphData() {
    this.columns = new ArrayList<>();
  }
  
  public void add(Column column) {
    columns.add(column);
  }
  
  public void addAll(Collection<Column<?>> columns) {
    columns.addAll(columns);
  }
  
  private static final Joiner COMMA_JOINER = Joiner.on(',');
  
  public String getGraphData() {
    String data = "[\n";

    List<String> labels = new ArrayList<>();
    columns.forEach(column -> labels.add("'" + column.getLabel() + "'"));
    data += "[" + COMMA_JOINER.join(labels) + "],\n";
    
    for (int i = 0; i < columns.get(0).size(); ++i) {
      data += "[";
      List<String> datum = new ArrayList<>();
      final int index = i;
      columns.forEach(column -> datum.add(column.getDatum(index)));
      data += COMMA_JOINER.join(datum);
      data += "],\n";
    }
    
    data += "]\n";
    return data;
  }

  @Override
  public String toString() {
    return getGraphData();
  }
}
