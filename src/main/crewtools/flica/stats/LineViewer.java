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

package crewtools.flica.stats;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.io.Resources;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto.Award;
import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.DomicileAward;
import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.Rank;
import crewtools.flica.Proto.ThinLine;
import crewtools.flica.Proto.ThinLineList;
import crewtools.flica.Proto.Trip;
import crewtools.flica.formatter.ThinLineFormatter;
import crewtools.flica.formatter.TripFormatter;
import crewtools.util.FlicaConfig;
import crewtools.util.PilotMatcher;

public class LineViewer extends AbstractHandler {
  private final ThinLineList lines;
  private final YearMonth yearMonth;
  private final AwardDomicile awardDomicile;
  private final Rank rank;
  private final int round;
  private final Map<String, Trip> trips;
  private final ThinLineFormatter lineFormatter;
  private final TripFormatter tripFormatter;
  private final DomicileAward domicileAward;
  private final int interestingEmployeeId;
  private final PilotMatcher matcher;

  public static void main(String args[]) throws Exception {
    new LineViewer(args).run();
  }

  public LineViewer(String args[]) throws FileNotFoundException, IOException {
    Preconditions.checkState(args.length == 4, "LineViewer CLT 2018-12 CAPTAIN 1");
    this.awardDomicile = AwardDomicile.valueOf(args[0]);
    this.yearMonth = YearMonth.parse(args[1]);
    this.rank = Rank.valueOf(args[2]);
    this.round = Integer.parseInt(args[3]);
    DataReader dataReader = new DataReader();
    this.lines = dataReader.readLines(yearMonth, awardDomicile, rank, round);
    this.trips = new HashMap<>();
    PairingList pairings = dataReader.readPairings(yearMonth, awardDomicile);
    for (Trip trip : pairings.getTripList()) {
      this.trips.put(trip.getPairingName(), trip);
    }
    File award = new File(
        dataReader.getAwardFilename(yearMonth, awardDomicile, rank, round));
    File seniority = new File(
        dataReader.getSeniorityFilename(yearMonth));
    this.domicileAward = award.exists() ?
        dataReader.readAwards(yearMonth, awardDomicile, rank, round) : null;
    this.matcher = award.exists() && seniority.exists() ?
        new PilotMatcher(dataReader.readSeniorityList(yearMonth)) : null;
    this.lineFormatter = new ThinLineFormatter(yearMonth);
    this.tripFormatter = new TripFormatter();
    this.interestingEmployeeId = Integer
        .parseInt(new FlicaConfig().getInterestingEmployeeId());
  }

  public void run() throws Exception {
    Server server = new Server(8080);
    server.setHandler(this);
    server.start();
    server.join();
  }

  @Override
  public void handle(String target, Request baseRequest,
      HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    response.setContentType("text/html; charset=utf-8");
    response.setStatus(HttpServletResponse.SC_OK);
    if (target.startsWith("/detail/")) {
      writePairing(target, response.getWriter());
    } else {
      writeLines(target, response.getWriter());
    }
    baseRequest.setHandled(true);
  }

  private void writeLines(String target, PrintWriter writer) throws IOException {
    // TODO: make final
    String base = Resources.toString(
        LineViewer.class.getResource("lineview.html"),
        StandardCharsets.UTF_8);
    base = base.replace("LINE_HTML", getLinesTable());
    writer.println(base);
  }

  private void writePairing(String target, PrintWriter writer) throws IOException {
    String pairingName = target.substring("/detail/".length());
    if (!trips.containsKey(pairingName)) {
      writer.printf("Pairing %s not found?", pairingName);
    } else {
      writer.println(getPairingTable(trips.get(pairingName)));
    }
  }

  private String getPairingTable(Trip trip) {
    String result = trip.getPairingName() + " ";
    if (trip.getSectionCount() > 0) {
      result += String.format("BSE REPT: %sL<br />",
        trip.getSection(0).getLocalDutyStartTime());
    }
    result += tripFormatter.getHtml(trip);
    return result;
  }

  private String getLinesTable() {
    String result = String.format("Domicile %s Rank %s Round %s Date %s "
        + "Employee %d<br /><table>",
        awardDomicile, rank, round, yearMonth, interestingEmployeeId);
    if (lines.getThinLineCount() > 0) {
      result += lineFormatter.getHeaderHtml();
    }
    for (ThinLine line : lines.getThinLineList()) {
      boolean eligible = true;
      if (domicileAward != null) {
        eligible = !isAwardedToSeniorPerson(line.getLineName());
      }
      result += lineFormatter.getRowHtml(line, awardDomicile, trips, eligible);
    }
    return result + "</table>\n";
  }

  private boolean isAwardedToSeniorPerson(String line) {
    for (Award award : domicileAward.getAwardList()) {
      if (line.equals(award.getLine())) {
        CrewMember member = Preconditions.checkNotNull(
            matcher.matchCrewMember(award.getPilot()));
        return member.getEmployeeId() < interestingEmployeeId;
      }
    }
    return false;
  }
}