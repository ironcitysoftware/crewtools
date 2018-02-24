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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;

import crewtools.util.FlicaConfig;

public class FlicaConnection {
  private final String username;
  private final String password;
  private final CloseableHttpClient httpclient;
  private final CookieStore cookieStore = new BasicCookieStore();
  private final Logger logger = Logger.getLogger(FlicaConnection.class.getName());

  private static final String CHROME_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.76 Safari/537.36";
  private static final String FLICA_LOGIN_URL = "https://jia.flica.net/public/flicaLogon.cgi";
  private static final String FLICA_LOGOUT_URL = "https://jia.flica.net/logoff";

  public FlicaConnection(FlicaConfig config) {
    this(config.getFlicaUsername(), config.getFlicaPassword());
  }

  public FlicaConnection(String username, String password) {
    this.username = username;
    this.password = password;
    this.httpclient = HttpClients.custom()
        .setDefaultCookieStore(cookieStore)
        .setUserAgent(CHROME_USER_AGENT)
        .build();
  }

  public void connect() throws ClientProtocolException, IOException {
    HttpPost httpPost = new HttpPost(FLICA_LOGIN_URL);
    List<NameValuePair> nvps = new ArrayList<>();
    nvps.add(new BasicNameValuePair("UserId", username));
    nvps.add(new BasicNameValuePair("Password", password));
    nvps.add(new BasicNameValuePair("Cookies", "1"));
    nvps.add(new BasicNameValuePair("PCookies", "1"));
    httpPost.setEntity(new UrlEncodedFormEntity(nvps));
    CloseableHttpResponse response = httpclient.execute(httpPost);
    try {
      Preconditions.checkState(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK,
          response);
      HttpEntity entity = response.getEntity();
      EntityUtils.consume(entity);
    } finally {
      response.close();
    }
  }

  public void disconnect() throws ClientProtocolException, IOException {
    retrieveUrl(FLICA_LOGOUT_URL);    
  }
  
  public String retrieveUrl(String url) throws ClientProtocolException, IOException {
    HttpGet httpGet = new HttpGet(url);
    CloseableHttpResponse response = httpclient.execute(httpGet);
    try {
      return EntityUtils.toString(response.getEntity());
    } finally {
      response.close();
    }
  }
  
  public byte[] retrieveUrlBytes(String url) throws ClientProtocolException, IOException {
    HttpGet httpGet = new HttpGet(url);
    CloseableHttpResponse response = httpclient.execute(httpGet);
    try {
      return EntityUtils.toByteArray(response.getEntity());
    } finally {
      response.close();
    }
  }

  public String postUrl(String url, Multimap<String, String> data) throws ClientProtocolException, IOException {
    HttpPost httpPost = new HttpPost(url);
    List<NameValuePair> nvps = new ArrayList<>();
    for (String key : data.keySet()) {
      for (String value : data.get(key)) {
        nvps.add(new BasicNameValuePair(key, value));
      }
    }
    httpPost.setEntity(new UrlEncodedFormEntity(nvps));
    CloseableHttpResponse response = httpclient.execute(httpPost);
    try {
      return EntityUtils.toString(response.getEntity());
    } finally {
      response.close();
    }
  }

  public String postUrlWithReferer(String url, String referer, Multimap<String, String> data) throws ClientProtocolException, IOException {
    HttpPost httpPost = new HttpPost(url);
    List<NameValuePair> nvps = new ArrayList<>();
    for (String key : data.keySet()) {
      for (String value : data.get(key)) {
        nvps.add(new BasicNameValuePair(key, value));
      }
    }
    httpPost.setEntity(new UrlEncodedFormEntity(nvps));
    httpPost.setHeader("Referer", referer);
    CloseableHttpResponse response = httpclient.execute(httpPost);
    try {
      return EntityUtils.toString(response.getEntity());
    } finally {
      response.close();
    }
  }
}
