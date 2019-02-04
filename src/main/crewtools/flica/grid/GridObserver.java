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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.TextFormat;

import crewtools.flica.AwardDomicile;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.rpc.Proto.GridObservation;
import crewtools.rpc.Proto.Observation;
import crewtools.util.FlicaConfig;

public class GridObserver {
  private final Logger logger = Logger.getLogger(GridObserver.class.getName());

  private DateTimeFormatter DATE_MONTH_FORMAT = DateTimeFormat.forPattern("ddMMM");

  private File path;
  private AwardDomicile domicile;
  private Rank rank;
  private YearMonth yearMonth;
  private FlicaService service;
  private GridObservation lastObservation;

  public static void main(String args[]) throws Exception {
    if (args.length == 0) {
      System.err.println("GridObserver /path/to/observation_dir PHL CAPTAIN 2019-03");
      System.exit(-1);
    }
    new GridObserver(args).run();
  }

  public GridObserver(String args[]) throws Exception {
    path = new File(args[0]);
    domicile = AwardDomicile.valueOf(args[1]);
    rank = Rank.valueOf(args[2]);
    yearMonth = YearMonth.parse(args[3]);
    lastObservation = getMostRecentObservation();
  }

  private GridObservation getMostRecentObservation()
      throws FileNotFoundException, IOException {
    File[] files = path.listFiles(file -> {
      return file.isFile();
    });
    if (files.length == 0) {
      return null;
    }
    Arrays.sort(files, (a, b) -> {
      return -new Long(a.lastModified()).compareTo(b.lastModified());
    });
    File mostRecentFile = files[0];
    logger.info("Reading most recent observation " + mostRecentFile);
    GridObservation.Builder builder = GridObservation.newBuilder();
    TextFormat.getParser().merge(
        Files.toString(mostRecentFile, StandardCharsets.UTF_8), builder);
    return builder.build();
  }

  private void writeGridObservation(GridObservation observation)
      throws FileNotFoundException, IOException {
    File file = new File(path, String.format("%s-%s-%s-%d",
        domicile.name(), rank.name(), yearMonth.toString(),
        observation.getObservationTime()));
    Preconditions.checkState(!file.exists());
    FileWriter writer = new FileWriter(file);
    TextFormat.print(observation, writer);
    writer.close();
    lastObservation = observation;
  }

  public void run() throws Exception {
    while (true) {
      observeAndNoteAnyChanges();
      Thread.sleep(1000 * 60 * 30);
    }
  }

  private void observeAndNoteAnyChanges() throws Exception {
    GridObservation observation = observe();
    if (lastObservation == null) {
      writeGridObservation(observation);
      logger.info("Writing initial observation.");
      return;
    }
    GridObservation.Builder old = lastObservation.toBuilder().clearObservationTime();
    GridObservation.Builder newer = observation.toBuilder().clearObservationTime();
    if (old.build().equals(newer.build())) {
      logger.info("No change in grid.");
    } else {
      logger.info("Grid change, writing new observation.");
      writeGridObservation(observation);
    }
  }

  private GridObservation observe() throws IOException, URISyntaxException {
    FlicaConnection connection = new FlicaConnection(FlicaConfig.readConfig());
    FlicaService service = new FlicaService(connection);

    String rawReserveGridJson = service.getReserveGrid(domicile, rank, 4, yearMonth);
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
