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

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import crewtools.flica.StationZoneProvider;

public class TimeUtils {
  private DateTimeFormatter HHMM_LOCALTIME = DateTimeFormat.forPattern("HHmm");

  public LocalTime parseLocalTime(String protoHhMmField) {
    return LocalTime.parse(protoHhMmField, HHMM_LOCALTIME);
  }

  private DateTimeFormatter HH_COLON_MM_LOCALTIME = DateTimeFormat.forPattern("HH:mm");

  public LocalTime parseLocalTimeWithColon(String protoHhColonMmField) {
    return LocalTime.parse(protoHhColonMmField, HH_COLON_MM_LOCALTIME);
  }

  private StationZoneProvider zoneProvider = new StationZoneProvider();

  public DateTime getDateTime(LocalDate date, LocalTime time, String station) {
    return date.toDateTime(time, zoneProvider.getDateTimeZone(station));
  }
}
