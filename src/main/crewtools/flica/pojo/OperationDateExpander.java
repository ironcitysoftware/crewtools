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

package crewtools.flica.pojo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import crewtools.flica.Proto;
import crewtools.flica.Proto.DayOfWeek;

// Oct 18-Oct 31
// Except Oct 24, Oct 25
public class OperationDateExpander {
  private final Logger logger = Logger.getLogger(OperationDateExpander.class.getName());

  private final int year;
  private final String operates;
  private final List<Proto.DayOfWeek> daysOfWeek;
  private final String except;

  public OperationDateExpander(int year, String operates, List<DayOfWeek> daysOfWeek, String except) {
    this.year = year;
    this.operates = operates;
    this.daysOfWeek = daysOfWeek;
    this.except = except;
  }

  private static final Pattern OPERATES_PATTERN =
      Pattern.compile("(\\w{3}) (\\d+)-(\\w{3}) (\\d+)");

  private static final DateTimeFormatter MONTH_DAY = DateTimeFormat.forPattern("MMM d");
  private static final Map<DayOfWeek, Integer> PROTO_TO_JODA =
      ImmutableMap.<DayOfWeek, Integer>builder()
          .put(DayOfWeek.SUNDAY, 7)
          .put(DayOfWeek.MONDAY, 1)
          .put(DayOfWeek.TUESDAY, 2)
          .put(DayOfWeek.WEDNESDAY, 3)
          .put(DayOfWeek.THURSDAY, 4)
          .put(DayOfWeek.FRIDAY, 5)
          .put(DayOfWeek.SATURDAY, 6)
          .build();
  private static final BiMap<DayOfWeek, Integer> PROTO_TO_JODA_BI = HashBiMap.create(PROTO_TO_JODA);
  private static final Splitter EXCEPT_SPLITTER = Splitter
      .on(CharMatcher.anyOf(",.")).trimResults().omitEmptyStrings();


  public List<LocalDate> getDates() {
    // A trip could have multiple pairing keys if the operates date spans
    // multiple days.  Expand these.

    // Oct 18-Oct 31
    ImmutableList.Builder<LocalDate> result = ImmutableList.builder();
    Matcher operatesMatcher = OPERATES_PATTERN.matcher(operates);
    Preconditions.checkState(operatesMatcher.matches(), "Unmached [" + operates + "]");
    LocalDate startDate =
        MONTH_DAY
            .parseLocalDate(operatesMatcher.group(1) + " " + operatesMatcher.group(2))
            .withYear(year);
    LocalDate endDate =
        MONTH_DAY
            .parseLocalDate(operatesMatcher.group(3) + " " + operatesMatcher.group(4))
            .withYear(year);

    // Except Oct 24, Oct 25
    Set<LocalDate> exceptions = new HashSet<>();
    for (String exception : EXCEPT_SPLITTER.split(except)) {
      exceptions.add(
          MONTH_DAY
              .parseLocalDate(exception)
              .withYear(year));
    }

    for (LocalDate localDate = startDate; !localDate.isAfter(endDate); localDate = localDate.plusDays(1)) {
      if (exceptions.contains(localDate)) {
        continue;
      }
      DayOfWeek dow = PROTO_TO_JODA_BI.inverse().get(localDate.getDayOfWeek());
      if (daysOfWeek.contains(dow)) {
        result.add(localDate);
      }
    }
    return result.build();
  }
}
