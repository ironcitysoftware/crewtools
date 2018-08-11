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

package crewtools.flica;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.ReadableInstant;

import crewtools.dashboard.ScheduleProvider;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.Leg;
import crewtools.util.Clock;
import crewtools.util.ListAndIndex;

public class LegSelector {
  private static final Logger logger = Logger.getLogger(LegSelector.class.getName());

  private Clock clock;
  private ScheduleProvider scheduleProvider;

  public LegSelector(Clock clock, ScheduleProvider scheduleProvider) {
    this.clock = clock;
    this.scheduleProvider = scheduleProvider;
  }

  public ListAndIndex<Leg> getRelevantLegs() throws IOException, ParseException {
    List<Leg> result = new ArrayList<>();
    LocalDate today = clock.today();
    if (today.getDayOfMonth() == 1) {
      result.addAll(scheduleProvider.getPreviousMonthSchedule().getLegs());
    }
    result.addAll(scheduleProvider.getCurrentMonthSchedule().getLegs());
    if (today.plusDays(1).getDayOfMonth() == 1) {
      result.addAll(scheduleProvider.getNextMonthSchedule().getLegs());
    }
    result.removeIf(leg -> !isRelevantLeg(leg));

    return new ListAndIndex<>(result, getCurrentOrNextLegIndex(result));
  }

  private int getCurrentOrNextLegIndex(List<Leg> legs) {
    for (int i = 0; i < legs.size(); ++i) {
      if (isCurrentLeg(legs.get(i))) {
        return i;
      }
    }
    for (int i = 0; i < legs.size(); ++i) {
      if (isNextLeg(legs.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private boolean isRelevantLeg(Leg leg) {
    return isNowWithin(leg.getDepartureTime().minusHours(24),
        leg.getArrivalTime().plusHours(24));
  }

  private boolean isCurrentLeg(Leg leg) {
    return isNowWithin(leg.getDepartureTime(), leg.getArrivalTime());
  }

  private boolean isNextLeg(Leg leg) {
    return leg.getDepartureTime().isAfter(clock.now());
  }

  private boolean isNowWithin(ReadableInstant start, ReadableInstant end) {
    DateTime now = clock.now();
    return !start.isAfter(now) && !end.isBefore(now);
  }
}
