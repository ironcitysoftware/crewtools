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

import static org.junit.Assert.assertEquals;

import java.security.NoSuchAlgorithmException;

import org.junit.Test;

public class AuthenticatorTest {
  @Test
  public void testGetAuthToken() throws NoSuchAlgorithmException {
    String mixedCaseDeviceId = "aa7DB01D" + "00000000" + "75B319F8" + "40115829"
        + "DD2ABAA4";
    long currentMillis = 1496260026157L;
    assertEquals("2797724bd102d5798f0e77928ae6cc9c",
        new Authenticator().getAuthToken(currentMillis, mixedCaseDeviceId));
  }

  @Test
  public void testSha1() throws NoSuchAlgorithmException {
    assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d",
        new Authenticator().getSha1("hello"));
  }

  @Test
  public void testMd5() throws NoSuchAlgorithmException {
    assertEquals("5d41402abc4b2a76b9719d911017c592",
        new Authenticator().getMd5("hello"));
  }
}
