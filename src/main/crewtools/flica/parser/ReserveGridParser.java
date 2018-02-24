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

import java.util.Map;
import java.util.TreeMap;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import crewtools.flica.pojo.ReserveGridEntry;

public class ReserveGridParser {
  private DateTimeFormatter DATE_MONTH_FORMAT = DateTimeFormat.forPattern("ddMMM");

  public Map<LocalDate, ReserveGridEntry> parse(int year, String rawReserveGridJson) {
    Map<LocalDate, ReserveGridEntry> result = new TreeMap<>();

    JsonParser parser = new JsonParser();
    JsonObject jsonObject = parser.parse(rawReserveGridJson).getAsJsonObject();
    Preconditions.checkState(
        jsonObject.get("success").getAsBoolean(),
        jsonObject.get("message").getAsString());
    JsonObject root = jsonObject.get("root").getAsJsonObject();
    JsonArray data = root.get("data").getAsJsonArray();

    for (int i = 0; i < data.size(); ++i) {
      JsonObject object = data.get(i).getAsJsonObject();
      String rawDate = object.get("date").getAsString();
      LocalDate date = DATE_MONTH_FORMAT.parseLocalDate(rawDate).withYear(year);
      int availableReserves = object.get("avl").getAsInt();
      int openDutyPeriods = object.get("odp").getAsInt();
      int netReserves = object.get("net").getAsInt();
      Preconditions.checkState(netReserves == availableReserves - openDutyPeriods);
      int minimumRequired = object.get("minReq").getAsInt();
      if (object.has("critical")) {
        String critical = object.get("critical").getAsString();
      }
      result.put(date, new ReserveGridEntry(
          availableReserves, openDutyPeriods, minimumRequired, false /* TODO */));
    }
    return result;
  }
}
