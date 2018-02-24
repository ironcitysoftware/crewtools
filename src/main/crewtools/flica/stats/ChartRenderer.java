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

package crewtools.flica.stats;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import crewtools.util.FlicaConfig;

public class ChartRenderer {
  private final String templateFilename;
  private final String outputFilename;
  private final Map<String, GraphData> data;
  
  public ChartRenderer(Map<String, GraphData> data,
      String templateFilename,
      String outputFilename) throws IOException {
    this.data = data;
    this.templateFilename = new FlicaConfig().getDataDirectory() + templateFilename;
    this.outputFilename = outputFilename;
  }
  
  public void render() throws IOException {
    File templateFile = new File(templateFilename);
    String template = Files.toString(templateFile, StandardCharsets.UTF_8);
    for (String key : data.keySet()) {
      Preconditions.checkState(template.contains(key), "Missing " + key + " in " + templateFile);
      template = template.replace(key, data.get(key).getGraphData());
    }
    Files.write(template, new File(outputFilename), StandardCharsets.UTF_8);
  }
}
