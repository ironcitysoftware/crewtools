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
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.flica.parser.OpenTimeParser;
import crewtools.flica.parser.ParseException;
import crewtools.flica.parser.ReserveGridParser;
import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.ReserveGridEntry;
import crewtools.util.Calendar;
import crewtools.util.FlicaConfig;

public class OpenDutyPeriodDiscrepancyReport {
  private final Logger logger = Logger
      .getLogger(OpenDutyPeriodDiscrepancyReport.class.getName());

  private final FlicaService service;
  private final YearMonth yearMonth;
  private final Rank rank;
  private final List<AwardDomicile> awardDomiciles;

  public static void main(String args[]) throws Exception {
    FlicaConfig config = FlicaConfig.readConfig();
    FlicaConnection conn = new FlicaConnection(config);
    FlicaService service = new FlicaService(conn);
    YearMonth yearMonth = new YearMonth(2019, 2);
    Rank rank = Rank.FIRST_OFFICER;
    List<AwardDomicile> awardDomiciles = Arrays.asList(AwardDomicile.values());
    Report report = new OpenDutyPeriodDiscrepancyReport(
        service, yearMonth, rank, awardDomiciles)
        .generateReport();

    System.out.println("OpenDutyPeriodDiscrepancyReport grid/ot");
    System.out.print("             ");
    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      System.out.print(awardDomicile.name() + "       ");
    }
    System.out.println();
    ;
    for (LocalDate date : report.rows.keySet()) {
      System.out.printf(date + " | ");
      for (AwardDomicile awardDomicile : awardDomiciles) {
        ReportItem item = report.rows.get(date).items.get(awardDomicile);
        System.out.printf("%02d/%02d%s   ",
            item.numGridOpenDutyPeriods,
            item.opentimeTasks.size(),
            item.numGridOpenDutyPeriods != item.opentimeTasks.size() ? "**" : "  ");
      }
      System.out.println();
    }
  }

  public OpenDutyPeriodDiscrepancyReport(FlicaService service, YearMonth yearMonth,
      Rank rank, List<AwardDomicile> awardDomiciles) {
    this.service = service;
    this.yearMonth = yearMonth;
    this.rank = rank;
    this.awardDomiciles = awardDomiciles;
  }

  public static class Report {
    public Map<LocalDate, ReportRow> rows = new TreeMap<>();
  }

  public static class ReportRow {
    public Map<AwardDomicile, ReportItem> items = new TreeMap<>();
  }

  public static class ReportItem {
    public ReportItem(int numGridOpenDutyPeriods, Set<FlicaTask> opentimeTasks) {
      this.numGridOpenDutyPeriods = numGridOpenDutyPeriods;
      this.opentimeTasks = opentimeTasks;
    }

    public final int numGridOpenDutyPeriods;
    public final Set<FlicaTask> opentimeTasks;
  }

  public Report generateReport()
      throws IOException, URISyntaxException, ParseException {
    Calendar calendar = new Calendar(yearMonth);
    List<LocalDate> dates = calendar.getRemainingDatesInPeriod(new LocalDate());
    Report report = new Report();
    for (AwardDomicile awardDomicile : awardDomiciles) {
      SetMultimap<LocalDate, FlicaTask> opentimeMap = getOpentimeMap(awardDomicile);
      Map<LocalDate, ReserveGridEntry> reserveGrid = getReserveGrid(awardDomicile);
      for (LocalDate date : dates) {
        if (!report.rows.containsKey(date)) {
          report.rows.put(date, new ReportRow());
        }
        ReportRow row = report.rows.get(date);
        ReserveGridEntry entry = reserveGrid.get(date);
        ReportItem item = new ReportItem(entry.openDutyPeriods, opentimeMap.get(date));
        row.items.put(awardDomicile, item);
      }
    }
    return report;
  }

  private SetMultimap<LocalDate, FlicaTask> getOpentimeMap(
      AwardDomicile awardDomicile)
      throws URISyntaxException, IOException, ParseException {
    String openTimeResponse = service.getOpenTime(awardDomicile, rank,
        FlicaService.BID_FIRST_COME, yearMonth);
    OpenTimeParser openTimeParser = new OpenTimeParser(yearMonth.getYear(),
        openTimeResponse);
    if (openTimeParser.isUnprivileged()) {
      throw new IOException("Unprivileged seat");
    }
    List<FlicaTask> tasks = openTimeParser.parse();
    SetMultimap<LocalDate, FlicaTask> taskMap = HashMultimap.create();
    tasks.forEach(task -> {
      if (task.tradeboardRequestId == null) {
        for (int i = 0; i < task.numDays; ++i) {
          taskMap.put(task.pairingDate.plusDays(i), task);
        }
      }
    });
    return taskMap;
  }

  private Map<LocalDate, ReserveGridEntry> getReserveGrid(
      AwardDomicile awardDomicile) throws URISyntaxException, IOException {
    String reserveGridRequest = service.getReserveGrid(awardDomicile, rank,
        FlicaService.BID_FIRST_COME, yearMonth);
    ReserveGridParser reserveGridParser = new ReserveGridParser();
    return reserveGridParser.parse(
        yearMonth.getYear(), reserveGridRequest);
  }
}
