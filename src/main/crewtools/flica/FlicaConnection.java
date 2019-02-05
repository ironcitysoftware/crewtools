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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Minutes;

import com.google.common.io.Files;
import com.google.protobuf.TextFormat;

import crewtools.util.Clock;
import crewtools.util.FlicaConfig;
import crewtools.util.SystemClock;
import okhttp3.Cookie;

public class FlicaConnection extends BaseFlicaConnection {
  private final Logger logger = Logger.getLogger(FlicaConnection.class.getName());
  private final Clock clock;
  private DateTime sessionCreationTime;
  private File sessionCacheFile;

  // TODO: determine correct value.
  private static final Minutes SESSION_DURATION = Minutes.minutes(30);

  public FlicaConnection(FlicaConfig config) throws IOException {
    super(config);
    this.clock = new SystemClock();
    String sessionCacheFilename = config.getSessionCacheFile();
    if (sessionCacheFilename != null) {
      this.sessionCacheFile = new File(sessionCacheFilename);
      if (sessionCacheFile.exists()) {
        setSession(Files.toString(sessionCacheFile, StandardCharsets.UTF_8));
      }
    }
  }

  private boolean isExpired(DateTime sessionCreationTime) {
    return sessionCreationTime == null
        || sessionCreationTime.plus(SESSION_DURATION).isBefore(clock.now());
  }

  private static final int MAGIC_NO_EXPIRATION_YEAR = 9999;

  /**
   * Returns the current session as a serializable string, or "" for no session.
   */
  public String getSession() {
    if (isExpired(sessionCreationTime)) {
      return "";
    }
    Proto.Session.Builder builder = Proto.Session.newBuilder();
    for (okhttp3.Cookie c : cookieJar.loadForRequest(FLICA_LOGIN_URL)) {
      Proto.Cookie.Builder cookieBuilder = builder.addCookieBuilder();
      cookieBuilder.setName(c.name()).setValue(c.value());
      if (new DateTime(c.expiresAt()).getYear() != MAGIC_NO_EXPIRATION_YEAR) {
        cookieBuilder.setExpiration(c.expiresAt());
      }
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
      logger.info("The saved session is equal to the current session.");
      return true;
    }

    logger.info("Attempting to restore saved session");
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
      okhttp3.Cookie.Builder cookieBuilder = new Cookie.Builder()
          .name(c.getName())
          .value(c.getValue())
          .domain("flica.net")
          .path("/")
          .secure()
          .httpOnly();
      if (c.hasExpiration()) {
        cookieBuilder.expiresAt(c.getExpiration());
      }
      cookies.add(cookieBuilder.build());
    }
    cookieJar.saveFromResponse(FLICA_LOGIN_URL, cookies);
    sessionCreationTime = new DateTime(builder.getCreationTimeMillis());
    logger.info("Restored from " +
        sessionCreationTime.withZone(DateTimeZone.forID("America/New_York")));
    return true;
  }

  @Override
  public boolean connect() throws IOException {
    if (!isExpired(sessionCreationTime)) {
      logger.info("Ignoring connect() due to active session");
      // TODO: this unfortunately overloads the response bit.
      return true;
    }
    if (super.connect()) {
      sessionCreationTime = clock.now();
      if (sessionCacheFile != null) {
        logger.info("Writing session to " + sessionCacheFile);
        Files.write(getSession(), sessionCacheFile, StandardCharsets.UTF_8);
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void disconnect() throws IOException {
    super.disconnect();
    sessionCreationTime = null;
  }
}
