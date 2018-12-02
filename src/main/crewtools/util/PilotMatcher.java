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

package crewtools.util;

import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.Pilot;
import crewtools.flica.Proto.SeniorityList;

public class PilotMatcher {
  private final SeniorityList seniorityList;

  public PilotMatcher(SeniorityList seniorityList) {
    this.seniorityList = seniorityList;
  }

  public CrewMember matchCrewMember(Pilot pilot) {
    String formattedName = (pilot.getFirstMiddleName()
        + " " + pilot.getLastNameSuffix()).toUpperCase();
    for (CrewMember member : seniorityList.getCrewMemberList()) {
      if (member.getName().equals(formattedName)) {
        return member;
      }
    }
    return null;
  }
}
