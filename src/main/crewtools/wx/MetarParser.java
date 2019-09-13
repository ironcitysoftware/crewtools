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

import java.util.Iterator;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;

import com.google.common.primitives.Ints;

// http://www.caa.co.uk/docs/33/CAP746.PDF
public class MetarParser {
  private static Logger log = Logger.getLogger(MetarParser.class.getName());

  public static final boolean OPTIONAL = true;

  public static final Pattern KRTime = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})Z$");
  public static final int RTime_Date = 1;
  public static final int RTime_Hour = 2;
  public static final int RTime_Minute = 3;

  public static final Pattern KRWind = Pattern
      .compile("^(VRB|\\d{3})(\\d{2,3})(G(\\d+))?KT$");
  public static final int RWind_Whence = 1;
  public static final int RWind_Vel = 2;
  public static final int RWind_Gust = 4;

  public static final Pattern KRWindVary = Pattern.compile("^(\\d{3})V(RB)?(\\d{3})$");
  public static final int RWindVary_From = 1;
  public static final int RWindVary_To = 3;

  public static final Pattern KRWholeVis = Pattern.compile("^(\\d)$");
  public static final int RWholeVis_Num = 1;

  public static final Pattern KRVis = Pattern.compile("^(M)?(\\d+)(/(\\d))?SM$");
  public static final int RVis_Minimum = 1;
  public static final int RVis_Top = 2;
  public static final int RVis_Divisor = 4;

  public static final Pattern KRRVR = Pattern.compile("^R.*/.*FT$");

  public static final Pattern KRWx = Pattern.compile(
      "^(VC)?(\\+|-)?(MI|BC|DR|BL|SH|TS|FZ|PR)?(DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|DU|SA|HZ|PY|VA|FU|PO|SQ|(\\+)?FC|SS|DS)*$");
  public static final int RWx_Vicinity = 1;
  public static final int RWx_Intensity = 2;
  public static final int RWx_Qualifier = 3;
  public static final int RWx_Wx = 4;

  public static final Pattern KRSky = Pattern
      .compile("^(SKC|CLR|((FEW|SCT|BKN|OVC|VV|[A-Z]{3})(\\d{3})(TCU|CB)?))$");
  public static final int RSky_Clear = 1;
  public static final int RSky_Cover = 3;
  public static final int RSky_Alt = 4;
  public static final int RSky_TS = 5;

  public static final Pattern KRTempDew = Pattern
      .compile("^(M)?(\\d{0,2})/(M)?(\\d{0,2})$");
  public static final int RTempSign = 1;
  public static final int RTemp = 2;
  public static final int RDewSign = 3;
  public static final int RDew = 4;

  public static final Pattern KRAlt = Pattern.compile("^A(\\d{2})(\\d{2})$");
  public static final int RAlt_Whole = 1;
  public static final int RAlt_Fraction = 2;

  public static final Pattern KRMillibarAlt = Pattern.compile("^Q(\\d{4})$");

  public static final Pattern KRLLWS = Pattern.compile("^WS(\\d{3})/(\\d{3})(\\d{2})KT$");
  public static final int RAltitudeOfShear = 1;
  public static final int RDirectionOfShear = 2;
  public static final int RSpeedOfShear = 3;

  public static final Pattern KRMilitaryVisibility = Pattern.compile("^(\\d{4})$");

  private final ParsedMetar result = new ParsedMetar();

  private final Iterator<String> tokens;
  private final DateTime baseDateTime;
  private int index = 0;

  public MetarParser(Iterator<String> tokens, DateTime baseDateTime) {
    this.tokens = tokens;
    this.baseDateTime = baseDateTime;
  }

  public ParsedMetar parse() {
    try {
      if (!tokens.hasNext()) {
        return result;
      }

      // airport id or 'NIL'
      String str = tokens.next();
      if (str.equals("NIL")) {
        return result;
      }

      result.isValid = true;  //premature?

      str = tokens.next();
      Matcher timeMatcher = KRTime.matcher(str);
      if (timeMatcher.matches()) {
        //time issued
        result.issued = baseDateTime
            .withDayOfMonth(Ints.tryParse(timeMatcher.group(RTime_Date)))
            .withHourOfDay(Ints.tryParse(timeMatcher.group(RTime_Hour)))
            .withMinuteOfHour(Ints.tryParse(timeMatcher.group(RTime_Minute)))
            .withSecondOfMinute(0)
            .withMillisOfSecond(0);
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      //AUTO or COR
      while (str.equals("AUTO") || str.equals("COR") || str.equals("RTD")) {
        result.isAutomated = str.equals("AUTO");
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      //wind
      Matcher windMatcher = KRWind.matcher(str);
      if (windMatcher.matches()) {
        result.windSpecified = true;
        String whence = windMatcher.group(RWind_Whence);
        if (whence.equals("VRB")) {
          result.windVariable = true;
        } else {
          result.windFrom = Ints.tryParse(whence);
        }
        result.windVelocity = Ints.tryParse(windMatcher.group(RWind_Vel));
        String gust = windMatcher.group(RWind_Gust);
        if (gust != null) {
          result.windGusts = Ints.tryParse(gust);
        }
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      //wind varying
      Matcher windVaryMatcher = KRWindVary.matcher(str);
      if (windVaryMatcher.matches()) {
        result.windSpecified = true;
        result.windVaryFrom = Ints.tryParse(windVaryMatcher.group(RWindVary_From));
        result.windVaryTo = Ints.tryParse(windVaryMatcher.group(RWindVary_To));
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      //military visibility in meters
      Matcher visibilityMetersMatcher = KRMilitaryVisibility.matcher(str);
      if (visibilityMetersMatcher.matches()) {
        result.visibilityMeters = Ints.tryParse(visibilityMetersMatcher.group(1));
        //http://www.lewis.army.mil/1ws/ftl-wx/taf.htm#Vis
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      } else {

        //visibility
        if (str.equals("P6SM")) {
          //this is more for TAF support
          result.isVisibilityGreaterThanSix = true;
          if (!tokens.hasNext())
            return result;
          str = tokens.next();
        } else {
          Matcher wholeVisibilityMatcher = KRWholeVis.matcher(str);
          if (wholeVisibilityMatcher.matches()) {
            result.visibilityWhole = Ints
                .tryParse(wholeVisibilityMatcher.group(RWholeVis_Num));
            if (!tokens.hasNext()) {
              return result;
            }
            str = tokens.next();
          }
          Matcher visibilityFractionMatcher = KRVis.matcher(str);
          if (visibilityFractionMatcher.matches()) {
            result.visNum = Ints.tryParse(visibilityFractionMatcher.group(RVis_Top));
            String den = visibilityFractionMatcher.group(RVis_Divisor);
            if (den != null) {
              result.visDen = Ints.tryParse(den);
            }
            if (!tokens.hasNext())
              return result;
            str = tokens.next();
          }
        }
      }

      //rvr
      Matcher rvrMatcher = KRRVR.matcher(str);
      if (!str.equals("RVRNO") && // MCEntire does this
          rvrMatcher.matches()) {
        result.rvr = Ints.tryParse(rvrMatcher.group(1));
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      // everything prior to sky cover is weather
      if (str.equals("NSW")) {
        //for support of TAF
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      // Europe
      if (str.equals("CAVOK")) {
        result.isVisibilityGreaterThanSix = true;
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      } else {

        while (true) {
          Matcher wxMatcher = KRWx.matcher(str);
          if (!wxMatcher.matches()) {
            break;
          }
          String vicinity = wxMatcher.group(RWx_Vicinity);
          String intensity = wxMatcher.group(RWx_Intensity);
          String qualifier = wxMatcher.group(RWx_Qualifier);
          String wx = wxMatcher.group(RWx_Wx);
          String tmp = "";
          if (vicinity != null && vicinity.length() > 0) {
            tmp += "VC"; // in vicinity
          }
          if (intensity != null)
            tmp += intensity;
          if (qualifier != null)
            tmp += qualifier;
          if (wx != null)
            tmp += wx;
          // TODO more than one weather phenomenon
          result.weather.add(tmp);
          if (!tokens.hasNext())
            return result;
          str = tokens.next();
        }
      }

      //sky
      while (true) {
        Matcher tempDewMatcher = KRTempDew.matcher(str);
        Matcher skyMatcher = KRSky.matcher(str);
        if (tempDewMatcher.matches() || !skyMatcher.matches()) {
          break;
        }
        String skyclear = skyMatcher.group(RSky_Clear);
        if (skyclear != null &&
            (skyclear.equals("SKC") || skyclear.equals("CLR"))) {
          // encode?
        } else {
          String cover = skyMatcher.group(RSky_Cover);
          int ceiling = Ints.tryParse(skyMatcher.group(RSky_Alt)) * 100;
          if (skyMatcher.group(RSky_TS) != null) {
            String unusedTS = skyMatcher.group(RSky_TS);
            // encode?
          }
          if (ceiling > -1) {
            result.ceiling.put(ceiling, cover);
            break;
          }
        }
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      Matcher windshearMatcher = KRLLWS.matcher(str);
      if (windshearMatcher.matches()) {
        int feet = Ints.tryParse(windshearMatcher.group(RAltitudeOfShear));
        int dir = Ints.tryParse(windshearMatcher.group(RDirectionOfShear));
        int speed = Ints.tryParse(windshearMatcher.group(RSpeedOfShear));

        // TODO use...

        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      //temp dew
      Matcher tempDewMatcher = KRTempDew.matcher(str);
      if (tempDewMatcher.matches()) {
        int sign = tempDewMatcher.group(RTempSign) == null ? 1 : -1;
        int t = Ints.tryParse(tempDewMatcher.group(RTemp));

        // TODO use...

        sign = tempDewMatcher.group(RDewSign) == null ? 1 : -1;
        int d = Ints.tryParse(tempDewMatcher.group(RDew));
        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }

      //alt
      Matcher altimeterMatcher = KRAlt.matcher(str);
      Matcher millibarMatcher = KRMillibarAlt.matcher(str);
      if (altimeterMatcher.matches()) {

        // TODO use ...

        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      } else if (millibarMatcher.matches()) {

        // TODO use ...

        if (!tokens.hasNext())
          return result;
        str = tokens.next();
      }
    } catch (Throwable e) {
      e.printStackTrace();
      log.severe("Error parsing: " + tokens);
    }
    return result;
  }
}
