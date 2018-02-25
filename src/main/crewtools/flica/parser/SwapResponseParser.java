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

import java.util.logging.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class SwapResponseParser {
  private final Logger logger = Logger.getLogger(SwapResponseParser.class.getName());

  private static final String DUPLICATE_MAGIC = "A duplicate request already exists.";

  private final String html;

  public SwapResponseParser(String html) {
    this.html = html;
  }

  public enum Status {
    SUCCESS, DUPLICATE
  }

  public Status parse() {
    if (html == null) {
      // TODO fix this in a mock.
      logger.warning("Assuming test environment");
      return Status.SUCCESS;
    }
    Document document = Jsoup.parse(html);
    Elements elements = document.select("html > head > script");
    if (elements.isEmpty()) {
      return Status.SUCCESS;
    }
    String script = elements.html();
    if (script.contains(DUPLICATE_MAGIC)) {
      return Status.DUPLICATE;
    }
    logger.warning("Unparseable script tag in swap response: " + script);
    return Status.SUCCESS;
  }
}
