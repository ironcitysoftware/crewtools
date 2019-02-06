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

package crewtools.flica.report;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Joiner;

import crewtools.flica.AwardDomicile;
import crewtools.flica.BaseFlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.report.OpenDutyPeriodDiscrepancyReport.Report;
import crewtools.flica.report.OpenDutyPeriodDiscrepancyReport.ReportItem;
import crewtools.rpc.Proto.ReportRequest;
import crewtools.rpc.Proto.ReportResponse;
import crewtools.util.FlicaConfig;

public class OpenDutyPeriodDiscrepancyReportAdapter {
  private final Logger logger = Logger
      .getLogger(OpenDutyPeriodDiscrepancyReportAdapter.class.getName());

  private final ReportRequest request;
  private final OutputStream output;
  private final ReportResponse.Builder response;
  private static final int ROUND = FlicaService.BID_FIRST_COME;

  public OpenDutyPeriodDiscrepancyReportAdapter(ReportRequest request,
      OutputStream output) {
    this.request = request;
    this.output = output;
    this.response = ReportResponse.newBuilder();
  }

  public void run() throws IOException {
    runInternal();
    response.build().writeTo(output);
  }

  private void runInternal() {
    if (!request.hasFlicaUsername() || !request.hasFlicaPassword()) {
      response.setError("Missing credentials");
      return;
    }

    Properties properties = new Properties();
    properties.put(FlicaConfig.FLICA_USERNAME, request.getFlicaUsername());
    properties.put(FlicaConfig.FLICA_PASSWORD, request.getFlicaPassword());
    properties.put(FlicaConfig.DATA_DIRECTORY, "/tmp/flica-report");
    properties.put(FlicaConfig.INTERESTING_EMPLOYEE_ID, "0");
    properties.put(FlicaConfig.SESSION_CACHE_FILE,
        request.getFlicaUsername() + ".cache");
    properties.put(FlicaConfig.DOMICILES, "");
    FlicaConfig config = new FlicaConfig(properties);

    if (!request.hasYearMonth()) {
      response.setError("Missing year/month");
      return;
    }

    YearMonth yearMonth = YearMonth.parse(request.getYearMonth());
    Rank rank = Rank.valueOf(request.getRank());
    final List<AwardDomicile> awardDomiciles = new ArrayList<>();
    if (request.getAwardDomicileCount() > 0) {
      request.getAwardDomicileList().forEach(
          str -> awardDomiciles.add(AwardDomicile.valueOf(str)));
    } else {
      awardDomiciles.addAll(Arrays.asList(AwardDomicile.values()));
    }

    try (BaseFlicaConnection connection = new BaseFlicaConnection(config)) {
      FlicaService service = new FlicaService(connection);
      OpenDutyPeriodDiscrepancyReport generator = new OpenDutyPeriodDiscrepancyReport(
          service, yearMonth, rank, awardDomiciles, request.getIgnoreTrailingDutyDay());
      Report report = generator.generateReport();
      response.setHtml(formatReport(report, yearMonth, rank, awardDomiciles));
    } catch (IOException | ParseException | URISyntaxException e) {
      logger.log(Level.INFO, "Error generating report", e);
      response.setError("Error generating report");
      return;
    }
  }

  private String formatReport(
      Report report, YearMonth yearMonth, Rank rank, List<AwardDomicile> awardDomiciles) {
    String html = "<table>\n";
    String firstHeader = " <tr><th />";
    String secondHeader = " <tr><th />";
    for (AwardDomicile awardDomicile : awardDomiciles) {
      firstHeader += String.format("<th colspan=2><strong>%s</strong></th>",
          awardDomicile.name());
      secondHeader += String.format("<th><a target=\"_blank\" href=\"%s\">grid</a></th>",
          FlicaService.getReserveGridUrl(awardDomicile, rank, ROUND, yearMonth));
      secondHeader += String.format("<th><a target=\"_blank\" href=\"%s\">pot</a></th>",
          FlicaService.getOpenTimeUrl(awardDomicile, rank, ROUND, yearMonth));
    }
    firstHeader += "</tr>\n";
    secondHeader += "</tr>\n";
    html += firstHeader + secondHeader;
    for (LocalDate date : report.rows.keySet()) {
      String row = "<tr><td class=\"date\">" + date + "</td>";
      for (AwardDomicile awardDomicile : awardDomiciles) {
        ReportItem item = report.rows.get(date).items.get(awardDomicile);
        boolean isDiscrepancy = item.numGridOpenDutyPeriods != item.opentimeTasks.size();
        row += String.format("<td class=\"%s\">%d</td>",
            isDiscrepancy ? "yellow" : "",
            item.numGridOpenDutyPeriods);
        String tripInfo = !isDiscrepancy
            ? ""
            : getOpentimeInfo(item.opentimeTasks);
        row += String.format("<td class=\"%s\">%d<br />%s</td>",
            isDiscrepancy ? "yellow wide" : "wide",
            item.opentimeTasks.size(),
            tripInfo);
      }
      row += "</tr>\n";
      html += row;
    }
    html += "</table>";

    if (!report.shortenedTrips.isEmpty()) {
      html += "<p>Shortened trips due to ignore trailing duty day:<br />";
      html += "<table><tr><td>";
      html += getOpentimeInfo(report.shortenedTrips);
      html += "</td></tr></table>";
    }
    return html;
  }

  private static final Joiner SPACE_JOINER = Joiner.on(" ");

  private String getOpentimeInfo(Set<FlicaTask> tasks) {
    List<String> urls = new ArrayList<>();
    tasks.forEach(task -> urls.add(
        String.format("<a target=\"blank\" href=\"%s\">%s</a>",
            FlicaService.getPairingDetailUrl(task.pairingName, task.pairingDate),
            task.pairingName)));
    return SPACE_JOINER.join(urls);
  }
}
