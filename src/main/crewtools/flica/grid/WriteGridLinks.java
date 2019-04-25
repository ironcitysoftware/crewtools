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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.joda.time.YearMonth;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;

public class WriteGridLinks {
  private static final String OUTPUT_PATH = "/tmp/links.html";
  private static final YearMonth YEAR_MONTH = new YearMonth(2019, 5);

  public static void main(String args[]) throws Exception {
    new WriteGridLinks().run();
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
    writer.println("<h2>FLICA Links " + YEAR_MONTH + "</h2>");
    writer.println("<table>");
    writer.println("  <tr><th />");
    for (AwardDomicile domicile : AwardDomicile.values()) {
      writer.printf("<th>%s</th>\n", domicile.name());
    }
    writer.println("  </tr>");
    for (Rank rank : Rank.values()) {
      writer.println("<tr><td>" + rank.name() + "</td>");
      for (AwardDomicile domicile : AwardDomicile.values()) {
        writer.printf("  <td><a target=_blank href=\"%s\">reserve grid</a>, "
            + "<a target=_blank href=\"%s\">opentime pot</a></td>\n",
            FlicaService.getReserveGridUrl(domicile, rank, FlicaService.BID_FIRST_COME,
                YEAR_MONTH),
            FlicaService.getOpenTimeUrl(domicile, rank, FlicaService.BID_FIRST_COME,
                YEAR_MONTH));
      }
      writer.print("</tr>");
    }
    for (Rank rank : Rank.values()) {
      writer.println("<tr><td>" + rank.name() + "</td>");
      for (AwardDomicile domicile : AwardDomicile.values()) {
        writer.printf("  <td><a target=_blank href=\"%s\">lines</a>, "
            + "<a target=_blank href=\"%s\">pairings</a></td>\n",
            FlicaService.getAllLinesUrl(domicile, rank, FlicaService.BID_ROUND_ONE,
                YEAR_MONTH),
            FlicaService.getAllPairingsUrl(domicile, rank, FlicaService.BID_ROUND_ONE,
                YEAR_MONTH));
      }
      writer.print("</tr>");
    }
    writer.println("</table>");
    writer.println("</body></html>");
    writer.close();
    System.err.println("Wrote to " + OUTPUT_PATH);
  }
}
