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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
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

import com.google.common.base.Splitter;

import crewtools.flica.Proto.CrewMember;
import crewtools.flica.Proto.CrewPosition;
import crewtools.flica.Proto.Domicile;
import crewtools.flica.Proto.SeniorityList;
import crewtools.flica.Proto.Status;

public class SeniorityParser {
  private final Logger logger = Logger.getLogger(SeniorityParser.class.getName());

  private final Set<String> domiciles;
  private final byte systemSeniorityPdf[];
  private final Splitter LINE_SPLITTER = Splitter.on("\n");

  // September 2017 and later.
  private static final String LIST_HEADER =
      "SEN ID EMP ID FIRST LAST HIRE DATE CITY TITLE STATUS";

  // August 2017 and prior.
  private static final String LEGACY_LIST_HEADER =
      "SEN EMP FIRST LAST HIRE DATE CITY TITLE STATUS";

  private static final Map<String, Status> STATUS_MAP =
      ParseUtils.getEnumValueMap(Status.class);

  public SeniorityParser(byte systemSeniorityPdf[], Set<String> domiciles) {
    this.systemSeniorityPdf = systemSeniorityPdf;
    this.domiciles = domiciles;
  }

  public SeniorityList parse() throws Exception {
    String systemSeniorityText = parsePdf();
    Iterable<String> lines = LINE_SPLITTER.split(systemSeniorityText);

    SeniorityList.Builder builder = SeniorityList.newBuilder();
    for (String line : lines) {
      parse(line, builder);
    }
    return builder.build();
  }

  enum ParseState {
    START,
    TITLE,
    PAGE_HEADER,
    LIST_HEADER,
    CREW_MEMBER,
    PAGE_FOOTER,
    FINISHED
  }

  private ParseState state = ParseState.START;

  private boolean firstPass = false;

  private Pattern PAGE_HEADER_PATTERN = Pattern.compile("SYS ?SEN (\\d{4})-(\\d+)");

  // Pre    Oct 2017: 1399 28148 NIMA MOJDEH 17-Jul-17 FO SIP
  // Oct 2017 onward: 1399 28148 NIMA MOJDEH 17-Jul-17 NH1 FO SIP
  private Pattern CREW_MEMBER_PATTERN = Pattern.compile("(\\d+) (\\d+) ([^0-9]+)"
      /* no space */
      + "(\\d{2})-(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)-(\\d{2})"
      + " ([A-Z0-9]+)? ?(FO|CA) ([A-Z]+)");
  private Pattern PAGE_FOOTER_PATTERN = Pattern.compile(
      "((\\d+) )?(\\d{1,2})/(\\d{1,2})/(\\d{4})");

  private void parse(String line, SeniorityList.Builder builder) throws ParseException {
    if (line.isEmpty()) {
      return;
    }
    switch(state) {
      case START:
      case PAGE_FOOTER:
        // December 2018 didn't have the SYSSEN header
        if (line.equals(LIST_HEADER)) {
          state = ParseState.LIST_HEADER;
          break;
        }
        Matcher matcher = PAGE_HEADER_PATTERN.matcher(line);
        // Oct 2018 had a non-standard header.
        if (!line.contains("SYSSEN") && !matcher.matches()) {
          // August 2018 started containing two lists - one old-style, one
          // split out by domicile. Ignore the rest of the document when we
          // start seeing a domicile page header.
          if (domiciles.contains(line)) {
            state = ParseState.FINISHED;
            return;
          }
          throw new ParseException("header [" + line + "] unmatched");
        }
        logger.info("Parsing new page");
        state = ParseState.PAGE_HEADER;
        break;

      case PAGE_HEADER:
        if (line.equals(LIST_HEADER) || line.equals(LEGACY_LIST_HEADER)) {
          state = ParseState.LIST_HEADER;
          break;
          // Jan 2017 and prior didn't have it?
          // throw new ParseException("header [" + line + "] unmatched");
        }
        // fall through

      case LIST_HEADER:
        matcher = CREW_MEMBER_PATTERN.matcher(line);
        if (!matcher.matches()) {
          throw new ParseException("unmatched crew member [" + line + "] (blank page?)");
        } else {
          // Oct 2018 SYSSEN did not have domicile headers.
          // So, ignore the rest of the document once we see #1 twice.
          int seniorityId = Integer.parseInt(matcher.group(1));
          if (seniorityId == 1) {
            if (firstPass) {
              state = ParseState.FINISHED;
              return;
            } else {
              firstPass = true;
            }
          }
        }
        addCrewMember(matcher, builder);
        state = ParseState.CREW_MEMBER;
        break;

      case CREW_MEMBER:
        matcher = CREW_MEMBER_PATTERN.matcher(line);
        if (!matcher.matches()) {
          matcher = PAGE_FOOTER_PATTERN.matcher(line);
          if (!matcher.matches()) {
            throw new ParseException("unmatched line [" + line + "] in state crew member");
          }
          state = ParseState.PAGE_FOOTER;
        } else {
          addCrewMember(matcher, builder);
        }
        break;

      case FINISHED:
        return;

      default:
        throw new ParseException("unhandled state " + state);
    }
  }

  private void addCrewMember(Matcher matcher, SeniorityList.Builder builder)
      throws ParseException {
    CrewMember.Builder member = builder.addCrewMemberBuilder();
    member.setSeniorityId(Integer.parseInt(matcher.group(1)));
    member.setEmployeeId(Integer.parseInt(matcher.group(2)));
    member.setName(matcher.group(3).trim());
    LocalDate hireDate =
        parseHireDate(matcher.group(4), matcher.group(5), matcher.group(6));
    member.setHireDate(hireDate.toString());
    // Domicile was added in October 2017.
    if (matcher.group(7) != null) {
      // new-style
      member.setDomicile(Domicile.valueOf(matcher.group(7)));
    }
    member.setCrewPosition(CrewPosition.valueOf(matcher.group(8)));
    String statusText = matcher.group(9);
    Status status = STATUS_MAP.get(statusText);
    if (status == null) {
      throw new ParseException("unmatched Status: " + statusText);
    }
    member.setStatus(status);
  }

  private DateTimeFormatter HIRE_DATE_FORMATTER =
      DateTimeFormat.forPattern("d-MMM-y");

  private LocalDate parseHireDate(String dayOfMonth, String month, String yearStr) {
    int year = 1900 + Integer.parseInt(yearStr);
    if (year < 1950) {
      year += 100;
    }
    String syntheticDate = String.format("%s-%s-%d", dayOfMonth, month, year);
    return HIRE_DATE_FORMATTER.parseLocalDate(syntheticDate);
  }

  private String parsePdf() throws IOException, SAXException, TikaException {
    ByteArrayInputStream bais = new ByteArrayInputStream(systemSeniorityPdf);
    BodyContentHandler handler = new BodyContentHandler(-1);
    Metadata metadata = new Metadata();
    PDFParser parser = new PDFParser();
    ParseContext parseContext = new ParseContext();
    parser.parse(bais, handler, metadata, parseContext);
    return handler.toString();
  }
}
