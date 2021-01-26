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

import com.google.common.base.Splitter;
import com.google.common.io.Files;
import crewtools.flica.Proto;
import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.CrewPosition;
import crewtools.flica.Proto.Domicile;
import crewtools.flica.Proto.SeniorityList;
import crewtools.flica.Proto.Status;
import crewtools.flica.stats.DataReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PeriodicAwardParser {
  private final Logger logger = Logger.getLogger(PeriodicAwardParser.class.getName());

  private final LocalDate awardDate;
  private final LocalDate effectiveDate;
  private final byte[] awardPdf;
  private final Splitter LINE_SPLITTER = Splitter.on("\n");

  private static final String LIST_HEADER =
      "Sen. Name Emp # Original  Awards";

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      System.err.println("PeriodicAwardParser award-date effective-date award.pdf");
      System.exit(1);
    }
    PeriodicAwardParser parser = new PeriodicAwardParser(
        LocalDate.parse(args[0]),
        LocalDate.parse(args[1]),
        Files.toByteArray(new File(args[2])));
    Proto.PeriodicAward award = parser.parse();
    System.out.println(award.toString());
  }

  public PeriodicAwardParser(LocalDate awardDate, LocalDate effectiveDate, byte[] awardPdf) {
    this.awardDate = awardDate;
    this.effectiveDate = effectiveDate;
    this.awardPdf = awardPdf;
  }

  public Proto.PeriodicAward parse() throws Exception {
    String awardText = parsePdf();
    Iterable<String> lines = LINE_SPLITTER.split(awardText);

    Proto.PeriodicAward.Builder builder = Proto.PeriodicAward.newBuilder();
    builder.setAwardDate(awardDate.toString());
    builder.setEffectiveDate(effectiveDate.toString());
    Map<String, Proto.BaseMove.Builder> baseMoveBuilders = new HashMap<>();
    for (String line : lines) {
      parse(line, builder, baseMoveBuilders);
    }
    return builder.build();
  }

  // 273 Harris, Ryan 25060 PHL-CRJ-CA CLT-CRJ-CA
  private Pattern BASE_MOVE_PATTERN = Pattern.compile("(\\d+) ([^0-9]+?) (\\d+)"
      + " (CLT|PHL|DAY|DCA|NH1)-CRJ-(CA|FO)"
      + " (CLT|PHL|DAY|DCA|NH1)-CRJ-(CA|FO)");

  private void parse(
      String line,
      Proto.PeriodicAward.Builder builder,
      Map<String, Proto.BaseMove.Builder> baseMoveBuilders) throws ParseException {
    if (line.isEmpty()) {
      return;
    }
    Matcher matcher = BASE_MOVE_PATTERN.matcher(line);
    if (!matcher.matches()) {
      System.err.println("SKIP: " + line);
    } else {
      addMove(line, matcher, builder, baseMoveBuilders);
    }
  }

  private void addMove(
      String line,
      Matcher matcher,
      Proto.PeriodicAward.Builder awardBuilder,
      Map<String, Proto.BaseMove.Builder> baseMoveBuilders)
      throws ParseException {
    int unusedSeniorityNumber = Integer.parseInt(matcher.group(1));
    String unusedCrewmemberName = matcher.group(2);
    int employeeId = Integer.parseInt(matcher.group(3));
    String from = matcher.group(4);
    String fromRank = matcher.group(5);
    String to = matcher.group(6);
    String toRank = matcher.group(7);

    String key = from + ":" + fromRank + ": " + to + ":" + toRank;
    Proto.BaseMove.Builder builder;
    if (baseMoveBuilders.containsKey(key)) {
      builder = baseMoveBuilders.get(key);
    } else {
      builder = awardBuilder.addBaseMoveBuilder();
      baseMoveBuilders.put(key, builder);
      builder.setFrom(Domicile.valueOf(from));
      builder.setFromRank(valueOfRank(fromRank));
      builder.setTo(Domicile.valueOf(to));
      builder.setToRank(valueOfRank(toRank));
    }
    builder.addEmployeeId(employeeId);

    logger.fine(String.format("%s -> %d '%s' %d %s-%s : %s-%s\n",
        line, unusedSeniorityNumber, unusedCrewmemberName, Integer.parseInt(matcher.group(3)),
        matcher.group(4), fromRank, matcher.group(6), toRank));
  }

  private Proto.Rank valueOfRank(String rank) {
    switch (rank) {
      case "CA": return Proto.Rank.CAPTAIN;
      case "FO": return Proto.Rank.FIRST_OFFICER;
      default: throw new IllegalStateException("Unknown rank " + rank);
    }
  }

  private String parsePdf() throws IOException, SAXException, TikaException {
    ByteArrayInputStream bais = new ByteArrayInputStream(awardPdf);
    BodyContentHandler handler = new BodyContentHandler(-1);
    Metadata metadata = new Metadata();
    PDFParser parser = new PDFParser();
    ParseContext parseContext = new ParseContext();
    parser.parse(bais, handler, metadata, parseContext);
    return handler.toString();
  }
}
