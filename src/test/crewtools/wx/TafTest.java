/**
 * Copyright 2020 Iron City Software LLC
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

package crewtools.wx;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.tz.DateTimeZoneBuilder;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

public class TafTest extends TestCase {

  final TafFormatter formatter = new TafFormatter();
  final Joiner joiner = Joiner.on('\n');

  final LocalDate nowUtc = new DateTime(DateTimeZone.UTC)
      .withYear(2013)
      .withMonthOfYear(8)
      .withDayOfMonth(1)
      .toLocalDate();

  // PST all year long.
  final DateTimeZone zone = new DateTimeZoneBuilder()
      .addCutover(-2147483648, 'w', 1, 1, 0, false, 0)
      .setStandardOffset(-28378000)
      .setFixedSavings("LMT", 0)
      .addCutover(1883, 'w', 11, 18, 0, false, 43200000)
      .setStandardOffset(-28800000)
      .addRecurringSavings("PST", 0, 1967, 2147483647, 'w', 10, -1, 7, false,
          7200000)
      .toDateTimeZone("America/Los_Angeles", true);

  // @formatter:off

  public void testExample() {
    List<String> txt = ImmutableList.of(
        "TAF KABC 241732Z 2418/2524 11006KT 4SM -SHRA BKN030 ",
        "FM242300 22006KT 3SM -SHRA OVC030 PROB30 2504/2506 VRB20G35KT " +
            "1SM TSRA BKN015CB ",
        "FM250600 25010KT 4SM -SHRA OVC050 ",
        "TEMPO 2508/2511 2SM -SHRA OVC030");
    ParsedTaf taf = new TafParser(nowUtc, txt).parse();
    assertEquals(
        joiner.join(formatter.format(taf)),
        joiner.join(
            "TAF KXXX 241732Z 2418/2600 11006KT 4SM -SHRA BKN030",
            "FM242300 22006KT 3SM -SHRA OVC030",
            "PROB30 2504/2506 VRB20G35KT 1SM TSRA BKN015",
            "FM250600 25010KT 4SM -SHRA OVC050",
            "TEMPO 2508/2511 2SM -SHRA OVC030"),
        joiner.join(formatter.format(taf)));
  }

  public void testAtl() {
    List<String> txt = ImmutableList.of(
        "TAF KATL 220540Z 2206/2312 03006KT P6SM SKC ",
        "FM221200 05005KT P6SM SKC ",
        "FM221600 09006KT P6SM SKC ",
        "FM222000 15006KT P6SM SKC ",
        "FM230300 25003KT P6SM SKC ",
        "FM230900 30003KT P6SM SKC");
    ParsedTaf taf = new TafParser(nowUtc, txt).parse();
    assertEquals(
        joiner.join(formatter.format(taf)),
        joiner.join(
            "TAF KXXX 220540Z 2206/2312 03006KT P6SM SKC",
            "FM221200 05005KT P6SM SKC",
            "FM221600 09006KT P6SM SKC",
            "FM222000 15006KT P6SM SKC",
            "FM230300 25003KT P6SM SKC",
            "FM230900 30003KT P6SM SKC"),
        joiner.join(formatter.format(taf)));
  }

  public void testIssued() {
    List<String> txt = ImmutableList.of(
        "TAF KSAC 312333Z 0100/0124 20012KT P6SM BKN250",
        "FM010500 18006KT P6SM BKN200");
    ParsedTaf taf = new TafParser(nowUtc, txt).parse();
    DateTime expectedIssued = new DateTime(DateTimeZone.UTC)
        .withMonthOfYear(7).withDayOfMonth(31).withHourOfDay(23).withMinuteOfHour(33)
        .withSecondOfMinute(0).withMillisOfSecond(0).withYear(2013);
    assertEquals(expectedIssued, taf.issued);
  }

  public void testEurope() {
    List<String> txt = ImmutableList.of(
        "TAF EDDF 101100Z 1012/1118 25007KT 9999 SCT040");
    ParsedTaf taf = new TafParser(nowUtc, txt).parse();
    assertEquals(
        joiner.join(formatter.format(taf)),
        "TAF KXXX 101100Z 1012/1118 25007KT P6SM SCT040",
        joiner.join(formatter.format(taf)));
  }
}
