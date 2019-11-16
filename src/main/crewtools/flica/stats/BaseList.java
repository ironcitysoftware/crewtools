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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.joda.time.YearMonth;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import crewtools.flica.Proto.CrewMember;

public class BaseList {
  private final Logger logger = Logger.getLogger(BaseList.class.getName());

  private final static int DEFAULT_MONTHLY_LINE_INCREASE = 1;

  private final YearMonth yearMonth;
  private final String header;
  private final LineInfo lineInfo;

  private Map<Integer, Member> membersByEmployeeId = new TreeMap<>();
  private Map<Integer, Member> membersBySeniorityId = new TreeMap<>();
  private Map<Member, AwardType> awardOverrides = new HashMap<>();
  private Map<Integer, String> cssClassesByEmployeeId = new HashMap<>();

  public BaseList(YearMonth yearMonth, String header, LineInfo lineInfo) {
    this.yearMonth = yearMonth;
    this.header = header;
    this.lineInfo = lineInfo;
  }

  public LineInfo getLineInfo() {
    return lineInfo;
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
    Preconditions.checkState(membersByEmployeeId.containsKey(employeeId),
        "Removing " + employeeId + " where it does not exist");
    Member member = membersByEmployeeId.remove(employeeId);
    membersBySeniorityId.remove(member.seniorityId);
    cssClassesByEmployeeId.remove(employeeId);
    awardOverrides.remove(member);
  }

  public void addWithoutAward(int employeeId, int seniorityId, String name) {
    addInternal(employeeId, seniorityId, name, null);
  }

  public void add(CrewMember pilot, AwardType awardType) {
    addInternal(pilot.getEmployeeId(), pilot.getSeniorityId(),
        pilot.getName(), awardType);
  }

  public void add(int employeeId, int seniorityId, String name, AwardType awardType) {
    addInternal(employeeId, seniorityId, name, awardType);
  }

  private void addInternal(int employeeId, int seniorityId, String name,
      AwardType awardType) {
    if (membersByEmployeeId.containsKey(employeeId)) {
      return;
    }
    Member member = new Member(employeeId, seniorityId, name);
    membersByEmployeeId.put(employeeId, member);
    membersBySeniorityId.put(seniorityId, member);
    if (awardType != null) {
      awardOverrides.put(member, awardType);
    }
  }

  public String getHeader() {
    return header;
  }

  private Map<AwardType, Integer> determineMostJuniorAward() {
    Map<AwardType, Integer> juniorSeniorityIds = new HashMap<>();
    List<Integer> seniorityIds = new ArrayList<>(membersBySeniorityId.keySet());
    Collections.sort(seniorityIds);
    for (int seniorityId : seniorityIds) {
      Member member = membersBySeniorityId.get(seniorityId);
      if (!awardOverrides.containsKey(member)) {
        continue;
      }
      juniorSeniorityIds.put(awardOverrides.get(member), member.seniorityId);
    }
    return juniorSeniorityIds;
  }

  private Set<Member> determineAwardsToRemove() {
    List<Integer> seniorityIds = new ArrayList<>(membersBySeniorityId.keySet());
    Collections.sort(seniorityIds);
    Map<AwardType, Integer> juniorSeniorityIds = determineMostJuniorAward();

    Set<Member> removeTheseAwards = new HashSet<>();
    for (int seniorityId : seniorityIds) {
      Member member = membersBySeniorityId.get(seniorityId);
      if (!awardOverrides.containsKey(member)) {
        continue;
      }
      AwardType awardType = awardOverrides.get(member);
      if (awardType == AwardType.ROUND1) {
        removeTheseAwards.add(member);
        continue;
      }
      AwardType previousAwardType = AwardType.values()[awardType.ordinal() - 1];
      if (member.seniorityId > juniorSeniorityIds.get(previousAwardType)) {
        removeTheseAwards.add(member);
      }
    }
    return removeTheseAwards;
  }

  public void removeUnnecessaryAwards() {
    Set<Member> removeTheseAwards = determineAwardsToRemove();
    for (Member member : removeTheseAwards) {
      awardOverrides.remove(member);
    }
  }

  public Set<Integer> getAwardOverrideEmployeeIds() {
    return awardOverrides
        .keySet()
        .stream()
        .map(m -> m.employeeId)
        .collect(Collectors.toSet());
  }

  public List<Member> getMembers(AwardType awardType) {
    Map<AwardType, List<Member>> computedAwards = new HashMap<>();
    for (AwardType at : AwardType.values()) {
      computedAwards.put(at, new ArrayList<>());
    }
    Iterator<Member> it = membersBySeniorityId.values().iterator();
    for (AwardType at : ImmutableList.of(
        AwardType.ROUND1, AwardType.ROUND2, AwardType.LCR)) {
      List<Member> result = computedAwards.get(at);
      while (result.size() < lineInfo.getNum(at)) {
        Member member = it.next();
        if (awardOverrides.containsKey(member)) {
          computedAwards.get(awardOverrides.get(member)).add(member);
        } else {
          result.add(member);
        }
      }
    }
    while (it.hasNext()) {
      computedAwards.get(AwardType.SCR).add(it.next());
    }
    return computedAwards.get(awardType);
  }

  public void setCssClass(int employeeId, String clazz) {
    cssClassesByEmployeeId.put(employeeId, clazz);
  }

  public boolean hasCssClass(int employeeId) {
    return cssClassesByEmployeeId.containsKey(employeeId);
  }

  public String getCssClass(int employeeId) {
    return cssClassesByEmployeeId.get(employeeId);
  }

  public Set<Integer> getEmployeeIds() {
    return membersByEmployeeId.keySet();
  }

  public boolean containsEmployeeId(int employeeId) {
    return membersByEmployeeId.containsKey(employeeId);
  }

  public YearMonth getYearMonth() {
    return yearMonth;
  }

  public BaseList copyWithoutStyles(YearMonth yearMonth, String newHeader) {
    BaseList copy = new BaseList(yearMonth, newHeader,
        lineInfo.increment(DEFAULT_MONTHLY_LINE_INCREASE));
    copy.membersByEmployeeId.putAll(membersByEmployeeId);
    copy.membersBySeniorityId.putAll(membersBySeniorityId);
    copy.awardOverrides.putAll(awardOverrides);
    return copy;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(header, membersByEmployeeId, awardOverrides,
        membersBySeniorityId);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof BaseList)) {
      return false;
    }
    BaseList that = (BaseList) o;
    return this.membersByEmployeeId.equals(that.membersByEmployeeId)
        && this.membersBySeniorityId.equals(that.membersBySeniorityId)
        && this.awardOverrides.equals(that.awardOverrides)
        && this.header.equals(that.header);
  }

  @Override
  public String toString() {
    return header + "=" + membersByEmployeeId.toString();
  }
}
