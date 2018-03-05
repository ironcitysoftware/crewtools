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

package crewtools.flica.parser;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import crewtools.flica.pojo.OpentimeRequest;

public class OpentimeRequestParserTest {
  @Test
  public void testSingleDay() throws ParseException {
    String page = " return -1;\n" + 
        "}\n" + 
        "QAry.push( new Req(\"01D385A4:CC126379\",\"04 JAN 16:41\",4,['L7101:20180120'],['L2062:20180119'],[''],[''],0,\"Approved\",\"\",\"\",1,false,'04 JAN 16:42',false,\"\",0,\"\",\"\",\"\",0,0,'',0) );\n" + 
        "QAry.push( new Req(\"01D389C1:71ADB35A\",\"09 JAN 22:16\",4,['L2062:20180119'],['L7369:20180120'],[''],[''],0,\"Approved\",\"\",\"\",2,false,'09 JAN 22:18',false,\"\",0,\"\",\"\",\"\",0,0,'',0) );\n" + 
        "window.onresize=function() {\n" + 
        "  try {";
    List<OpentimeRequest> requests = new OpentimeRequestParser(page).parse();
    assertEquals(2, requests.size());
    assertEquals("01D385A4:CC126379", requests.get(0).getRequestId());
    assertEquals("01D389C1:71ADB35A", requests.get(1).getRequestId());
  }
}
