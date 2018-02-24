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

/**
 * Builds a unique device ID.
 */
public class DeviceIdFactory {
  private final UniqueIdProvider uniqueIdProvider;

  public DeviceIdFactory(UniqueIdProvider uniqueIdProvider) {
    this.uniqueIdProvider = uniqueIdProvider;
  }

  private static final long INT_MASK = 0xffffffffL;
  private static final int MIN_DEVICE_ID_LENGTH = 40;

  private static final BigInteger MAGIC_CONSTANT =
      BigInteger.valueOf(0xaa7db01dL);

  public String getDeviceId() {
    BigInteger uniqueId =
        parseHexStringAsBigInteger(uniqueIdProvider.getUniqueId());

    long macAddressHashCode =
        safeHashCode(uniqueIdProvider.getMacAddress()) & INT_MASK;
    BigInteger macAddressInt = BigInteger.valueOf(macAddressHashCode);

    String resultStr = MAGIC_CONSTANT
        .shiftLeft(64)
        .or(macAddressInt)
        .shiftLeft(64)
        .or(uniqueId)
        .toString(16);

    while (resultStr.length() < MIN_DEVICE_ID_LENGTH) {
      resultStr = "0" + resultStr;
    }
    return resultStr;
  }

  private BigInteger parseHexStringAsBigInteger(String hexString) {
    if (hexString == null || hexString.isEmpty()) {
      return BigInteger.valueOf(0);
    } else {
      try {
        return new BigInteger(hexString, 16);
      } catch (NumberFormatException e) {
        return BigInteger.valueOf(hexString.hashCode() & INT_MASK);
      }
    }
  }

  private int safeHashCode(Object object) {
    return object == null ? 0 : object.hashCode();
  }
}
