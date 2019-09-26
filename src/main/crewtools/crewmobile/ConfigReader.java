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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Files;
import com.google.protobuf.TextFormat;

import crewtools.crewmobile.Proto.CrewmobileConfig;

public class ConfigReader {
  private static final String CONFIG_PROPERTIES_PATH = "/.crewmobile";

  public ConfigReader() {
  }

  public static CrewmobileConfig readConfig() throws IOException {
    File file = new File(System.getProperty("user.home") + CONFIG_PROPERTIES_PATH);
    CrewmobileConfig.Builder builder = CrewmobileConfig.newBuilder();
    TextFormat.getParser().merge(
        Files.toString(file, StandardCharsets.UTF_8), builder);
    return builder.build();
  }
}
