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

import java.math.BigInteger;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UniqueIdProvider {
  private final Logger LOGGER = Logger.getLogger(UniqueIdProvider.class.getName());

  // https://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-hardware-id
  private static final String DEFAULT_ANDROID_6_0_MAC_ADDRESS =
      "02:00:00:00:00:00";

  public static final int UNIQUE_ID_LENGTH = 8;

  /**
   * Returns a 64-bit number (as a hex string) that should remain constant for
   * the lifetime of this machine.  (eg Android Settings.Secure.ANDROID_ID.)
   * Possibly returns empty string or null.
   */
  public String getUniqueId() {
    try {
      Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      while (nics.hasMoreElements()) {
        NetworkInterface nic = nics.nextElement();
        byte mac[] = nic.getHardwareAddress();
        if (mac == null) {
          return null;
        }
        String fullAddress = new BigInteger(mac).toString(16);
        if (fullAddress.length() > UNIQUE_ID_LENGTH) {
          return fullAddress.substring(fullAddress.length() - UNIQUE_ID_LENGTH);
        }
        while (fullAddress.length() < UNIQUE_ID_LENGTH) {
          fullAddress = "0" + fullAddress;
        }
        return fullAddress;
      }
      return null;
    } catch (SocketException e) {
      LOGGER.log(Level.INFO, "Unable to determine MAC address", e);
      return null;
    }
  }

  /**
   * Returns the MAC address of this machine.
   * Possibly returns empty string or null.
   */
  public String getMacAddress() {
    return DEFAULT_ANDROID_6_0_MAC_ADDRESS;
  }
}
