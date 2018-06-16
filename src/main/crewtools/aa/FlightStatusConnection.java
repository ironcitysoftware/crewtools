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
import java.util.logging.Logger;

import crewtools.util.SimpleCookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FlightStatusConnection {
  private final Logger logger = Logger.getLogger(FlightStatusConnection.class.getName());

  private final Authenticator authenticator;
  private final DeviceIdFactory deviceIdGenerator;
  private final UserAgentFactory userAgentFactory;
  private final OkHttpClient httpclient;
  private final SimpleCookieJar cookieJar = new SimpleCookieJar();

  private static final String ACCEPT_HEADER =
      "application/vnd.aa.mobile.app+json;version=31.0";

  public FlightStatusConnection(UniqueIdProvider uniqueIdProvider) {
    this.httpclient = new OkHttpClient().newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(cookieJar)
        .build();
    this.userAgentFactory = new UserAgentFactory();
    this.authenticator = new Authenticator();
    this.deviceIdGenerator = new DeviceIdFactory(uniqueIdProvider);
  }

  void addStandardHeaders(Request.Builder request) {
    long currentMillis = System.currentTimeMillis();
    String deviceId = deviceIdGenerator.getDeviceId();
    request.header("accept", ACCEPT_HEADER);
    request.header("Version", UserAgentFactory.AA_APP_VERSION);
    request.header("Auth-Token",
        authenticator.getAuthToken(currentMillis, deviceId));
    request.header("Device-ID", deviceId);
    request.header("Timestamp", Long.toString(currentMillis));
    request.header("User-Agent", userAgentFactory.getUserAgent());
  }

  public String retrieveUrl(HttpUrl url) throws IOException {
    Request.Builder request = new Request.Builder()
        .url(url);
    addStandardHeaders(request);
    Response response = httpclient.newCall(request.build()).execute();
    return response.body().string();
  }
}

