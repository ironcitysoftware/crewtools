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

import crewtools.flica.pojo.OpentimeRequest;

public class OpentimeRequestParser {
  private final Logger logger = Logger.getLogger(OpentimeRequestParser.class.getName());

  private static final Pattern REQUEST_DECLARATION_PATTERN =
      Pattern.compile("QAry\\.push\\( new Req\\((.+)\\) \\);");

  private final String input;
  
  public OpentimeRequestParser(String input) {
    this.input = input;
  }
  
  public List<OpentimeRequest> parse() throws ParseException {
    try {
      return parseInternal();
    } catch (ParseException pe) {
      throw pe;
    } catch (Exception e) {
      throw new ParseException(e);
    }
  }

  protected List<OpentimeRequest> parseInternal() throws ParseException {
    List<OpentimeRequest> results = new ArrayList<>();
    Matcher taskDeclarationMatcher = REQUEST_DECLARATION_PATTERN.matcher(input);
    while (taskDeclarationMatcher.find()) {
      String argString = taskDeclarationMatcher.group(1);
      List<String> args = new ArrayList<>();
      int start = 0;
      boolean inBrace = false;
      for (int end = 0; end < argString.length(); ++end) {
        if (argString.charAt(end) == '[') {
          inBrace = true;
        } else if (argString.charAt(end) == ']') {
          inBrace = false;
        } else if (end == argString.length() - 1 
            || (!inBrace && argString.charAt(end) == ',')) {
          String arg = argString.substring(start, end + 1);
          if (arg.endsWith(",")) {
            arg = arg.substring(0, arg.length() - 1);
          }
          if (arg.startsWith("\"") && arg.endsWith("\"")) {
            arg = arg.substring(1, arg.length() - 1);
          }
          args.add(arg);
          start = end + 1;
        }
      }
      results.add(new OpentimeRequest(args.iterator()));
    }
    return results;
  }
}
