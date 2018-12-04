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
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import crewtools.flica.pojo.PeerScheduleDay;

public class PeerScheduleParser {
  private final Logger logger = Logger.getLogger(PeerScheduleParser.class.getName());

  private static final Pattern TASK_DECLARATION_PATTERN = Pattern
      .compile("=new Task\\((.+)\\);");
  private static final Splitter SPLITTER = Splitter.on(", ")
      .trimResults(CharMatcher.anyOf("\"'"));
  private static final String NO_SCHEDULE_AVAILABLE = "No Schedule Available.";

  private final String input;

  public PeerScheduleParser(String input) {
    this.input = input;
  }

  public List<PeerScheduleDay> parse() throws ParseException {
    try {
      return parseInternal();
    } catch (ParseException pe) {
      throw pe;
    } catch (Exception e) {
      throw new ParseException(e);
    }
  }

  protected List<PeerScheduleDay> parseInternal() throws ParseException {
    List<PeerScheduleDay> results = new ArrayList<>();
    if (!input.contains(NO_SCHEDULE_AVAILABLE)) {
      Matcher taskDeclarationMatcher = TASK_DECLARATION_PATTERN.matcher(input);
      while (taskDeclarationMatcher.find()) {
        String argString = taskDeclarationMatcher.group(1);
        PeerScheduleDay day = new PeerScheduleDay(SPLITTER.split(argString).iterator());
        results.add(day);
      }
    }
    return results;
  }
}
