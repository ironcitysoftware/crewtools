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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** This class is thread-safe and Android-friendly. */
public class Authenticator {
  private static final String SHA1 = "SHA-1";
  private static final String MD5 = "MD5";

  // AARequestInterceptor.addAuthData
  private static final String SALT_BASE = "d70X3cj^5_~J9_@$7_0XI%_";

  public String getAuthToken(long currentMillis, String deviceId) {
    deviceId = deviceId.toUpperCase();

    String salt = SALT_BASE
        .replace("0X", "").replace("_", "")
        .substring(5);

    String saltedDeviceId;
    // Either the author is attempting obfuscation, or this is a bug.
    long alwaysZero = currentMillis / 2 % 2;
    if (alwaysZero == 0) {
      saltedDeviceId = salt + deviceId;
    } else {
      // Never happens.
      saltedDeviceId = deviceId + salt;
    }

    boolean isMillisOdd = currentMillis % 2 == 0;
    try {
      if (isMillisOdd) {
        return getSha1(saltedDeviceId);
      } else {
        return getMd5(saltedDeviceId);
      }
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  String getSha1(String message) throws NoSuchAlgorithmException {
    MessageDigest sha1Digester = MessageDigest.getInstance(SHA1);
    sha1Digester.update(message.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(sha1Digester.digest());
  }

  String getMd5(String message) throws NoSuchAlgorithmException {
    MessageDigest md5Digester = MessageDigest.getInstance(MD5);
    md5Digester.update(message.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(md5Digester.digest());
  }

  private final static char[] hexArray = "0123456789abcdef".toCharArray();

  static String bytesToHex(byte[] bytes) {
      char[] hexChars = new char[bytes.length * 2];
      for ( int j = 0; j < bytes.length; j++ ) {
          int v = bytes[j] & 0xFF;
          hexChars[j * 2] = hexArray[v >>> 4];
          hexChars[j * 2 + 1] = hexArray[v & 0x0F];
      }
      return new String(hexChars);
  }
}