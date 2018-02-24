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

import crewtools.flica.pojo.FlicaTask;

public class OpenTimeParser {
  private final Logger logger = Logger.getLogger(OpenTimeParser.class.getName());

  private static final Pattern TASK_DECLARATION_PATTERN =
      Pattern.compile("\\d+\\]=new Task\\((([^\\)]+,? ?)+)\\)");

  private static final Splitter COMMA_SPLITTER = Splitter.on(',')
      .trimResults(CharMatcher.anyOf("\"' "));

  private static final String UNPUBLISHED_MAGIC_MARKER =
      "Bid Package Not Published";
  
  private final int year;
  private final String input;
  private final boolean published;

  public OpenTimeParser(int year, String input) {
    this.year = year;
    this.input = input;
    this.published = !input.contains(UNPUBLISHED_MAGIC_MARKER);
  }

  public List<FlicaTask> parse() throws ParseException {
    try {
      return parseInternal();
    } catch (ParseException pe) {
      throw pe;
    } catch (Exception e) {
      throw new ParseException(e);
    }
  }

  public boolean isPublished() {
    return published;
  }
  
  protected List<FlicaTask> parseInternal() throws ParseException {
    List<FlicaTask> results = new ArrayList<>();
    Matcher taskDeclarationMatcher = TASK_DECLARATION_PATTERN.matcher(input);
    while (taskDeclarationMatcher.find()) {
      String argString = taskDeclarationMatcher.group(1);
      Iterable<String> args = COMMA_SPLITTER.split(argString);
      FlicaTask task = new FlicaTask(year, args.iterator());
      if (task.repdate == null) {
        logger.info("Skipping " + task.pairingName + " as it appears cancelled.");
      } else {
        results.add(task);
      }
    }
    return results;
  }
}
