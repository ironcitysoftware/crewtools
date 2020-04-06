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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class Result {
  private final List<String> facts = new ArrayList<>();
  private final List<String> errors = new ArrayList<>();

  public void addFact(String fact) {
    facts.add(fact);
  }

  public void addError(String error) {
    errors.add(error);
  }

  public boolean hasError() {
    return !errors.isEmpty();
  }

  public void output(PrintStream out) {
    out.println("FACTS");
    facts.forEach(fact -> out.println(fact));
    out.println();
    out.println("ERRORS");
    errors.forEach(error -> out.println(error));
  }
}
