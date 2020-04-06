/**
 * Copyright 2020 Iron City Software LLC
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

package crewtools.util;

import java.util.Iterator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;

public class CompoundFraction {
  private final Integer whole;
  private final Integer numerator;
  private final Integer denominator;

  public CompoundFraction(Integer whole, Integer numerator, Integer denominator) {
    if (whole == null && numerator != null && denominator == null) {
      this.whole = numerator;
      this.numerator = null;
      this.denominator = null;
    } else {
      this.whole = whole;
      this.numerator = numerator;
      this.denominator = denominator;
    }
    Preconditions.checkArgument(denominator == null || denominator != 0);
  }

  public int getWhole() {
    return whole == null ? 0 : whole;
  }

  public boolean hasNumerator() {
    return numerator != null;
  }

  public int getNumerator() {
    return numerator == null ? 0 : numerator;
  }

  public int getDenominator() {
    Preconditions.checkNotNull(denominator, this.toString());
    return denominator;
  }

  public CompoundFraction removeWhole() {
    return new CompoundFraction(null, numerator, denominator);
  }

  private static final Pattern Whole = Pattern.compile("^(\\d+)$");
  private static final int Whole_Num = 1;

  private static final Pattern Fraction = Pattern.compile("^(\\d+)/(\\d+)$");
  private static final int Fraction_Numerator = 1;
  private static final int Fraction_Denominator = 2;

  private static final Splitter SPLITTER = Splitter.on(' ').trimResults()
      .omitEmptyStrings();

  public static CompoundFraction parse(String str) {
    Integer whole = null;
    Integer numerator = null;
    Integer denominator = null;

    Iterator<String> tokens = SPLITTER.split(str).iterator();
    if (!tokens.hasNext()) {
      throw new IllegalArgumentException("Not a compound fraction [" + str + "]");
    }
    String token = tokens.next();

    Matcher wholeVisibilityMatcher = Whole.matcher(token);
    if (wholeVisibilityMatcher.matches()) {
      whole = Ints
          .tryParse(wholeVisibilityMatcher.group(Whole_Num));
      if (!tokens.hasNext()) {
        return new CompoundFraction(whole, numerator, denominator);
      }
      token = tokens.next();
    }
    Matcher visibilityFractionMatcher = Fraction.matcher(token);
    if (visibilityFractionMatcher.matches()) {
      // original parsing code had denominator optional.
      numerator = Ints.tryParse(visibilityFractionMatcher.group(Fraction_Numerator));
      denominator = Ints.tryParse(visibilityFractionMatcher.group(Fraction_Denominator));
    }
    if (tokens.hasNext()) {
      throw new IllegalArgumentException("Not a compound fraction [" + token + "]");
    }
    return new CompoundFraction(whole, numerator, denominator);
  }

  @Override
  public String toString() {
    String result = "";
    if (whole != null) {
      result = Integer.toString(whole);
      if (numerator != null) {
        result += " ";
      }
    }
    if (numerator != null) {
      result += String.format("%d/%d", numerator, denominator);
    }
    return result;
  }

  @Override
  public int hashCode() {
    return Objects.hash(whole, numerator, denominator);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof CompoundFraction)) {
      return false;
    }
    CompoundFraction that = (CompoundFraction) o;
    return Objects.equals(whole, that.whole)
        && Objects.equals(numerator, that.numerator)
        && Objects.equals(denominator, that.denominator);
  }
}
