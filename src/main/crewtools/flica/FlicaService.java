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

package crewtools.flica;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import crewtools.flica.Proto.Rank;
import crewtools.flica.pojo.PairingKey;
import crewtools.util.Calendar;
import okhttp3.HttpUrl;
import okhttp3.Response;

public class FlicaService {
  private final Logger logger = Logger.getLogger(FlicaService.class.getName());

  private final BaseFlicaConnection connection;

  private static final String HOST = "jia.flica.net";

  private static final String DOMAIN =
      "https://jia.flica.net/";

  private static final String AWARD_BY_LINE_BASE_URL = "awardbyline.cgi";

  private static final String SCHEDULE_BY_MONTH_BASE_URL = "scheduledetail.cgi";

  private static final String ALL_PAIRINGS_BASE_URL =
      "pddetail.cgi";

  private static final String ALL_LINES_BASE_URL =
      "bslines.cgi";

  private static final String OPEN_TIME_BASE_URL =
      "otopentimepot.cgi";

  private static final String PAIRING_DETAIL_BASE_URL =
      "rbcpair.cgi";

  private static final String RESERVE_GRID_BASE_URL =
      "viewresg.cgi";

  private static final String TRIP_SWAP_BASE_URL =
      "otswap2.cgi";

  private static final String BIDS_BASE_URL =
      "bsbids.cgi";

  private static final String OPENTIME_REQUEST_BASE_URL =
      "otrequest.cgi";

  private static final String SHOW_DOCUMENT_STAGE_ONE_BASE_URL =
      "coversheet_showdocument.cgi";

  private static final String CREW_MEMBER_SCHEDULE_URL = "cmschedules.cgi";

  private static final String SHOW_DOCUMENT_STAGE_TWO_FORMAT_SPEC =
      DOMAIN + "public/getdoc.dll/%s_Document%03d.pdf?type=bc&bc=%d";

  private static final String TRADE_BOARD_ALL_REQUESTS_URL = "online/TB_otherrequests.cgi";

  public FlicaService(BaseFlicaConnection connection) {
    this.connection = connection;
  }

  public void connect() throws IOException {
    Preconditions.checkState(connection.connect(), "Connect failed.");
  }

  private static final YearMonth JAN_2017 = YearMonth.parse("2017-01");
  private static final int AUG_2017_CODE_FO_SAP = 12;
  private static final YearMonth AUG_2017 = YearMonth.parse("2017-08");
  private static final int JAN_2017_CODE = 38;
  private static final String BID_CLOSE_ID_FORMAT_SPEC = "0%d%d.%03d";
  private static final int JUN_2019_CODE_CA_SAP = 34;
  private static final YearMonth JUN_2019 = YearMonth.parse("2019-06");
  private static final int NOV_2019_CODE_CA_SBB = 2;
  private static final YearMonth NOV_2019 = YearMonth.parse("2019-11");
  private static final YearMonth DEC_2019 = YearMonth.parse("2019-12");

  public static final int BID_ROUND_ONE = 1;
  public static final int BID_ROUND_TWO = 2;
  public static final int BID_LEGACY_SENIORITY_BASED = 4;
  public static final int BID_OPENTIME = 5;
  public static final int BID_FIRST_COME = 5; // alias
  public static final int BID_TRADEBOARD = 6;
  public static final int BID_CA_SBB = 7;
  // TODO FO_SBB
  public static final int BID_CA_SAP = 9;
  public static final int BID_FO_SAP = 10;
  public static final int BID_CA_FIRST_COME = 27;

  private static final Set<Integer> LEGAL_ROUNDS = ImmutableSet.<Integer>builder()
      .add(BID_ROUND_ONE)
      .add(BID_ROUND_TWO)
      .add(BID_LEGACY_SENIORITY_BASED)
      .add(BID_OPENTIME)
      .add(BID_TRADEBOARD)
      .add(BID_CA_SBB)
      .add(BID_CA_SAP)
      .add(BID_FO_SAP)
      .add(BID_CA_FIRST_COME)
      .build();

  // BCID = 01[Round 1=0, Round 2=1, SeniorityBased=3, Opentime=5,
  // CA SBB=6, CA SAP=8, FO SAP=9].xxx where 040 = March 2017
  // BCID = 02
  public static String getBidCloseId(int round, YearMonth yearMonth) {
    Preconditions.checkState(LEGAL_ROUNDS.contains(round));
    int roundCode = round - 1;
    int yearMonthCode;
    int prefixDigit = 1;
    if (round == BID_FO_SAP) {
      yearMonthCode = AUG_2017_CODE_FO_SAP + Months.monthsBetween(AUG_2017, yearMonth).getMonths();
    } else if (round == BID_CA_SAP) {
      yearMonthCode = JUN_2019_CODE_CA_SAP
          + Months.monthsBetween(JUN_2019, yearMonth).getMonths();
    } else if (round == BID_CA_SBB) {
      yearMonthCode = NOV_2019_CODE_CA_SBB
          + Months.monthsBetween(NOV_2019, yearMonth).getMonths();
    } else if (round == BID_CA_FIRST_COME) {
      prefixDigit = 2;
      roundCode = 7;
      yearMonthCode = Months.monthsBetween(DEC_2019, yearMonth).getMonths();
    } else {
      int monthsBetween = Months.monthsBetween(JAN_2017, yearMonth).getMonths();
      if (round == BID_OPENTIME) {
        // To view November opentime we need 014.049, not 014.048
        monthsBetween++;
      } else {
        if (round == BID_ROUND_ONE) {
          if (!yearMonth.isBefore(YearMonth.parse("2020-04"))) {
            // April rebid due to COVID-19
            monthsBetween++;
          }
        }
      }
      yearMonthCode = JAN_2017_CODE + monthsBetween;
    }
    return String.format(BID_CLOSE_ID_FORMAT_SPEC, prefixDigit, roundCode, yearMonthCode);
  }

  private static final String CREW_CLASS_ID_FORMAT_SPEC = "%s6%c";

  public static String getCrewClassId(AwardDomicile domicile, Rank rank) {
    char rankCharacter = rank == Rank.CAPTAIN ? 'C' : 'F';
    return String.format(CREW_CLASS_ID_FORMAT_SPEC,
        domicile.getAwardId(), rankCharacter);
  }

  String getCrewClass(AwardDomicile domicile, Rank rank) {
    return String.format("%s-CRJ-%s", domicile, rank == Rank.CAPTAIN ? "CA" : "FO");
  }

  public String getBidAward(AwardDomicile awardDomicile, Rank rank, int round, YearMonth yearMonth)
      throws IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    String crewClassId = getCrewClassId(awardDomicile, rank);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(AWARD_BY_LINE_BASE_URL)
        .addQueryParameter("bcid", bidCloseId)
        .addQueryParameter("ccid", crewClassId)
        .build();
    return connection.retrieveUrl(url);
  }

  // Month is 1-based
  // Year is either 201x or 1x
  public synchronized String getSchedule(YearMonth yearMonth)
      throws IOException {
    int year = yearMonth.getYear();
    if (year > 2000) {
      year -= 2000;
    }
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(SCHEDULE_BY_MONTH_BASE_URL)
        .addQueryParameter("GO", "1")
        .addQueryParameter("BlockDate",
            String.format("%02d%02d", yearMonth.getMonthOfYear(), year))
        .build();
    return connection.retrieveUrl(url);
  }

  private static final DateTimeFormatter DIGITS_ONLY_DATE_FORMAT =
      DateTimeFormat.forPattern("yyyyMMdd");

  public static HttpUrl getAllPairingsUrl(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth) {
    String bcid = getBidCloseId(round, yearMonth);
    String ccid = getCrewClassId(awardDomicile, rank);
    Calendar calendar = new Calendar(yearMonth);
    String startDate = DIGITS_ONLY_DATE_FORMAT.print(calendar.getFirstDateInPeriod());
    String endDate = DIGITS_ONLY_DATE_FORMAT.print(calendar.getLastDateInPeriod());

    return new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(ALL_PAIRINGS_BASE_URL)
        .addQueryParameter("WithSorting", "YES")
        .addQueryParameter("bFromOT", "1")
        .addQueryParameter("BCID", bcid)
        .addQueryParameter("CC", ccid)
        .addQueryParameter("SortOperation", "0")
        .addQueryParameter("AllPairingFlag", "1")
        .addQueryParameter("StartDate", startDate)
        .addQueryParameter("EndDate", endDate)
        .addQueryParameter("PrintLines", "999999")
        .addQueryParameter("LinesPerPage", "999999")  // 22
        .build();
  }

  public String getAllPairings(AwardDomicile awardDomicile, Rank rank, int round,
      YearMonth yearMonth)
      throws IOException, URISyntaxException {
    HttpUrl url = getAllPairingsUrl(awardDomicile, rank, round, yearMonth);
    return connection.retrieveUrl(url);
  }

  public static HttpUrl getAllLinesUrl(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth) throws URISyntaxException, IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    String crewClassId = getCrewClassId(awardDomicile, rank);

    return new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(ALL_LINES_BASE_URL)
        .addQueryParameter("DOWNLOAD", "YES")
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("PrintLines", "99999")
        .addQueryParameter("FontSize", "7")
        .addQueryParameter("LinesPerPage", "99999")  // 22
        .addQueryParameter("FileType", "0")  // html
        .addQueryParameter("SO", "")
        .addQueryParameter("SLF", "SLF:2;")
        .addQueryParameter("ISPOPUP", "0")
        .addQueryParameter("HCI", "HCI=8;")
        .addQueryParameter("CCI", "CCI=1;")
        .addQueryParameter("VCI", "VCI=2;")
        .addQueryParameter("MD", "MD=1;")
        .addQueryParameter("INT", "INT=0;")
        .addQueryParameter("HSS", "HSS=1;")
        .addQueryParameter("CC", crewClassId)
        .build();
  }

  public String getAllLines(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth) throws URISyntaxException, IOException {
    HttpUrl url = getAllLinesUrl(awardDomicile, rank, round, yearMonth);
    return connection.retrieveUrl(url);
  }

  public static HttpUrl getOpenTimeUrl(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth) {
    String bidCloseId = getBidCloseId(round, yearMonth);
    String crewClassId = getCrewClassId(awardDomicile, rank);
    HttpUrl.Builder builder = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(OPEN_TIME_BASE_URL)
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("ViewOT", "1");
    if (round != FlicaService.BID_CA_SAP
        && round != FlicaService.BID_FO_SAP) {
        builder
            .addQueryParameter("SubmitBids", "NO")
            .addQueryParameter("CC", crewClassId);
    }
    return builder.build();
  }

  public synchronized String getOpenTime(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth)
      throws URISyntaxException, IOException {
    HttpUrl url = getOpenTimeUrl(awardDomicile, rank, round, yearMonth);
    return connection.retrieveUrl(url);
  }

  public static HttpUrl getPairingDetailUrl(String pairingName, LocalDate date) {
    return new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(PAIRING_DETAIL_BASE_URL)
        .addQueryParameter("DCOR", "7")
        .addQueryParameter("cfg", "7")
        .addQueryParameter("PID", pairingName)
        .addQueryParameter("DATE", DIGITS_ONLY_DATE_FORMAT.print(date))
        .build();
  }

  public String getPairingDetail(String pairingName, LocalDate date)
      throws URISyntaxException, IOException {
    HttpUrl url = getPairingDetailUrl(pairingName, date);
    return connection.retrieveUrl(url);
  }

  public static HttpUrl getReserveGridUrl(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth, String airlineId) {
    String bidCloseId = FlicaService.getBidCloseId(round, yearMonth);
    String crewClassId = FlicaService.getCrewClassId(awardDomicile, rank);
    return new HttpUrl.Builder()
        .scheme("https")
        .host("jia.flica.net")
        .addPathSegment("ui")
        .addPathSegment("private")
        .addPathSegment("bidCloseReserveGrid")
        .addPathSegment("index.html")
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("CC", crewClassId)
        .addQueryParameter("alid", airlineId)
        .build();
  }

  public static HttpUrl getReserveAvailabilityUrl(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth) {
    String bidCloseId = FlicaService.getBidCloseId(round, yearMonth);
    String crewClassId = FlicaService.getCrewClassId(awardDomicile, rank);
    return new HttpUrl.Builder()
        .scheme("https")
        .host("jia.flica.net")
        .addPathSegment("ui")
        .addPathSegment("private")
        .addPathSegment("reserveAvail")
        .addPathSegment("index.html")
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("CC", crewClassId)
        .build();
  }

  public static HttpUrl getReserveGridJsonUrl(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth) {
    String bidCloseId = getBidCloseId(round, yearMonth);
    String crewClassId = getCrewClassId(awardDomicile, rank);
    return new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(RESERVE_GRID_BASE_URL)
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("json", "1")
        .addQueryParameter("CC", crewClassId)
        .build();
  }

  public synchronized String getReserveGrid(
      AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth, String airlineId)
      throws URISyntaxException, IOException {
    HttpUrl url = getReserveGridUrl(awardDomicile, rank, round, yearMonth, airlineId);
    return connection.retrieveUrl(url);
  }

  public synchronized String getReserveAvailability(AwardDomicile awardDomicile,
      Rank rank,
      int round, YearMonth yearMonth)
      throws URISyntaxException, IOException {
    HttpUrl url = getReserveAvailabilityUrl(awardDomicile, rank, round, yearMonth);
    return connection.retrieveUrl(url);
  }

  public static HttpUrl getTradeBoardAllRequestsUrl(AwardDomicile awardDomicile,
      int round, YearMonth yearMonth) {
    String bidCloseId = getBidCloseId(round, yearMonth.plusMonths(1));
    return new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegments(TRADE_BOARD_ALL_REQUESTS_URL)
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("hdnBase", awardDomicile.name())
        .build();
  }

  public synchronized String submitSwap(int round, YearMonth yearMonth,
      LocalDate today, List<PairingKey> addTrips,
      List<PairingKey> dropTrips) throws URISyntaxException, IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(TRIP_SWAP_BASE_URL)
        .addQueryParameter("SubmitBids", "YES")
        .addQueryParameter("BCID", bidCloseId)
        .build();
    String startDate = DIGITS_ONLY_DATE_FORMAT.print(today);
    String endDate = DIGITS_ONLY_DATE_FORMAT.print(today.dayOfMonth().withMaximumValue().minusDays(1));
    ImmutableMultimap.Builder<String, String> data = ImmutableMultimap.builder();
    data.put("StartDate", startDate);
    data.put("EndDate", endDate);
    for (PairingKey key : addTrips) {
      data.put("addBid", formatPairingKeyForBid(key));
    }
    for (PairingKey key : dropTrips) {
      data.put("dropBid", formatPairingKeyForBid(key));
    }
    data.put("pageId", "25270342");  // needed?

    String submitPairs = "";
    for (PairingKey key : addTrips) {
      if (!submitPairs.isEmpty()) {
        submitPairs = submitPairs + " and ";
      }
      submitPairs += key.getPairingName()
          + ":"
          + String.format("%02d", key.getPairingDate().getDayOfMonth())
          + monthName(key.getPairingDate().getMonthOfYear());
    }

    String swapPairs = "";
    for (PairingKey key : dropTrips) {
      if (!swapPairs.isEmpty()) {
        swapPairs = swapPairs + " and ";
      }
      swapPairs += key.getPairingName()
          + ":"
          + String.format("%02d", key.getPairingDate().getDayOfMonth())
          + monthName(key.getPairingDate().getMonthOfYear());
    }

    data.put("MySwapPairs", swapPairs);
    data.put("MySubmitPairs", submitPairs);
    data.put("Restriction", "0");
    data.put("ALLR", "1");
    data.put("PIDX", "0");
    data.put("SELREQ", "false");
    data.put("btnAdd", "");

    HttpUrl refererUrl = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(TRIP_SWAP_BASE_URL)
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("MySwapPairs", swapPairs)
        .addQueryParameter("MySubmitPairs", submitPairs)
        .addQueryParameter("pageId", "2527034")
        .build();

    return connection.postUrlWithReferer(url, refererUrl.toString(), data.build());
  }

  private String monthName(int month) {
    switch (month) {
    case 1: return "JAN";
    case 2: return "FEB";
    case 3: return "MAR";
    case 4: return "APR";
    case 5:
      return "MAY";
    case 6:
      return "JUN";
    case 7:
      return "JUL";
    case 8:
      return "AUG";
    case 9:
      return "SEP";
    case 10:
      return "OCT";
    case 11:
      return "NOV";
    case 12:
      return "DEC";
    default: throw new IllegalStateException("fix this " + month);
    }
  }

  public Response submitLineBid(int round, YearMonth yearMonth,
      List<String> lines) throws URISyntaxException, IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(BIDS_BASE_URL)
        .addQueryParameter("SendData", "YES")
        .addQueryParameter("BCID", bidCloseId)
        .build();
    ImmutableMultimap.Builder<String, String> data = ImmutableMultimap.builder();
    data.put("BidsSync", "0");
    data.put("BidType", "TLineBid");
    for (String line : lines) {
      data.put("FB", line);
    }
    return connection.postUrl(url, data.build());
  }

  public String getOpentimeRequests(int round, YearMonth yearMonth)
      throws URISyntaxException, IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(OPENTIME_REQUEST_BASE_URL)
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("isInFrame", "false")
        // trumps the BCID; shows the most recently closed bid.
        // .addQueryParameter("VC", "yes")
        .build();
    return connection.retrieveUrl(url);
    //BO=20180121&GO=1
  }

  public String getPeerSchedule(int employeeId, YearMonth yearMonth) throws IOException {
    int shortYear = yearMonth.getYear();
    if (shortYear > 2000) {
      shortYear -= 2000;
    }
    String bd = String.format("%02d%02d", yearMonth.getMonthOfYear(), shortYear);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(CREW_MEMBER_SCHEDULE_URL)
        .addQueryParameter("EmployeeId", "" + employeeId)
        .addQueryParameter("bd", bd)
        .build();
    return connection.retrieveUrl(url);
  }

  // self.location='/public/getdoc.dll/CLT-CRJ-FO_Document008.pdf?type=bc&bc=3211274';
  private final Pattern DOC_ID = Pattern.compile("&bc=(\\d+)[&']");

  public byte[] getDocument(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth,
      int documentId, String title)
      throws URISyntaxException, IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    String crewClass = getCrewClass(awardDomicile, rank);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(SHOW_DOCUMENT_STAGE_ONE_BASE_URL)
        .addQueryParameter("bcid", bidCloseId)
        .addQueryParameter("CrewClass", crewClass)
        .addQueryParameter("docid", Integer.toString(documentId))
        .addQueryParameter("title", title)
        .build();
    String redirectText = connection.retrieveUrl(url);
    Matcher docIdMatcher = DOC_ID.matcher(redirectText);
    Preconditions.checkState(docIdMatcher.find(), redirectText);
    String serverDocumentId = docIdMatcher.group(1);
    logger.info("server document id = [" + serverDocumentId + "]");

    url = HttpUrl.parse(
        String.format(SHOW_DOCUMENT_STAGE_TWO_FORMAT_SPEC,
            getCrewClass(awardDomicile, rank),
            documentId,
            Integer.parseInt(serverDocumentId)));
    return connection.retrieveUrlBytes(url);
  }

  //  "L7436:20171025"
  String formatPairingKeyForBid(PairingKey key) {
    return String.format("%s:%s", key.getPairingName(),
        DIGITS_ONLY_DATE_FORMAT.print(key.getPairingDate()));
  }
}
