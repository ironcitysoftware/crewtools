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

import java.util.logging.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class AppConfig {
  private final Logger logger = Logger.getLogger(AppConfig.class.getName());

  public AppConfig(String json) {
    JsonParser parser = new JsonParser();
    JsonElement element = parser.parse(json);

    logger.info(json);
  }
}
