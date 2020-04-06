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

package crewtools.legal;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import crewtools.wx.MetarParser;
import crewtools.wx.ParsedMetar;
import crewtools.wx.ParsedTaf;
import crewtools.wx.TafParser;

public class ValidatorStub {
  public static void main(String args[]) throws Exception {
    new ValidatorStub().validate();
  }

  private static final List<String> ORIGIN_TAF = ImmutableList.of(
      "CLT TAF KCLT 031734Z 0318/0424 32009KT P6SM FEW250",
      "FM040000 34004KT P6SM SCT150",
      "FM041400 01007KT P6SM SCT200 ");

  private static final List<String> DESTINATION_TAF = ImmutableList.of(
      "ABE TAF KABE 031720Z 0318/0418 34014G23KT P6SM OVC060",
      " FM032200 35010KT P6SM OVC030",
      " FM041000 01005KT P6SM BKN030",
      " FM041500 02005KT P6SM BKN035 ");

  private static final List<String> ALT_TAF = ImmutableList.of(
      "AVP TAF KAVP 031741Z 0318/0418 33012G20KT P6SM -RA OVC015 ");

  private static final String ORIGIN_METAR = "CLT"
      + " 031652Z 29009KT 10SM FEW250 21/00 A3000 RMK AO2 SLP156"
      + " T02060000 ";

  private static final String DESTINATION_METAR = "ABE"
      + " 031651Z 34013G24KT 10SM OVC055 10/01 A2976 RMK AO2 PK WND"
      + " 34026/1553 SLP078 T01000011 ";

  private static final String ALT_METAR = "AVP"
      + " 031654Z 36015G20KT 10SM OVC019 07/02 A2981 RMK AO2 SLP096"
      + " T00720022 ";

  public void validate() throws Exception {
    LocalDate today = new LocalDate(2020, 4, 3);
    YearMonth thisMonth = new YearMonth(today);

    ValidationContext context = new ValidationContext();

    context.departureMetar = new MetarParser(thisMonth,
        Splitter.on(" ").split(ORIGIN_METAR).iterator()).parse();
    context.departureTaf = new TafParser(today, ORIGIN_TAF).parse();
    // System.out.println(cltTaf);
    // System.out.println(new TafFormatter().format(cltTaf));

    context.arrivalMetar = new MetarParser(thisMonth,
        Splitter.on(" ").split(DESTINATION_METAR).iterator()).parse();
    context.arrivalTaf = new TafParser(today, DESTINATION_TAF).parse();
    // System.out.println(abeMetar);
    // System.out.println(new TafFormatter().format(abeTaf));

    ParsedMetar avpMetar = new MetarParser(thisMonth,
        Splitter.on(" ").split(ALT_METAR).iterator()).parse();
    ParsedTaf avpTaf = new TafParser(today, ALT_TAF).parse();
    // System.out.println(avpMetar);
    // System.out.println(new TafFormatter().format(avpTaf));

    context.arrivalEta = new DateTime(2020, 4, 3, 20, 34, 0, DateTimeZone.UTC);
    context.arrivalFaaId = "ABE";
    Validator validator = new Validator(context);
    validator.validate();
    Result result = validator.getResult();
    result.output(System.out);
  }
}
