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

package crewtools.aa;

public class UserAgentFactory {

  // AA Version Upgrade: AARequestInterceptor.versionName
  public static final String AA_APP_VERSION = "5.8.0.1";

  private static final String AA_APP_VERSION_NAME = "Android";

  // android.os.Build(.MODEL|.DEVICE)
  private static final String DEVICE_NAME = "Nexus 5X";

  // android.os.Build.VERSION.RELEASE (N_MR1)
  private static final String BUILD_VERSION_RELEASE = "7.1.2";

  private static final int DEFAULT_DISPLAY_WIDTH = 1080;

  private static final int DEFAULT_DISPLAY_HEIGHT = 1794;

  // AANetworkUtils.getMWSHeader constant
  private static final String MAGIC_VERSION = "1.0";

  public String getUserAgent() {
    return String.format("%s/%s %s|%s|%d|%d|%s",
        AA_APP_VERSION_NAME,
        AA_APP_VERSION,
        DEVICE_NAME,
        BUILD_VERSION_RELEASE,
        DEFAULT_DISPLAY_WIDTH,
        DEFAULT_DISPLAY_HEIGHT,
        MAGIC_VERSION);
  }
}
