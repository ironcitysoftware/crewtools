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
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

public class PdfTool {

  public static void main(String args[]) throws Exception {
    new PdfTool().run(args[0]);
  }

  public void run(String filename) throws Exception {
    File file = new File(filename);
    PDDocument pdf = PDDocument.load(file);
    PDDocumentOutline outline = pdf.getDocumentCatalog().getDocumentOutline();
    process(outline, "");
  }

  private void process(PDOutlineNode node, String label) throws IOException {
    PDOutlineItem current = node.getFirstChild();
    int childIndex = 0;
    while (current != null) {
      PDDestination dest = current.getDestination();
      if (dest instanceof PDNamedDestination) {
        System.out.printf("%02d %s of %s = %s\n",
            childIndex++,
            current.getTitle(),
            label,
            ((PDNamedDestination) dest).getNamedDestination());
      }
      process(current, current.getTitle());
      current = current.getNextSibling();
    }
  }

}