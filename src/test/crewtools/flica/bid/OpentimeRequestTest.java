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

package crewtools.flica.bid;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.OpentimeRequest;

public class OpentimeRequestTest {
  @Test
  public void testParser() throws ParseException {
    String data[] = {
      "01D3A782:5162B3A2",
      "16 FEB 19:00",
      "4",
      "['L2003:20180311','L2071:20180321','L2095:20180328']",
      "['L2042:20180321']",
      "['', '', '']",
      "['']",
      "0",
      "Approved",
      "",
      "",
      "1",
      "false",
      "16 FEB 19:18",
      "false",
      "",
      "0",
      "",
      "",
      "",
      "0",
      "0",
      "",
      "0" };
    OpentimeRequest request = new OpentimeRequest(Arrays.asList(data).iterator());
    assertEquals("Approved", request.getStatus());
  }
}
