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

import crewtools.flica.Proto.Rank;
import crewtools.flica.pojo.PairingKey;
import okhttp3.HttpUrl;
import okhttp3.Response;

public class FlicaService {
  private final Logger logger = Logger.getLogger(FlicaService.class.getName());

  private final FlicaConnection connection;

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

  private static final String SHOW_DOCUMENT_STAGE_TWO_FORMAT_SPEC =
      DOMAIN + "public/getdoc.dll/%s_Document%03d.pdf?type=bc&bc=%d";

  public FlicaService(FlicaConnection connection) {
    this.connection = connection;
  }

  public void connect() throws IOException {
    Preconditions.checkState(connection.connect(), "Connect failed.");
  }

  private static final YearMonth JAN_2017 = YearMonth.parse("2017-01");
  private static final int AUG_2017_CODE_FO_SAP = 12;
  private static final YearMonth AUG_2017 = YearMonth.parse("2017-08");
  private static final int JAN_2017_CODE = 38;
  private static final String BID_CLOSE_ID_FORMAT_SPEC = "01%d.%03d";

  // BCID = 01[Round 1=0, Round 2=1, SeniorityBased=3, Opentime=5, FO SAP=9].xxx where 040 = March 2017
  String getBidCloseId(int round, YearMonth yearMonth) {
    Preconditions.checkState(round == 1 || round == 2 || round == 3
        || round == 4 || round == 5 || round == 10);
    int roundCode = round - 1;
    int yearMonthCode;
    if (roundCode == 9) {
      yearMonthCode = AUG_2017_CODE_FO_SAP + Months.monthsBetween(AUG_2017, yearMonth).getMonths();
    } else {
      int monthsBetween = Months.monthsBetween(JAN_2017, yearMonth).getMonths();
      if (roundCode == 4) {
        // To view November opentime we need 014.049, not 014.048
        monthsBetween++;
      } else {
        // But to view Feburary SBB (round code 3) we need 013.051, not 013.052
      }
      yearMonthCode = JAN_2017_CODE + monthsBetween; 
    }
    return String.format(BID_CLOSE_ID_FORMAT_SPEC, roundCode, yearMonthCode);
  }

  private String CREW_CLASS_ID_FORMAT_SPEC = "%s6%c";

  String getCrewClassId(AwardDomicile domicile, Rank rank) {
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
  public String getSchedule(YearMonth yearMonth)
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

  public String getAllPairings(AwardDomicile awardDomicile, Rank rank, int round, YearMonth yearMonth)
      throws IOException, URISyntaxException {
    String bcid = getBidCloseId(round, yearMonth);
    String ccid = getCrewClassId(awardDomicile, rank);
    LocalDate firstOfMonth = yearMonth.toLocalDate(1);
    LocalDate lastOfMonth = firstOfMonth.dayOfMonth().withMaximumValue();
    /* Probably doesn't belong here, but we want JAN 31 and MAR 1 with Feb. */
    if (yearMonth.getMonthOfYear() == 2) {
      firstOfMonth = firstOfMonth.minusDays(1);
      lastOfMonth = lastOfMonth.plusDays(1);
    }
    String startDate = DIGITS_ONLY_DATE_FORMAT.print(firstOfMonth);
    String endDate = DIGITS_ONLY_DATE_FORMAT.print(lastOfMonth);

    HttpUrl url = new HttpUrl.Builder()
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

    return connection.retrieveUrl(url);
  }

  public String getAllLines(AwardDomicile awardDomicile, Rank rank, 
      int round, YearMonth yearMonth) throws URISyntaxException, IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    String crewClassId = getCrewClassId(awardDomicile, rank);

    HttpUrl url = new HttpUrl.Builder()
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
    return connection.retrieveUrl(url);
  }

  public String getOpenTime(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth)
      throws URISyntaxException, IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    String crewClassId = getCrewClassId(awardDomicile, rank);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(OPEN_TIME_BASE_URL)
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("ViewOT", "1")
        .addQueryParameter("SubmitBids", "NO")
        .addQueryParameter("CC", crewClassId)
        .build();
    return connection.retrieveUrl(url);
  }

  public String getPairingDetail(String pairingName, LocalDate date)
      throws URISyntaxException, IOException {
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(PAIRING_DETAIL_BASE_URL)
        .addQueryParameter("DCOR", "7")
        .addQueryParameter("cfg", "7")
        .addQueryParameter("PID", pairingName)
        .addQueryParameter("DATE", DIGITS_ONLY_DATE_FORMAT.print(date))
        .build();
    return connection.retrieveUrl(url);
  }

  public String getReserveGrid(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth) throws URISyntaxException, IOException {
    String bidCloseId = getBidCloseId(round, yearMonth);
    String crewClassId = getCrewClassId(awardDomicile, rank);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("full")
        .addPathSegment(RESERVE_GRID_BASE_URL)
        .addQueryParameter("BCID", bidCloseId)
        .addQueryParameter("json", "1")
        .addQueryParameter("CC", crewClassId)
        .build();
    return connection.retrieveUrl(url);
  }

  public String submitSwap(int round, YearMonth yearMonth,
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
        .build();
    return connection.retrieveUrl(url);
    //BO=20180121&GO=1
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
