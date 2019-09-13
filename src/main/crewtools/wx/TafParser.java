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

package crewtools.wx;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;

import crewtools.wx.ParsedTaf.TafPeriod;

public class TafParser {
  private static Logger logger = Logger.getLogger(TafParser.class.getName());

  private static final Pattern KRTime = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})Z$");
  private static final int RTime_Date = 1;
  private static final int RTime_Hour = 2;
  private static final int RTime_Minute = 3;

  private static final Pattern KRValid = Pattern
      .compile("^(\\d{2})(\\d{2})/(\\d{2})(\\d{2})$");
  private static final int RValid_FromDate = 1;
  private static final int RValid_FromHour = 2;
  private static final int RValid_ToDate = 3;
  private static final int RValid_ToHour = 4;

  private static final Pattern KRRange = Pattern
      .compile("^(\\d{2})(\\d{2})/(\\d{2})(\\d{2})$");
  private static final int RRange_FromDate = 1;
  private static final int RRange_FromHour = 2;
  private static final int RRange_ToDate = 3;
  private static final int RRange_ToHour = 4;

  private static final Pattern KRFrom = Pattern.compile("^FM(\\d{2})(\\d{2})(\\d{2})$");
  private static final int RFrom_Date = 1;
  private static final int RFrom_Hour = 2;
  private static final int RFrom_Minute = 3;

  private static final Pattern KRProb = Pattern.compile("^PROB(\\d{2})$");
  private static final int RProb_Percent = 1;

  private final DateTime nowUtc;
  private final List<String> lines;

  private final ParsedTaf result = new ParsedTaf();

  private final Splitter splitter = Splitter.on(' ').omitEmptyStrings().trimResults();

  public TafParser(DateTime nowUtc, List<String> lines) {
    this.nowUtc = nowUtc;
    this.lines = lines;
  }

  public ParsedTaf parse() {
    if (lines.isEmpty())
      return result;

    String txt = lines.get(0);
    StringTokenizer st = new StringTokenizer(txt, " ");

    // Sometimes KXXX TAF XXX, sometimes TAF XXX
    for (int i = 0; i < 2; i++) {
      if (st.nextToken().equals("TAF")) {
        break;
      }
    }

    String unusedAirportId = st.nextToken();
    if (unusedAirportId.equals("TAF")) { // Germany?
      unusedAirportId = st.nextToken();
    }
    if (unusedAirportId.equals("COR")) {
      unusedAirportId = st.nextToken(); // LSZS
    }

    String s = st.nextToken();

    // time issued - some military tafs do not have issue time, only valid time
    Matcher issuedMatcher = KRTime.matcher(s);

    if (!issuedMatcher.matches()) {
      // military station, with no issued time.
    } else {
      // Issued time 31 May but current date is 1 June
      int issueDayOfMonth = Ints.tryParse(issuedMatcher.group(RTime_Date));
      boolean issuedInPreviousMonth = Math
          .abs(issueDayOfMonth - nowUtc.getDayOfMonth()) > 2;
      result.issued = new DateTime(nowUtc)
          .withDayOfMonth(Ints.tryParse(issuedMatcher.group(RTime_Date)))
          .withHourOfDay(Ints.tryParse(issuedMatcher.group(RTime_Hour)))
          .withMinuteOfHour(Ints.tryParse(issuedMatcher.group(RTime_Minute)))
          .withSecondOfMinute(0)
          .withMillisOfSecond(0)
          .withPeriodAdded(Period.months(1), issuedInPreviousMonth ? -1 : 0);
      if (!st.hasMoreTokens())
        return result;
      s = st.nextToken();
    }

    Matcher validMatcher = KRValid.matcher(s);
    if (!validMatcher.matches()) {
      return result;
    }

    result.validFrom = setDayAndHour(
        nowUtc,
        validMatcher.group(RValid_FromDate),
        validMatcher.group(RValid_FromHour))
        .withMinuteOfHour(0)
        .withSecondOfMinute(0)
        .withMillisOfSecond(0);

    result.validTo = setDayAndHour(
        result.validFrom,
        validMatcher.group(RValid_ToDate),
        validMatcher.group(RValid_ToHour));

    if (!st.hasMoreTokens())
      return result;

    Interval validInterval = new Interval(result.validFrom, result.validTo);
    TafPeriod previousPeriod = new TafPeriod(validInterval, null, null);

    List<String> remainder = new ArrayList<>();
    while (st.hasMoreTokens()) {
      remainder.add(st.nextToken());
    }
    logger.finest("remainder: " + remainder);
    result.forecast.put(previousPeriod, decodePeriod(remainder));

    for (int i = 1; i < lines.size(); ++i) {
      String str = lines.get(i);
      logger.finest("line: " + str);

      ListIterator<String> tokens = splitter.splitToList(str).listIterator();
      if (!tokens.hasNext())
        continue;

      do {
        TafPeriod period = parseTafPeriod(tokens, validInterval);
        if (period == null) {
          logger.warning("unable to parse taf period: " + tokens);
          break;
        }

        // if the period doesn't have a modifier, end the
        // previous period when this period starts.
        // (periods with a modifier already have start/end times).

        if (period.modifier == null) {
          Preconditions.checkState(result.forecast.containsKey(previousPeriod));
          ParsedMetar previousCondition = result.forecast.remove(previousPeriod);
          TafPeriod truncatedPeriod = previousPeriod
              .truncateTo(period.interval.getStart());
          result.forecast.put(truncatedPeriod, previousCondition);
          previousPeriod = period;
        }

        // say we have "condition prob30 condition"
        // we want to add "condition", and re-run loop with "prob30 condition".

        List<String> conditionTokens = new ArrayList<>();
        while (tokens.hasNext()) {
          String token = tokens.next();
          Matcher innerProbMatcher = KRProb.matcher(token);
          if (innerProbMatcher.matches()) {
            tokens.previous();
            break; // loop again to catch tempo
          }
          conditionTokens.add(token);
        }
        result.forecast.put(period, decodePeriod(conditionTokens));
      } while (tokens.hasNext());

    } // for each line

    return result;
  }

  private ParsedMetar decodePeriod(List<String> tokens) {
    List<String> copy = new ArrayList<>(tokens);
    copy.add(0, "KXYZ");
    copy.add(1, "200000Z");
    MetarParser metarParser = new MetarParser(copy.iterator(),
        new DateTime(DateTimeZone.UTC));
    return metarParser.parse();
  }

  private TafPeriod parseTafPeriod(ListIterator<String> tokens,
      Interval currentInterval) {
    String str = tokens.next();
    Matcher fromMatcher = KRFrom.matcher(str);
    Matcher probMatcher = KRProb.matcher(str);
    if (str.equals("BECMG") ||
        str.equals("TEMPO") ||
        probMatcher.matches()) {
      // eg parse PROB30 2504/2506

      TafPeriod.Modifier modifier = null;
      Integer probability = null;

      // BECMG 0004 or TEMPO 1822, PROB30, etc
      if (str.equals("BECMG")) {
        modifier = TafPeriod.Modifier.BECMG;
      } else if (str.equals("TEMPO")) {
        modifier = TafPeriod.Modifier.TEMPO;
      } else {
        modifier = TafPeriod.Modifier.PROB;
        probability = Ints.tryParse(probMatcher.group(RProb_Percent));
      }

      if (!tokens.hasNext()) {
        return null; // whoa there should be at least one more
      }
      str = tokens.next();

      Matcher rangeMatcher = KRRange.matcher(str);
      if (!rangeMatcher.matches()) {
        return null; // whoa this should match something?
      }

      // See testOctoberNovemberBoundary.
      DateTime from = setDayAndHour(
          currentInterval.getStart(),
          rangeMatcher.group(RRange_FromDate),
          rangeMatcher.group(RRange_FromHour));

      DateTime to = setDayAndHour(
          from,
          rangeMatcher.group(RRange_ToDate),
          rangeMatcher.group(RRange_ToHour));

      return new TafPeriod(new Interval(from, to), modifier, probability);

    } else if (fromMatcher.matches()) {

      // eg FM242300
      DateTime from = setDayAndHour(
          currentInterval.getStart(),
          fromMatcher.group(RFrom_Date),
          fromMatcher.group(RFrom_Hour))
              .withMinuteOfHour(
                  Ints.tryParse(fromMatcher.group(RFrom_Minute)));
      return new TafPeriod(new Interval(from, result.validTo), null, null);

    } else {
      // don't recognize it
      return null;
    }
  }

  private DateTime setDayAndHour(DateTime baseDateTime, String day, String hour) {
    int validDay = Ints.tryParse(day);
    int validHour = Ints.tryParse(hour);
    if (validHour < 24) {
      return baseDateTime
          .withDayOfMonth(validDay)
          .withHourOfDay(validHour);
    } else {
      Preconditions.checkState(validHour == 24);
      return baseDateTime
          .withDayOfMonth(validDay)
          .withHourOfDay(0)
          .plusDays(1);
    }
  }
}
