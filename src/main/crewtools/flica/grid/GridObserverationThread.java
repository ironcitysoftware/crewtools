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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.rpc.Proto.GridObservation;
import crewtools.rpc.Proto.Observation;
import crewtools.util.PeriodicDaemonThread;

public class GridObserverationThread extends PeriodicDaemonThread {
  private final Logger logger = Logger.getLogger(GridObserverationThread.class.getName());

  private static final Duration NO_INITIAL_DELAY = Duration.ZERO;

  private DateTimeFormatter DATE_MONTH_FORMAT = DateTimeFormat.forPattern("ddMMM");

  private final YearMonth yearMonth;
  private final FlicaService service;
  private final AwardDomicile domicile;
  private final Rank rank;
  private final Observer observer;

  public GridObserverationThread(
      Duration interval,
      YearMonth yearMonth,
      FlicaService service,
      AwardDomicile domicile,
      Rank rank,
      Observer observer) {
    super(NO_INITIAL_DELAY, interval);
    this.yearMonth = yearMonth;
    this.service = service;
    this.domicile = domicile;
    this.rank = rank;
    this.observer = observer;
    this.setName("GridObservationThread");
    this.setDaemon(true);
  }

  /**
   * Returns COMPLETE if the work succeeded.
   * If INCOMPLETE is returned, the thread will sleep FAILURE_INTERVAL and retry.
   */
  @Override
  protected WorkResult doPeriodicWork() {
    try {
      observer.observe(getGridObservation());
      return WorkResult.COMPLETE;
    } catch (IOException | URISyntaxException e) {
      logger.log(Level.WARNING, "Failure to observe grid", e);
      return WorkResult.INCOMPLETE;
    }
  }

  private GridObservation getGridObservation() throws IOException, URISyntaxException {
    String rawReserveGridJson = service.getReserveGrid(domicile, rank, FlicaService.BID_FIRST_COME, yearMonth);
    JsonParser parser = new JsonParser();
    JsonObject jsonObject = parser.parse(rawReserveGridJson).getAsJsonObject();
    Preconditions.checkState(
        jsonObject.get("success").getAsBoolean(),
        jsonObject.get("message").getAsString());
    JsonObject root = jsonObject.get("root").getAsJsonObject();
    JsonArray data = root.get("data").getAsJsonArray();

    GridObservation.Builder builder = GridObservation.newBuilder()
        .setObservationTime(new DateTime().withZone(DateTimeZone.UTC).getMillis())
        .setDomicile(domicile.name())
        .setRank(rank.name())
        .setYear(yearMonth.getYear())
        .setMonth(yearMonth.getMonthOfYear());

    for (int i = 0; i < data.size(); ++i) {
      Observation.Builder observation = builder.addObservationBuilder();
      JsonObject object = data.get(i).getAsJsonObject();
      String rawDate = object.get("date").getAsString();
      LocalDate date = DATE_MONTH_FORMAT.parseLocalDate(rawDate)
          .withYear(yearMonth.getYear());
      int availableReserves = object.get("avl").getAsInt();
      int openDutyPeriods = object.get("odp").getAsInt();
      observation
          .setMonth(date.getMonthOfYear())
          .setDay(date.getDayOfMonth())
          .setAvailableReserve(availableReserves)
          .setOpenDutyPeriods(openDutyPeriods);
      int netReserves = object.get("net").getAsInt();
      Preconditions.checkState(netReserves == availableReserves - openDutyPeriods);
      int minimumRequired = object.get("minReq").getAsInt();
      observation
          .setMinRequired(minimumRequired);
    }
    return builder.build();
  }
}
