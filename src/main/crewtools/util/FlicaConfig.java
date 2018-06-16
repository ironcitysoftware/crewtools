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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import com.google.common.base.Preconditions;

public class FlicaConfig {
  private static final String CONFIG_PROPERTIES_PATH = "/.crewtools";

  private final Properties props;

  public FlicaConfig() throws IOException {
    props = new Properties();
    props.load(new FileInputStream(
        new File(System.getProperty("user.home") + CONFIG_PROPERTIES_PATH)));
  }

  public String getFlicaUsername() {
    return Preconditions.checkNotNull(props.getProperty("flicaUsername"));
  }

  public String getFlicaPassword() {
    return Preconditions.checkNotNull(props.getProperty("flicaPassword"));
  }
  
  public String getDataDirectory() {
    return Preconditions.checkNotNull(props.getProperty("dataDirectory"));
  }

  public String getInterestingEmployeeId() {
    return Preconditions.checkNotNull(props.getProperty("interestingEmployeeId"));
  }

  public String getSessionCacheFile() {
    return props.getProperty("sessionCacheFile");
  }
}
