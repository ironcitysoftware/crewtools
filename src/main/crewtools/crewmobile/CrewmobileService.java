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

package crewtools.crewmobile;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import crewtools.util.FlicaConfig;
import okhttp3.HttpUrl;

public class CrewmobileService {
  private final Logger logger = Logger.getLogger(CrewmobileService.class.getName());

  private final FlicaConfig flicaConfig;
  private AppConfig appConfig;
  private final Gson gson;
  private final CrewmobileConnection connection;

  public CrewmobileService(FlicaConfig flicaConfig) {
    this.flicaConfig = flicaConfig;
    this.gson = new GsonBuilder().create();
    this.connection = new CrewmobileConnection();
  }

  public synchronized void connect(String bearerToken) throws IOException {
    Map<String, String> data = ImmutableMap.of(
        "applicationName", "CrewMobile",
        "refreshToken", bearerToken);
    String raw = connection.postUrlString(
        HttpUrl.parse(flicaConfig.getCrewmobileAppConfigUrl()),
        gson.toJson(data));
    this.appConfig = new AppConfig(raw);
  }
}
