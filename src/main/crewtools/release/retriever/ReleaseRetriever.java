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

package crewtools.release.retriever;

import java.io.File;

import com.google.common.io.Files;

import crewtools.release.ReleaseService;

public class ReleaseRetriever {
  public static void main(String args[]) throws Exception {
    new ReleaseRetriever().run(args);
  }

  public void run(String args[]) throws Exception {
    if (args.length != 3) {
      System.err.println("releaseRetriever service-impl-class-name uuid output-file.pdf");
      System.exit(-1);
    }

    ReleaseService service = (ReleaseService) Class
        .forName(args[0])
        .newInstance();
    byte pdf[] = service.getRelease(args[1]);
    Files.write(pdf, new File(args[2]));
  }
}
