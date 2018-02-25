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

import org.junit.Test;

import crewtools.flica.parser.SwapResponseParser;
import crewtools.flica.parser.SwapResponseParser.Status;

public class SwapResponseParserTest {
  @Test
  public void testSuccess() {
    assertEquals(Status.SUCCESS, new SwapResponseParser("").parse());
  }

  @Test
  public void testDuplicate() {
    assertEquals(Status.DUPLICATE, new SwapResponseParser(
        "<html><head><script>alert('A duplicate request already exists.');</script></head></html>").parse());
  }

  @Test
  public void testUnrecognized() {
    assertEquals(Status.SUCCESS,
        new SwapResponseParser("<html><head><script>alert('what is this?');</script></head></html>").parse());
  }
}
