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
import java.util.logging.Logger;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CrewmobileConnection {
  private final OkHttpClient httpclient;
  private final Logger logger = Logger.getLogger(CrewmobileConnection.class.getName());
  private static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  public CrewmobileConnection() {
    this.httpclient = new OkHttpClient().newBuilder()
        .addNetworkInterceptor(new MungeHeaders())
        .addNetworkInterceptor(new LoggingInterceptor())
        .followRedirects(false)
        .followSslRedirects(false)
        .build();
  }

  public Response retrieveUrl(HttpUrl url, String bearerToken) throws IOException {
    Request.Builder requestBuilder = new Request.Builder()
        .url(url)
        .header("Authorization", "Bearer " + bearerToken);
    return httpclient.newCall(requestBuilder.build()).execute();
  }

  public String retrieveUrlString(HttpUrl url, String bearerToken)
      throws IOException {
    Response response = retrieveUrl(url, bearerToken);
    try {
      return response.body().string();
    } finally {
      response.body().close();
    }
  }

  public Response postUrl(HttpUrl url, String content) throws IOException {
    Request request = new Request.Builder()
        .url(url)
        .post(RequestBody.create(APPLICATION_JSON, content))
        .build();
    logger.fine("Post data = " + content);
    return httpclient.newCall(request).execute();
  }

  public String postUrlString(HttpUrl url, String content) throws IOException {
    Response response = postUrl(url, content);
    try {
      return response.body().string();
    } finally {
      response.body().close();
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

  // Alters okhttp default headers.
  class MungeHeaders implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request()
          .newBuilder()
          .removeHeader("Accept-Encoding")
          .removeHeader("User-Agent")
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .build();
      return chain.proceed(request);
    }
  }
}

