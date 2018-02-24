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

package crewtools.flica.retriever;

import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import crewtools.flica.AwardDomicile;
import crewtools.flica.CachingFlicaService;
import crewtools.flica.FlicaConnection;
import crewtools.flica.FlicaService;
import crewtools.flica.Proto.Rank;
import crewtools.util.FlicaConfig;

// Suggests lines to bid for monthly initial bid.
public class ReserveGridRetriever {
  private final Logger logger = Logger.getLogger(ReserveGridRetriever.class.getName());

  public static void main(String args[]) throws Exception {
    new ReserveGridRetriever().run(args);
  }

  private DateTimeFormatter DATE_MONTH_FORMAT = DateTimeFormat.forPattern("ddMMM");

  public void run(String args[]) throws Exception {
    YearMonth yearMonth = YearMonth.parse("2017-09");
    LocalDate today = new DateTime().toLocalDate();

    FlicaConnection connection = new FlicaConnection(new FlicaConfig());
    FlicaService service = new FlicaService(connection);
    service.connect();

    int round = Integer.parseInt(args[0]);
    AwardDomicile awardDomicile = AwardDomicile.valueOf(args[1]);
    Rank rank = Rank.valueOf(args[2]);
    
    if (false) {
      System.out.println(service.getBidAward(awardDomicile, rank, round, yearMonth));
      return;
    }
    
    String rawReserveGridJson = service.getReserveGrid(
        awardDomicile,
        rank,
        round,
        yearMonth //.plusMonths(1),  // need + 1 on the BCID.. why?
        );

    logger.info(rawReserveGridJson);
    
    JsonParser parser = new JsonParser();
    JsonObject jsonObject = parser.parse(rawReserveGridJson).getAsJsonObject();
    Preconditions.checkState(
        jsonObject.get("success").getAsBoolean(),
        jsonObject.get("message").getAsString());
    JsonObject root = jsonObject.get("root").getAsJsonObject();
    JsonArray data = root.get("data").getAsJsonArray();

    for (int i = 0; i < data.size(); ++i) {
      JsonObject object = data.get(i).getAsJsonObject();
      String rawDate = object.get("date").getAsString();
      LocalDate date = DATE_MONTH_FORMAT.parseLocalDate(rawDate).withYear(yearMonth.getYear());
      int availableReserves = object.get("avl").getAsInt();
      int openDutyPeriods = object.get("odp").getAsInt();
      int netReserves = object.get("net").getAsInt();
      Preconditions.checkState(netReserves == availableReserves - openDutyPeriods);
      int minimumRequired = object.get("minReq").getAsInt();
      if (object.has("critical")) {
        String critical = object.get("critical").getAsString();
      }

      if (!date.isBefore(today)) {
//        System.out.printf("%s : avail %d / open %d / net %d / min %d\n", date, availableReserves,
//            openDutyPeriods, netReserves, minimumRequired);
        if (netReserves > minimumRequired) {
          System.out.printf("%s is green\n", date);
        }
      }
    }
  }
}
