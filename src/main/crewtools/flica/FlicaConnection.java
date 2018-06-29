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

package crewtools.flica;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.Minutes;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.protobuf.TextFormat;

import crewtools.util.Clock;
import crewtools.util.FlicaConfig;
import crewtools.util.SimpleCookieJar;
import crewtools.util.SystemClock;
import okhttp3.Cookie;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class FlicaConnection {
  private final String username;
  private final String password;
  private final OkHttpClient httpclient;
  private final SimpleCookieJar cookieJar = new SimpleCookieJar();
  private final Logger logger = Logger.getLogger(FlicaConnection.class.getName());
  private final Clock clock;
  private DateTime sessionCreationTime;
  private File sessionCacheFile;

  // TODO: determine correct value.
  private static final Minutes SESSION_DURATION = Minutes.minutes(30);

  private static final String USER_AGENT_KEY = "User-Agent";
  private static final String CHROME_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.76 Safari/537.36";
  private static final HttpUrl FLICA_LOGIN_URL = HttpUrl
      .parse("https://jia.flica.net/public/flicaLogon.cgi");
  private static final HttpUrl FLICA_LOGOUT_URL = HttpUrl
      .parse("https://jia.flica.net/logoff");

  public FlicaConnection(FlicaConfig config) throws IOException {
    this(config.getFlicaUsername(), config.getFlicaPassword());
    String sessionCacheFilename = config.getSessionCacheFile();
    if (sessionCacheFilename != null) {
      this.sessionCacheFile = new File(sessionCacheFilename);
      if (sessionCacheFile.exists()) {
        setSession(Files.toString(sessionCacheFile, StandardCharsets.UTF_8));
      }
    }
  }

  public FlicaConnection(String username, String password) {
    this.username = username;
    this.password = password;
    this.httpclient = new OkHttpClient().newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(cookieJar)
        .build();
    this.clock = new SystemClock();
  }

  private boolean isExpired(DateTime sessionCreationTime) {
    return sessionCreationTime == null
        || sessionCreationTime.plus(SESSION_DURATION).isBefore(clock.now());
  }

  public String getSession() {
    if (isExpired(sessionCreationTime)) {
      return "";
    }
    Proto.Session.Builder builder = Proto.Session.newBuilder();
    for (okhttp3.Cookie c : cookieJar.loadForRequest(FLICA_LOGIN_URL)) {
      builder.addCookieBuilder().setName(c.name()).setValue(c.value());
    }
    builder.setCreationTimeMillis(sessionCreationTime.getMillis());
    StringWriter writer = new StringWriter();
    try {
      TextFormat.print(builder.build(), writer);
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
    return writer.toString();
  }

  /** Returns true if the session was successfully set. */
  public boolean setSession(String session) {
    String existingSession = getSession();
    if (existingSession.equals(session)) {
      return true;
    }

    Proto.Session.Builder builder = Proto.Session.newBuilder();
    try {
      TextFormat.getParser().merge(session, builder);
    } catch (IOException unlikely) {
      throw new IllegalStateException(unlikely);
    }
    if (builder.hasCreationTimeMillis()) {
      DateTime sessionCreationTime = new DateTime(builder.getCreationTimeMillis());
      if (isExpired(sessionCreationTime)) {
        logger.info("Cowardly refusing to set an expired session.");
        return false;
      }
    }

    List<okhttp3.Cookie> cookies = new ArrayList<>();
    for (Proto.Cookie c : builder.getCookieList()) {
      cookies.add(new Cookie.Builder()
          .name(c.getName())
          .value(c.getValue())
          .domain(FLICA_LOGIN_URL.host())
          .build());
    }
    cookieJar.saveFromResponse(FLICA_LOGIN_URL, cookies);
    sessionCreationTime = new DateTime(builder.getCreationTimeMillis());
    return true;
  }

  public boolean connect() throws IOException {
    if (!isExpired(sessionCreationTime)) {
      logger.info("Ignoring connect() due to active session");
      // TODO: this unfortunately overloads the response bit.
      return true;
    }
    RequestBody form = new FormBody.Builder()
        .add("UserId", username)
        .add("Password", password)
        .add("Cookies", "1")
        .add("PCookies", "1")
        .build();
    Request request = new Request.Builder()
        .url(FLICA_LOGIN_URL)
        .header(USER_AGENT_KEY, CHROME_USER_AGENT)
        .post(form)
        .build();
    Response response = httpclient.newCall(request).execute();
    if (response.code() != HttpURLConnection.HTTP_OK) {
      return false;
    }
    sessionCreationTime = clock.now();
    if (sessionCacheFile != null) {
      logger.info("Writing session to " + sessionCacheFile);
      Files.write(getSession(), sessionCacheFile, StandardCharsets.UTF_8);
    }
    return true;
  }

  public void disconnect() throws IOException {
    retrieveUrl(FLICA_LOGOUT_URL);
  }
  
  private ResponseBody retrieveUrlInternal(HttpUrl url) throws IOException {
    logger.info("url = [" + url.toString() + "]");
    Request request = new Request.Builder()
        .url(url)
        .header(USER_AGENT_KEY, CHROME_USER_AGENT)
        .build();
    Response response = httpclient.newCall(request).execute();
    logger.info("First Request Status: " + response.message());
    if (response.code() == HttpURLConnection.HTTP_MOVED_TEMP) {
      logger.info("(Re)Logging in");
      Preconditions.checkState(connect(), "connect failed");
      response = httpclient.newCall(request).execute();
      logger.info("Second Request Status: " + response.message());
    }
    return response.body();
  }

  public String retrieveUrl(HttpUrl url) throws IOException {
    return retrieveUrlInternal(url).string();
  }

  public byte[] retrieveUrlBytes(HttpUrl url) throws IOException {
    return retrieveUrlInternal(url).bytes();
  }

  public String postUrl(HttpUrl url, Multimap<String, String> data) throws IOException {
    FormBody.Builder form = new FormBody.Builder();
    for (String key : data.keySet()) {
      for (String value : data.get(key)) {
        form.add(key, value);
      }
    }
    // TODO auto-login on 302
    Request request = new Request.Builder()
        .url(FLICA_LOGIN_URL)
        .header(USER_AGENT_KEY, CHROME_USER_AGENT)
        .post(form.build())
        .build();
    Response response = httpclient.newCall(request).execute();
    return response.body().string();
  }

  public String postUrlWithReferer(HttpUrl url, String referer,
      Multimap<String, String> data) throws IOException {
    FormBody.Builder form = new FormBody.Builder();
    for (String key : data.keySet()) {
      for (String value : data.get(key)) {
        form.add(key, value);
      }
    }
    // TODO auto-login on 302
    Request request = new Request.Builder()
        .url(FLICA_LOGIN_URL)
        .header(USER_AGENT_KEY, CHROME_USER_AGENT)
        .header("Referer", referer)
        .post(form.build())
        .build();
    Response response = httpclient.newCall(request).execute();
    return response.body().string();
  }
}
