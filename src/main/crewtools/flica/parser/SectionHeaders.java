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

package crewtools.flica.parser;

import static crewtools.flica.parser.ParseUtils.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

public class SectionHeaders {
  public enum Header {
    DAY_OF_WEEK("DY"),
    DAY_OF_MONTH("DD"),
    IS_DEADHEAD("DH"),
    CHANGE_PLANES("C"),
    FLIGHT_NUMBER("FLTNO"),
    CITY_PAIR("DPS-ARS"),
    DEPARTURE_LOCAL_TIME("DEPL"),
    ARRIVAL_LOCAL_TIME("ARRL"),
    BLOCK_DURATION("BLKT"),
    GROUND_DURATION("GRNT"),
    OTHER_AIRLINE("OA"),
    TRIP_TOTAL_LABEL("unused"),
    EQUIPMENT("EQP"),
    SECTION_BLOCK_DURATION("TBLK"),
    SECTION_DEADHEAD_DURATION("TDHD"),
    SECTION_CREDIT_DURATION("TCRD"),
    DUTY_TIMES("TDUTY/FDP"),
    LAYOVER("LAYOVER"),
    BLANK("");

    private final String header;

    Header(String header) {
      this.header = header;
    }

    String getHeader() {
      return header;
    }
  }

  private final List<Header> headers;
  private final Map<String, Header> acceptableHeaders;

  public SectionHeaders() {
    this.headers = new ArrayList<>();
    ImmutableMap.Builder<String, Header> builder = ImmutableMap.builder();
    for (Header header : Header.values()) {
      builder.put(header.getHeader(), header);
    }
    this.acceptableHeaders = builder.build();
  }

  public void add(String headerText) throws ParseException {
    checkState(headerText.isEmpty() || acceptableHeaders.containsKey(headerText),
        "Unexpected header: " + headerText);
    Header header = acceptableHeaders.get(headerText);
    checkState(header != null, "No header for [" + headerText + "]");
    headers.add(header);
  }

  public void add(Header header) {
    headers.add(header);
  }

  public int indexOf(Header header) {
    return headers.indexOf(header);
  }

  public boolean hasHeader(Header header) {
    return headers.contains(header);
  }

  @Override
  public String toString() {
    return headers.toString();
  }
}
