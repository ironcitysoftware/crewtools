/**
 * Copyright 2019 Iron City Software LLC
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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;

public class PdfReader {
  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err.println("PdfReader file.pdf");
      System.exit(-1);
    }
    FileInputStream fis = new FileInputStream(new File(args[0]));
    BodyContentHandler handler = new BodyContentHandler(-1);
    Metadata metadata = new Metadata();
    PDFParser parser = new PDFParser();
    ParseContext parseContext = new ParseContext();
    parser.parse(fis, handler, metadata, parseContext);
    System.out.println(handler.toString());
  }
}
