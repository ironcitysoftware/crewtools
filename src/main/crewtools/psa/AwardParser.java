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

package crewtools.psa;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.xml.sax.SAXException;

import com.google.common.io.Files;

/** Parses monthly bid awards. */
public class AwardParser {
  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err.println("awardParser award.pdf");
      System.exit(-1);
    }
    new AwardParser().run(new File(args[0]));
  }

  class AwardItem {
    AwardItem(int employeeId, String fromBase, String toBase) {
      this.employeeId = employeeId;
      this.fromBase = fromBase;
      this.toBase = toBase;
    }
    int employeeId;
    String fromBase;
    String toBase;
  }

  public void run(File pdfFile) throws Exception {
    byte pdf[] = Files.toByteArray(pdfFile);
    String raw = parsePdf(pdf);

    LocalDate awardDate = null;
    LocalDate effectiveDate = null;
    List<AwardItem> awards = new ArrayList<>();

    for (String line : raw.split("\\R")) {
      Matcher awardDateMatcher = AWARD_DATE.matcher(line);
      if (awardDate == null && awardDateMatcher.matches()) {
        awardDate = LocalDate.parse(awardDateMatcher.group(1));
        continue;
      }
      Matcher effectiveDateMatcher = EFFECTIVE_DATE.matcher(line);
      if (effectiveDate == null && effectiveDateMatcher.matches()) {
        effectiveDate = longDate.parseLocalDate(effectiveDateMatcher.group(1));
        continue;
      }
      Matcher awardMatcher = AWARD.matcher(line);
      if (awardMatcher.matches() && awardMatcher.group(9).equals("CA")) {
        int employeeId = Integer.parseInt(awardMatcher.group(3));
        String fromBase = null;
        if (awardMatcher.group(6).equals("CA")) {
          fromBase = awardMatcher.group(5);
        } else {
          // for upgrades, we don't list a from base.
        }
        awards.add(new AwardItem(employeeId, fromBase, awardMatcher.group(8)));
      }
    }

    // output is traditionally ordered CA->CA moves by destination base,
    // followed by upgrades by destination base.
    if (!awards.isEmpty()) {
      Collections.sort(awards, new Comparator<AwardItem>() {
        @Override
        public int compare(AwardItem left, AwardItem right) {
          if (left.fromBase == null ^ right.fromBase == null) {
            // upgrades go at the end.
            return left.fromBase == null ? 1 : -1;
          }
          // we are comparing either two upgrades or two moves.
          if (left.fromBase == null && right.fromBase == null) {
            // two upgrades: sort on to base.
            return left.toBase.compareTo(right.toBase);
          } else {
            // two moves: sort on from base.
            return left.fromBase.compareTo(right.fromBase);
          }
        }
      });
      output(awardDate, effectiveDate, awards);
    }
  }

  void output(LocalDate awardDate, LocalDate effectiveDate, List<AwardItem> awards) {
    System.out.println("periodic_award {");
    System.out.printf("  award_date: \"%s\"\n", awardDate);
    System.out.printf("  effective_date: \"%s\"\n", effectiveDate);
    for (AwardItem award : awards) {
      if (award.fromBase == null) {
        System.out.printf("  base_move { employee_id: %d to: %s }\n",
            award.employeeId, award.toBase);
      } else {
        System.out.printf("  base_move { employee_id: %d from: %s to: %s }\n",
            award.employeeId, award.fromBase, award.toBase);
      }
    }
    System.out.println("}");
  }

  //@formatter:off
  private final Pattern AWARD = Pattern.compile(
        "(\\d+) " // seniority number
      + "([A-Za-z ,]+)" // name
      + "(\\d+) " // employee number
      + "((CLT|TYS|ORF|DCA|PHL|DAY|CVG)-CRJ-(FO|CA)) " // from
      + "((CLT|TYS|ORF|DCA|PHL|DAY|CVG)-CRJ-(FO|CA))"); // to

  // @formatter:off
  /**
   * group0: 001 Doe, John 00001 FRM-CRJ-CA DST-CRJ-CA
   * group1: 001
   * group2: Doe, John
   * group3: 00001
   * group4: FRM-CRJ-CA
   * group5: FRM
   * group6: CA
   * group7: DST-CRJ-CA
   * group8: DST
   * group9: CA
   */

  // @formatter:on

  private final Pattern AWARD_DATE = Pattern.compile(
      "(\\d{4}-\\d{2}-\\d{2}) CA Award");

  private final Pattern EFFECTIVE_DATE = Pattern.compile(
      "Effective (([A-Za-z]+) (\\d+), (\\d+))");

  private DateTimeFormatter longDate = DateTimeFormat.forPattern("MMMM dd, yyyy");

  private String parsePdf(byte[] pdf) throws IOException, SAXException, TikaException {
    ByteArrayInputStream bais = new ByteArrayInputStream(pdf);
    BodyContentHandler handler = new BodyContentHandler(-1);
    Metadata metadata = new Metadata();
    PDFParser parser = new PDFParser();
    ParseContext parseContext = new ParseContext();
    parser.parse(bais, handler, metadata, parseContext);
    return handler.toString();
  }
}
