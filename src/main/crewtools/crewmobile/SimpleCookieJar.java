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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Logger;

import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class SimpleCookieJar implements CookieJar {
  private final Logger logger = Logger.getLogger(CookieJar.class.getName());
  private final HashMap<String, List<okhttp3.Cookie>> cookieStore = new HashMap<>();

  @Override
  public void saveFromResponse(HttpUrl url, List<okhttp3.Cookie> cookies) {
    for (okhttp3.Cookie cookie : cookies) {
      if (cookie.domain() != null) {
        add(cookie.domain(), cookie);
      } else {
        add(url.host(), cookie);
      }
    }
  }

  @Override
  public List<okhttp3.Cookie> loadForRequest(HttpUrl url) {
    List<okhttp3.Cookie> cookies = new ArrayList<>();
    for (String key : cookieStore.keySet()) {
      if (url.host().contains(key)) {
        cookies.addAll(cookieStore.get(key));
      }
    }
    return cookies;
  }

  public void add(String key, okhttp3.Cookie cookie) {
    if (cookieStore.containsKey(key)) {
      ListIterator<okhttp3.Cookie> it = cookieStore.get(key).listIterator();
      while (it.hasNext()) {
        if (it.next().name().equals(cookie.name())) {
          it.remove();
        }
      }
    }
    if (cookie.value().isEmpty()) {
      return;
    }
    if (cookieStore.containsKey(key)) {
      cookieStore.get(key).add(cookie);
    } else {
      List<okhttp3.Cookie> list = new ArrayList<>();
      list.add(cookie);
      cookieStore.put(key, list);
    }
  }

  public void clear() {
    cookieStore.clear();
  }
}
