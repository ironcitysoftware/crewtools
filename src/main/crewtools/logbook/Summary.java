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

package crewtools.logbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import crewtools.util.Period;

public class Summary {
  public static class Node {
    public Period time = Period.ZERO;
    public Map<String, Node> children = new HashMap<>();

    public void add(List<String> keys, Period block) {
      String key = keys.remove(0);
      Node child;
      if (children.containsKey(key)) {
        child = children.get(key);
      } else {
        child = new Node();
        children.put(key, child);
      }
      child.time = child.time.plus(block);
      if (!keys.isEmpty()) {
        child.add(keys, block);
      }
    }

    public Period traverse(String parentLabel, Map<String, Period> summaries) {
      if (children.isEmpty()) {
        summaries.put(parentLabel, time);
        return time;
      } else {
        Period leafSum = Period.ZERO;
        for (String key : children.keySet()) {
          String label = parentLabel.isEmpty() ? key : parentLabel + "/" + key;
          leafSum = leafSum.plus(children.get(key).traverse(label, summaries));

        }
        if (!parentLabel.isEmpty()) {
          summaries.put(parentLabel, leafSum);
        }
        return leafSum;
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(time, children);
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || !(o instanceof Node)) {
        return false;
      }
      Node that = (Node) o;
      return this.time.equals(that.time)
          && this.children.equals(that.children);
    }

    @Override
    public String toString() {
      return time.toString() + ":" + children.toString();
    }
  }

  private Node root = new Node();

  public void add(Record record) {
    LocalDate utcDate = record.zonedDepartureTime != null
        ? record.zonedDepartureTime.withZone(DateTimeZone.UTC).toLocalDate()
        : record.date;
    add(record.isPic, record.block, record.shorthandAircraftType,
        utcDate);
  }

  public void add(boolean isPic, Period block,
      String aircraftType, LocalDate utcDate) {
    List<String> keys = new ArrayList<>();
    keys.add("TYPE");
    keys.add(isPic ? "PIC" : "SIC");
    keys.add("RJ" + aircraftType);
    root.add(keys, block);

    keys.clear();
    keys.add("DATE");
    keys.add(isPic ? "PIC" : "SIC");
    keys.add(Integer.toString(utcDate.getYear()));
    keys.add(Integer.toString(utcDate.getMonthOfYear()));
    root.add(keys, block);
  }

  @Override
  public String toString() {
    Map<String, Period> summaries = new TreeMap<>();
    root.traverse("", summaries);
    String result = "";
    for (String label : summaries.keySet()) {
      result += String.format("%s: %s%n", label, summaries.get(label));
    }
    return result;
  }
}
