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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.joda.time.YearMonth;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import crewtools.flica.Proto.CrewMember;

public class BaseList {
  private final YearMonth yearMonth;
  private final String header;
  private Map<Integer, Member> list = new TreeMap<>();
  private Set<Integer> employeeIds = new HashSet<>();
  private Map<Integer, String> cssClasses = new HashMap<>();

  public BaseList(YearMonth yearMonth, String header) {
    this.yearMonth = yearMonth;
    this.header = header;
  }

  public static class Member {
    public final int employeeId;
    public final int seniorityId;
    public final String name;

    public Member(int employeeId, int seniorityId, String name) {
      this.employeeId = employeeId;
      this.seniorityId = seniorityId;
      this.name = name;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(employeeId, seniorityId, name);
    }

    public String format() {
      return String.format("%5d %4d %s", employeeId, seniorityId, name);
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || !(o instanceof Member)) {
        return false;
      }
      Member that = (Member) o;
      return this.employeeId == that.employeeId
          && this.seniorityId == that.seniorityId
          && this.name.equals(that.name);
    }

    @Override
    public String toString() {
      return String.format("%5d %4d %s", employeeId, seniorityId, name);
    }
  }

  public void remove(int employeeId) {
    Preconditions.checkState(employeeIds.contains(employeeId),
        "Removing " + employeeId + " where it does not exist: " + list);
    employeeIds.remove(employeeId);
    cssClasses.remove(employeeId);
    for (Member member : list.values()) {
      if (member.employeeId == employeeId) {
        list.remove(member.seniorityId);
        return;
      }
    }
    throw new IllegalStateException("No member with employee id " + employeeId);
  }

  public void add(CrewMember pilot) {
    add(pilot.getEmployeeId(), pilot.getSeniorityId(), pilot.getName());
  }

  public void add(int employeeId, int seniorityId, String name) {
    list.put(seniorityId, new Member(employeeId, seniorityId, name));
    Preconditions.checkState(employeeIds.add(employeeId),
        String.format("Attempt to add %d (%s) to list %s",
            employeeId, name, list.values()));
  }

  public int size() {
    return list.size();
  }

  public String getHeader() {
    return header;
  }

  public List<Member> getMembers() {
    return new ArrayList<>(list.values());
  }

  public void setCssClass(int employeeId, String clazz) {
    cssClasses.put(employeeId, clazz);
  }

  public boolean hasCssClass(int employeeId) {
    return cssClasses.containsKey(employeeId);
  }

  public String getCssClass(int employeeId) {
    return cssClasses.get(employeeId);
  }

  public Set<Integer> getEmployeeIds() {
    return employeeIds;
  }

  public boolean containsEmployeeId(int employeeId) {
    return employeeIds.contains(employeeId);
  }

  public YearMonth getYearMonth() {
    return yearMonth;
  }

  public BaseList copyWithoutStyles(YearMonth yearMonth, String newHeader) {
    BaseList copy = new BaseList(yearMonth, newHeader);
    for (Member member : list.values()) {
      copy.list.put(member.seniorityId, member);
      copy.employeeIds.add(member.employeeId);
    }
    return copy;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(header, list);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof BaseList)) {
      return false;
    }
    BaseList that = (BaseList) o;
    return this.list.equals(that.list)
        && this.header.equals(that.header);
  }

  @Override
  public String toString() {
    return header + "=" + list.toString();
  }
}
