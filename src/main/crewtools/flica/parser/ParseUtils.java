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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.nodes.Element;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ProtocolMessageEnum;

import crewtools.flica.Proto;

public class ParseUtils {

  public static <T extends Enum<T>> Map<String, T> getEnumValueMap(Class<T> enumType) {
    ImmutableMap.Builder<String, T> builder = ImmutableMap.builder();
    for (T value : enumType.getEnumConstants()) {
      String key = getFlicaName((ProtocolMessageEnum) value);
      if (key == null) {
        key = value.name();
      }
      builder.put(key, value);
    }
    return builder.build();
  }
  
  public static <T extends Enum<T>> String getFlicaName(ProtocolMessageEnum protoValue) {
    return protoValue.getValueDescriptor().getOptions().hasExtension(Proto.flicaName)
        ? protoValue.getValueDescriptor().getOptions().getExtension(Proto.flicaName)
        : null;
  }

  public static List<String> expandCells(Element row) {
    List<String> result = new ArrayList<>();
    Iterator<Element> cells = row.select("> td").iterator();
    while (cells.hasNext()) {
      Element cell = cells.next();
      if (cell.hasAttr("colspan")) {
        result.add(trim(cell.text()));
        String colspanParameter = cell.attr("colspan");
        for (int i = 1; i < Integer.parseInt(colspanParameter); i++) {
          result.add("");
        }
      } else {
        result.add(trim(cell.text()));
      }
    }
    return result;
  }

  public static String trim(String text) {
    // Trim &nbsp;
    return text.replaceAll("(^\\h*)|(\\h*$)", "");
  }

  private static final Pattern LOCAL_TIME_PATTERN = Pattern.compile("^(\\d{4})L$");

  public static String parseLocalTime(String text) throws ParseException {
    Matcher dayEndMatcher = LOCAL_TIME_PATTERN.matcher(text);
    checkState(dayEndMatcher.matches(), "Misformatted local time: " + text);
    return dayEndMatcher.group(1);
  }

  private static final DateTimeFormatter TRIP_DATE_FORMATTER = DateTimeFormat.forPattern("dMMM");

  public static LocalDate parseTripLocalDateWithYearHint(String text, int year) {
    LocalDate date = TRIP_DATE_FORMATTER.parseLocalDate(text);
    LocalDate today = new LocalDate().withYear(year);
    if (date.getMonthOfYear() > today.getMonthOfYear() + 2) {
      date = date.withYear(year - 1);
    } else {
      date = date.withYear(year);
    }
    return date;
  }

  public static void checkState(boolean expression, String message) throws ParseException {
    if (!expression) {
      throw new ParseException(message);
    }
  }
}
