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

import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class FlightStatusConnection {
  private final Logger logger = Logger.getLogger(FlightStatusConnection.class.getName());

  private final CookieStore cookieStore;
  private final CloseableHttpClient httpclient;
  private final Authenticator authenticator;
  private final DeviceIdFactory deviceIdGenerator;
  private final UserAgentFactory userAgentFactory;

  private static final String ACCEPT_HEADER =
      "application/vnd.aa.mobile.app+json;version=31.0";

  private static class DisableRedirectStrategy extends DefaultRedirectStrategy {
    @Override
    protected boolean isRedirectable(final String method) {
      return false;
    }
  }

  public FlightStatusConnection(UniqueIdProvider uniqueIdProvider) {
    this.cookieStore = new BasicCookieStore();
    this.userAgentFactory = new UserAgentFactory();
    this.httpclient = HttpClients.custom()
        .setDefaultCookieStore(cookieStore)
        .setUserAgent(userAgentFactory.getUserAgent())
        .setDefaultRequestConfig(RequestConfig.custom()
            .setCookieSpec(CookieSpecs.STANDARD)
            .build())
        .setRedirectStrategy(new DisableRedirectStrategy())
        .build();
    this.authenticator = new Authenticator();
    this.deviceIdGenerator = new DeviceIdFactory(uniqueIdProvider);
  }

  void addStandardHeaders(HttpRequest request) {
    long currentMillis = System.currentTimeMillis();
    String deviceId = deviceIdGenerator.getDeviceId();
    request.setHeader("accept", ACCEPT_HEADER);
    request.setHeader("Version", UserAgentFactory.AA_APP_VERSION);
    request.setHeader("Auth-Token",
        authenticator.getAuthToken(currentMillis, deviceId));
    request.setHeader("Device-ID", deviceId);
    request.setHeader("Timestamp", Long.toString(currentMillis));
  }

  public String retrieveUrl(String url) throws ClientProtocolException, IOException {
    HttpGet httpGet = new HttpGet(url);
    addStandardHeaders(httpGet);
    CloseableHttpResponse response = httpclient.execute(httpGet);
    try {
      return EntityUtils.toString(response.getEntity());
    } finally {
      response.close();
    }
  }
}

