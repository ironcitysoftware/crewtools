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

import org.junit.Test;

public class DeviceIdProviderTest {
  @Test
  public void testNullAndroidId() {
    final String macAddress = "123";
    UniqueIdProvider uniqueIdProvider = new UniqueIdProvider() {
      @Override public String getUniqueId() { return null; }
      @Override public String getMacAddress() { return macAddress; }
    };
    assertEquals(0xbe32, macAddress.hashCode());
    assertEquals("aa7db01d000000000000be320000000000000000",
        new DeviceIdFactory(uniqueIdProvider).getDeviceId());
  }

  @Test
  public void testEmptyAndroidId() {
    UniqueIdProvider uniqueIdProvider = new UniqueIdProvider() {
      @Override public String getUniqueId() { return ""; }
      @Override public String getMacAddress() { return "123"; }
    };
    assertEquals("aa7db01d000000000000be320000000000000000",
        new DeviceIdFactory(uniqueIdProvider).getDeviceId());
  }

  @Test
  public void testNullMacAddress() {
    UniqueIdProvider uniqueIdProvider = new UniqueIdProvider() {
      @Override public String getUniqueId() { return null; }
      @Override public String getMacAddress() { return null; }
    };
    assertEquals("aa7db01d00000000000000000000000000000000",
        new DeviceIdFactory(uniqueIdProvider).getDeviceId());
  }
}
