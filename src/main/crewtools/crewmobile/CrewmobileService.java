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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.TextFormat;
import com.google.protobuf.util.JsonFormat;

import crewtools.crewmobile.Proto.AccessToken;
import crewtools.crewmobile.Proto.AppConfig;
import crewtools.crewmobile.Proto.CalendarDataFeed;
import crewtools.crewmobile.Proto.CrewmobileConfig;
import crewtools.crewmobile.Proto.RefreshToken;
import okhttp3.HttpUrl;

public class CrewmobileService {
  private final Logger logger = Logger.getLogger(CrewmobileService.class.getName());

  private static final File REFRESH_TOKEN = new File(
      System.getProperty("java.io.tmpdir") + "/crewmobile.refreshtoken.txt");

  private static final File ACCESS_TOKEN = new File(
      System.getProperty("java.io.tmpdir") + "/crewmobile.accesstoken.txt");

  private final CrewmobileConfig config;
  private AppConfig appConfig;
  private final Gson gson;
  private final CrewmobileConnection connection;
  private final CrewmobileOidcClient oidcClient;
  private RefreshToken refreshToken;

  public CrewmobileService(CrewmobileConfig crewmobileConfig) throws Exception {
    this.config = crewmobileConfig;
    this.gson = new GsonBuilder().create();
    this.connection = new CrewmobileConnection();
    this.oidcClient = (CrewmobileOidcClient) Class
        .forName(crewmobileConfig.getOidcClientClassName())
        .newInstance();
  }

  private RefreshToken readOrGetRefreshToken() throws IOException {
    if (REFRESH_TOKEN.exists()) {
      logger.info("Using cached refresh token");
      RefreshToken.Builder builder = RefreshToken.newBuilder();
      TextFormat.getParser().merge(
          Files.toString(REFRESH_TOKEN, UTF_8), builder);
      return builder.build();
    } else {
      AccessToken accessToken = readOrGetAccessToken();
      RefreshToken refreshToken;
      try {
        refreshToken = oidcClient.getRefreshToken(accessToken);
      } catch (Exception e) {
        throw new IOException(e);
      }
      Files.write(TextFormat.printToString(refreshToken), REFRESH_TOKEN, UTF_8);
      return refreshToken;
    }
  }

  private AccessToken readOrGetAccessToken() throws IOException {
    if (ACCESS_TOKEN.exists()) {
      logger.info("Using cached access token");
      AccessToken.Builder builder = AccessToken.newBuilder();
      TextFormat.getParser().merge(
          Files.toString(ACCESS_TOKEN, UTF_8), builder);
      return builder.build();
    } else {
      AccessToken accessToken;
      try {
        accessToken = oidcClient.runImplicitFlow(config);
      } catch (Exception e) {
        throw new IOException(e);
      }
      Files.write(TextFormat.printToString(accessToken), ACCESS_TOKEN, UTF_8);
      return accessToken;
    }
  }

  public synchronized void connect() throws IOException {
    Map<String, String> data = ImmutableMap.of(
        "applicationName", "CrewMobile",
        "refreshToken", readOrGetRefreshToken().getRefreshToken());
    String json = connection.postUrlString(
        HttpUrl.parse(config.getAppConfigUrl()),
        gson.toJson(data));
    AppConfig.Builder builder = AppConfig.newBuilder();
    JsonFormat.parser().merge(json, builder);
    this.appConfig = builder.build();
  }

  public synchronized CalendarDataFeed getCalendarDataFeed() throws IOException {
    String json = connection.retrieveUrlString(
        HttpUrl.parse(config.getCalendarDataFeedUrl()),
        appConfig.getToken());
    // response is an array of Month objects, but the parser doesn't handle
    // a top-level array.
    json = "{\"month\":" + json + "}";
    CalendarDataFeed.Builder builder = CalendarDataFeed.newBuilder();
    JsonFormat.parser().merge(json, builder);
    return builder.build();
  }
}
