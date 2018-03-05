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
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.common.io.Files;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.TextFormat;

public class FileUtils {
  private final static Logger logger = Logger.getLogger(FileUtils.class.getName());

  @SuppressWarnings("unchecked")
  public static <B extends Builder, M extends Message> M
  readProto(String protoTxtFile, B builder) throws IOException {
    FileReader reader = new FileReader(protoTxtFile);
    TextFormat.getParser().merge(reader, builder);
    return (M) builder.build();
  }
  
  public static void writeDebugFile(String prefix, String data) throws IOException {
    File out = new File("/opt/autobidder/debug/" 
        + prefix + "-" + System.currentTimeMillis() + ".txt");
    logger.info("Writing debug data to " + out.toString());
    if (data == null) {
      return;
    }
    Files.write(data, out, StandardCharsets.UTF_8);
  }
}
