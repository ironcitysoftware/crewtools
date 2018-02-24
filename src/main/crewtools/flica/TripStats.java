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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Preconditions;

import crewtools.flica.Proto.Section;
import crewtools.flica.Proto.Trip;

public class TripStats {
  class Summation {
    private Duration blockDuration = Duration.ZERO;
    private Duration deadheadDuration = Duration.ZERO;
    public Duration getBlockDuration() { return blockDuration; }
    public void addBlockDuration(Duration blockDuration) {
      this.blockDuration = this.blockDuration.plus(blockDuration);
    }
    public Duration getDeadheadDuration() { return deadheadDuration; }
    public void addDeadheadDuration(Duration deadheadDuration) {
      this.deadheadDuration = this.deadheadDuration.plus(deadheadDuration);
    }

    public void add(Summation that) {
      addBlockDuration(that.blockDuration);
      addDeadheadDuration(that.deadheadDuration);
    }

    @Override
    public String toString() {
      return String.format("Total block: %s; total deadhead: %s",
          blockDuration, deadheadDuration);
    }
  }

  private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d{2})(\\d{2})$");
  private static final Pattern LOCAL_TIME_PATTERN = Pattern.compile("^(\\d{4})L$");

  private static final DateTimeFormatter LOCAL_TIME = DateTimeFormat.forPattern("HHmm");

  private Duration computeTimeAwayFromBase(Trip trip) {
    if (trip.getSectionCount() == 0) {
      return Duration.ZERO;
    }
    Section firstSection = trip.getSection(0);
    Section lastSection = trip.getSection(trip.getSectionCount() - 1);

    LocalDate localStartDate = LocalDate.parse(trip.getStartDate());
    LocalTime localStartTime = LOCAL_TIME.parseLocalTime(firstSection.getLocalDutyStartTime());
    // TODO Timezone
    DateTime startTime = localStartDate.toDateTime(localStartTime, DateTimeZone.forID("America/New_York"));
    LocalDate localEndDate = localStartDate.plusDays(trip.getSectionCount() - 1);
    LocalTime localEndTime = LOCAL_TIME.parseLocalTime(lastSection.getLocalDutyEndTime());
    DateTime endTime = localEndDate.toDateTime(localEndTime, DateTimeZone.forID("America/New_York"));
    return new Duration(startTime, endTime);
  }

  private Duration getDuration(String hhmm) {
    Matcher matcher = DURATION_PATTERN.matcher(hhmm);
    Preconditions.checkState(matcher.matches());
    int numHours = Integer.parseInt(matcher.group(1));
    int numMinutes = Integer.parseInt(matcher.group(2));
    return Duration.standardHours(numHours).plus(Duration.standardMinutes(numMinutes));
  }
}
