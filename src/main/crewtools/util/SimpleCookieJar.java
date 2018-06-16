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

package crewtools.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class SimpleCookieJar implements CookieJar {
  private final HashMap<String, List<okhttp3.Cookie>> cookieStore = new HashMap<>();

  @Override
  public void saveFromResponse(HttpUrl url, List<okhttp3.Cookie> cookies) {
    cookieStore.put(url.host(), cookies);
  }

  @Override
  public List<okhttp3.Cookie> loadForRequest(HttpUrl url) {
    List<okhttp3.Cookie> cookies = cookieStore.get(url.host());
    return cookies != null ? cookies : new ArrayList<okhttp3.Cookie>();
  }

}
