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

package crewtools.aa;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.joda.time.LocalDate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FlightStatusService {
  private final FlightStatusConnection connection;

  public FlightStatusService() {
    this.connection = new FlightStatusConnection(new UniqueIdProvider());
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 3) {
      System.err.println("flightStatus flightNumber month day");
      System.exit(-1);
    }
    int flightNumber = Integer.parseInt(args[0]);
    LocalDate localDate = new LocalDate(
        2017,
        Integer.parseInt(args[1]),
        Integer.parseInt(args[2]));
    System.out.printf("Flight %d on %s:\n", flightNumber, localDate);
    new FlightStatusService().getFlightStatus(flightNumber, localDate);
  }

  private static final int API_VERSION = 32;

  private static final String FLIGHT_STATUS_URL_FORMAT_SPEC =
      "https://cdn.flyaa.aa.com/mws_v%d/flightstatus?"
      + "departureMonth=%d&departureDay=%d&flightNumber=%d";


  public void getFlightStatus(int flightNumber, LocalDate localDate) throws ClientProtocolException, IOException {
    String url = String.format(FLIGHT_STATUS_URL_FORMAT_SPEC,
        API_VERSION,
        localDate.getMonthOfYear(),
        localDate.getDayOfMonth(),
        flightNumber);
    String json = connection.retrieveUrl(url);

    JsonParser parser = new JsonParser();
    JsonObject jsonObject = parser.parse(json).getAsJsonObject();
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String prettyJson = gson.toJson(jsonObject);
    System.out.println(prettyJson);
  }
}
