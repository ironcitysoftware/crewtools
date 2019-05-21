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

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Multimap;

import crewtools.util.FlicaConfig;
import crewtools.util.SimpleCookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BaseFlicaConnection implements Closeable {
  private final String username;
  private final String password;
  private final OkHttpClient httpclient;
  protected final SimpleCookieJar cookieJar = new SimpleCookieJar();
  private final Logger logger = Logger.getLogger(BaseFlicaConnection.class.getName());

  private static final String HOST = "jia.flica.net";

  private static final String USER_AGENT_KEY = "User-Agent";
  private static final String CHROME_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.76 Safari/537.36";

  protected static final HttpUrl FLICA_LOGIN_URL = new HttpUrl.Builder()
      .scheme("https")
      .host(HOST)
      .addPathSegments("public/flicaLogon.cgi")
      .build();
  private static final HttpUrl FLICA_LOGOUT_URL = new HttpUrl.Builder()
      .scheme("https")
      .host(HOST)
      .addPathSegments("logoff")
      .build();

  public BaseFlicaConnection(FlicaConfig config) throws IOException {
    this(config.getFlicaUsername(), config.getFlicaPassword());
  }

  // TODO make private
  public BaseFlicaConnection(String username, String password) {
    this.username = username;
    this.password = password;
    this.httpclient = new OkHttpClient().newBuilder()
        //.addNetworkInterceptor(new LoggingInterceptor())
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(cookieJar)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
  }

  public boolean connect() throws IOException {
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
    try {
      return response.code() == HttpURLConnection.HTTP_OK;
    } finally {
      response.close();
    }
  }

  public void disconnect() throws IOException {
    retrieveUrl(FLICA_LOGOUT_URL);
  }

  @Override
  public void close() throws IOException {
    disconnect();
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
      response.body().close();
      logger.info("(Re)Logging in");
      Preconditions.checkState(connect(), "connect failed");
      response = httpclient.newCall(request).execute();
      Preconditions.checkState(response.code() != HttpURLConnection.HTTP_MOVED_TEMP,
          response.toString());
    }
    return response.body();
  }

  public String retrieveUrl(HttpUrl url) throws IOException {
    ResponseBody body = retrieveUrlInternal(url);
    try {
      return body.string();
    } finally {
      body.close();
    }
  }

  public byte[] retrieveUrlBytes(HttpUrl url) throws IOException {
    ResponseBody body = retrieveUrlInternal(url);
    try {
      return body.bytes();
    } finally {
      body.close();
    }
  }

  public Response postUrl(HttpUrl url, Multimap<String, String> data) throws IOException {
    FormBody.Builder form = new FormBody.Builder();
    for (String key : data.keySet()) {
      for (String value : data.get(key)) {
        form.add(key, value);
      }
    }
    // TODO auto-login on 302
    Request request = new Request.Builder()
        .url(url)
        .header(USER_AGENT_KEY, CHROME_USER_AGENT)
        .post(form.build())
        .build();
    return httpclient.newCall(request).execute();
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
        .url(url)
        .header(USER_AGENT_KEY, CHROME_USER_AGENT)
        .header("Referer", referer)
        .post(form.build())
        .build();

    Response response = httpclient.newCall(request).execute();
    ResponseBody body = response.body();
    try {
      return body.string();
    } finally {
      body.close();
    }
  }

  private int i = 1;

  class LoggingInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      logger.info(String.format("-[%02d]-------------------------------------", i++));
      logger.info(String.format("Request %s %s%n%s",
          request.method(), request.url(), request.headers()));
      Response response = chain.proceed(request);
      logger.info(String.format("Response for %s %s%n%d %s%n%s",
          response.request().method(), response.request().url(),
          response.code(), response.message(), response.headers()));
      return response;
    }
  }
}
