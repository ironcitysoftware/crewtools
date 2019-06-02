/**
 * Copyright 2019 Iron City Software LLC
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

package crewtools.flica.grid;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Map;

import org.joda.time.YearMonth;

import com.google.common.collect.ImmutableMap;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;

public class WriteGridLinks {
  private static final String OUTPUT_PATH = "/tmp/links.html";
  private final YearMonth yearMonth;

  public static void main(String args[]) throws Exception {
    if (args.length == 0) {
      System.err.println("writeGridLinks 2019-6");
      System.exit(-1);
    }
    new WriteGridLinks(YearMonth.parse(args[0])).run();
  }

  public WriteGridLinks(YearMonth yearMonth) {
    this.yearMonth = yearMonth;
  }

  private static final String CSS = "html, body {\n"
      + "  margin: 0;\n"
      + "  padding: 0;\n"
      + "  font-family: \"Trebuchet MS, Helvetica, sans-serif\";\n"
      + "}\n"
      + "table {\n"
      + "  border-collapse: collapse;\n"
      + "}\n"
      + "table td {\n"
      + "  padding: 0.5em;\n"
      + "  border: 1px solid black;\n"
      + "  vertical-align: top;\n"
      + "}\n";

  public void run() throws Exception {
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT_PATH)));
    writer.println("<html><head><style>");
    writer.println(CSS);
    writer.println("</style></head><body>");
    writer.println(
        "<br/><font size=2>Notes: Log into FLICA first.  Opentime pot and tradeboard are restricted to your seat.</font>");
    writeLinks(writer, yearMonth);
    writer.println("<hr/>");
    writeLinks(writer, yearMonth.plusMonths(1));
    writer.println("</body></html>");
    writer.close();
    System.err.println("Wrote to " + OUTPUT_PATH);
  }

  private static final Map<Rank, String> SHORT_RANK = ImmutableMap.of(Rank.CAPTAIN, "CA",
      Rank.FIRST_OFFICER, "FO");

  private void writeLinks(PrintWriter writer, YearMonth yearMonth)
      throws IOException, URISyntaxException {
    writer.println("<h2>FLICA Links " + yearMonth + "</h2>");
    writer.println("<table>");
    writer.println("  <tr><th />");
    for (AwardDomicile domicile : AwardDomicile.values()) {
      writer.printf("<th>%s</th>\n", domicile.name());
    }
    writer.println("  </tr>");
    for (Rank rank : Rank.values()) {
      writer.println("<tr><td>" + SHORT_RANK.get(rank) + "</td>");
      for (AwardDomicile domicile : AwardDomicile.values()) {
        writer.printf("  <td><a target=_blank href=\"%s\">reserve grid</a>,<br />\n"
            + "<a target=_blank href=\"%s\">reserve avail</a>,<br />\n"
            + "<a target=_blank href=\"%s\">opentime pot</a>,<br />\n"
            + "<a target=_blank href=\"%s\">lines</a>,<br />\n"
            + "<a target=_blank href=\"%s\">pairings</a>,<br />\n"
            + "<a target=_blank href=\"%s\">tradeboard</a></td>",
            FlicaService.getReserveGridUrl(domicile, rank, FlicaService.BID_FIRST_COME,
                yearMonth),
            FlicaService.getReserveAvailabilityUrl(domicile, rank,
                FlicaService.BID_FIRST_COME,
                yearMonth),
            FlicaService.getOpenTimeUrl(domicile, rank, FlicaService.BID_FIRST_COME,
                yearMonth),
            FlicaService.getAllLinesUrl(domicile, rank, FlicaService.BID_ROUND_ONE,
                yearMonth),
            FlicaService.getAllPairingsUrl(domicile, rank, FlicaService.BID_ROUND_ONE,
                yearMonth),
            FlicaService.getTradeBoardAllRequestsUrl(domicile,
                FlicaService.BID_TRADEBOARD,
                yearMonth));
      }
      writer.print("</tr>");
    }
    writer.println("</table>");
  }
}
