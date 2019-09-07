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
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

public class FlicaConfig {
  private static final String CONFIG_PROPERTIES_PATH = "/.crewtools";

  public static final String FLICA_USERNAME = "flicaUsername";
  public static final String FLICA_PASSWORD = "flicaPassword";
  public static final String DATA_DIRECTORY = "dataDirectory";
  public static final String INTERESTING_EMPLOYEE_ID = "interestingEmployeeId";
  public static final String SESSION_CACHE_FILE = "sessionCacheFile";
  public static final String DOMICILES = "domiciles";
  public static final String AIRLINE_ID = "airlineId";

  private final Properties props;

  public FlicaConfig(Properties props) {
    this.props = props;
  }

  public static FlicaConfig readConfig() throws IOException {
    Properties props = new Properties();
    File file = new File(System.getProperty("user.home") + CONFIG_PROPERTIES_PATH);
    props.load(new FileInputStream(file));
    return new FlicaConfig(props);
  }

  public String getFlicaUsername() {
    return Preconditions.checkNotNull(props.getProperty(FLICA_USERNAME));
  }

  public String getFlicaPassword() {
    return Preconditions.checkNotNull(props.getProperty(FLICA_PASSWORD));
  }

  public String getDataDirectory() {
    return Preconditions.checkNotNull(props.getProperty(DATA_DIRECTORY));
  }

  public String getInterestingEmployeeId() {
    return Preconditions.checkNotNull(props.getProperty(INTERESTING_EMPLOYEE_ID));
  }

  public String getSessionCacheFile() {
    return props.getProperty(SESSION_CACHE_FILE);
  }

  public Set<String> getDomiciles() {
    return ImmutableSet.copyOf(
        Splitter.on(',').omitEmptyStrings().trimResults().split(
            Preconditions.checkNotNull(props.getProperty(DOMICILES))));
  }

  public String getAirlineId() {
    return Preconditions.checkNotNull(props.getProperty(AIRLINE_ID));
  }
}
