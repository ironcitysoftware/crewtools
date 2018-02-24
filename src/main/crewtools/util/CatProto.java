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

import com.google.protobuf.Message;

import crewtools.flica.Proto.PairingList;
import crewtools.flica.Proto.ThinLineList;

public class CatProto {
  public static void main(String args[]) throws Exception {
    if (args.length == 0) {
      System.err.println("CatProto proto.io");
      System.exit(-1);
    }
    File protoFile = new File(args[0]);
    Message.Builder builder = null;
    if (protoFile.getName().startsWith("lines-")) {
      builder = ThinLineList.newBuilder();
    } else if (true || protoFile.getName().startsWith("pairings-")) {
      builder = PairingList.newBuilder();
    } else {
      System.err.println("Need to add detection for this filename: " + protoFile.getName());
      System.exit(-1);
    }
    FileInputStream inputStream = new FileInputStream(protoFile);
    builder.mergeFrom(inputStream);
    System.out.println(builder.toString());
  }
}
