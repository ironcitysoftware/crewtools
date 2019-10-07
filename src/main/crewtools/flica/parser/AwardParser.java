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

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.base.Preconditions;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.Award;
import crewtools.flica.Proto.DomicileAward;
import crewtools.flica.Proto.Pilot;
import crewtools.flica.Proto.Rank;

public class AwardParser {
  private final Logger logger = Logger.getLogger(AwardParser.class.getName());

  private final String input;
  private final AwardDomicile domicile;
  private final Rank rank;
  private final int round;

  public AwardParser(String input, AwardDomicile domicile, Rank rank, int round) {
    this.input = input;
    this.domicile = domicile;
    this.rank = rank;
    this.round = round;
  }

  private final Pattern LINE_HOLDER = Pattern
      .compile("^([\\w\\s]+), ([\\w\\s-]+) \\((\\d+)\\)$");

  public DomicileAward parse() throws IOException {
    DomicileAward.Builder domicileAward = DomicileAward.newBuilder();

    Document document = Jsoup.parse(input);
    // One might think body > table:eq(1) is what we want.  But the way jsoup
    // works is that the count of elements in the eq parameter is not restricted
    // to the associated selector (table).  There are other elements (div, br..)
    // which are part of the count.  It seems best to extract just the tables
    // first, then use .get(1).
    Elements tables = document.select("body > table");
    Element awardTableBody = tables.select("tbody:eq(1)").first();
    String timestamp = tables.select("caption").text().trim();
    domicileAward.setTimestamp(timestamp);
    domicileAward.setDomicile(domicile.name());
    domicileAward.setRank(rank);
    domicileAward.setRound(round);

    Elements awardTableRows = awardTableBody.children();
    // First row is header, eg pilot monthly bid round 1
    awardTableRows = awardTableRows.next();
    // Second row is pilot type, eg Captain
    awardTableRows = awardTableRows.next();
    for (Element tr : awardTableRows) {
      String line = tr.select("td:eq(0)").text();
      String holder = tr.select("td:eq(1)").text();
      String trimmedHolder = ParseUtils.trim(holder);
      if (trimmedHolder.isEmpty()) {
        logger.info("Unassigned line: " + line);
        continue;
      }
      Matcher matcher = LINE_HOLDER.matcher(trimmedHolder);
      Preconditions.checkState(matcher.matches(), "[" + trimmedHolder + "]");
      String lastNameSuffix = matcher.group(1);
      String firstName = matcher.group(2);
      int employeeId = Integer.parseInt(matcher.group(3));
      domicileAward.addAward(Award.newBuilder()
          .setLine(line)
          .setPilot(Pilot.newBuilder()
              .setFirstMiddleName(firstName)
              .setLastNameSuffix(lastNameSuffix)
              .setEmployeeId(employeeId)));
    }
    return domicileAward.build();
  }
}
