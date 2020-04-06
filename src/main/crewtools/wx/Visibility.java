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

package crewtools.wx;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

import crewtools.airport.Proto;
import crewtools.util.CompoundFraction;

public class Visibility {

  private static final int FEET_PER_STATUTE_MILE = 5000; // Per FAR AIM 5-4-20
  private static final int RVR_MAGNITUDE = 100;

  // FAR AIM 5-4-20
  private static final Map<CompoundFraction, Integer> SM_TO_FEET = ImmutableMap
      .<CompoundFraction, Integer>builder()
      .put(CompoundFraction.parse("1/4"), 1600)
      .put(CompoundFraction.parse("1/2"), 2400)
      .put(CompoundFraction.parse("5/8"), 3200)
      .put(CompoundFraction.parse("3/4"), 4000)
      .put(CompoundFraction.parse("7/8"), 4500)
      .put(CompoundFraction.parse("1"), 5000)
      .put(CompoundFraction.parse("1 1/4"), 6000)
      .build();

  private final Integer rvr;
  private final CompoundFraction statuteMile;
  private final Boolean greaterThanSixMiles;
  private final Integer feet;

  private Visibility(Integer rvr, CompoundFraction statuteMile,
      Boolean greaterThanSixMiles) {
    Preconditions
        .checkState(rvr != null ^ statuteMile != null ^ greaterThanSixMiles != null);
    this.rvr = rvr;
    this.statuteMile = statuteMile;
    this.greaterThanSixMiles = greaterThanSixMiles;
    if (rvr != null) {
      Preconditions.checkArgument(rvr <= 60);
      this.feet = rvr * RVR_MAGNITUDE;
    } else if (statuteMile != null) {
      this.feet = convertToFeet(statuteMile);
    } else {
      this.feet = null;
    }
  }

  public boolean hasFeet() {
    return feet != null;
  }

  public int getFeet() {
    Preconditions.checkNotNull(feet);
    return feet;
  }

  public boolean isUnlimited() {
    return greaterThanSixMiles != null && greaterThanSixMiles;
  }

  private int convertToFeet(CompoundFraction statuteMile) {
    if (SM_TO_FEET.containsKey(statuteMile)) {
      return SM_TO_FEET.get(statuteMile);
    }
    int feet = FEET_PER_STATUTE_MILE * statuteMile.getWhole();
    statuteMile = statuteMile.removeWhole();
    if (!statuteMile.hasNumerator()) {
      return feet;
    }
    if (SM_TO_FEET.containsKey(statuteMile)) {
      return feet + SM_TO_FEET.get(statuteMile);
    } else {
      return feet +
          (FEET_PER_STATUTE_MILE * statuteMile.getNumerator() / statuteMile.getDenominator());
    }
  }

  public static Visibility rvr(int rvr) {
    return new Visibility(rvr, null, null);
  }

  public static Visibility statuteMile(String statuteMile) {
    return Visibility.statuteMile(CompoundFraction.parse(statuteMile));
  }

  public static Visibility statuteMile(CompoundFraction statuteMile) {
    return new Visibility(null, statuteMile, null);
  }

  public static Visibility greaterThanSixMiles() {
    return new Visibility(null, null, true);
  }

  public static Visibility fromProto(Proto.Visibility proto) {
    if (proto.hasRvr()) {
      return Visibility.rvr(proto.getRvr());
    } else if (proto.hasStatuteMile()) {
      return Visibility.statuteMile(proto.getStatuteMile());
    } else {
      throw new IllegalArgumentException(proto.toString());
    }
  }

  public static final Pattern MilitaryVisibility = Pattern.compile("^(\\d{4})$");

  public Visibility parse(String str) {
    // military visibility in meters
    Matcher visibilityMetersMatcher = MilitaryVisibility.matcher(str);
    if (visibilityMetersMatcher.matches()) {
      int visibilityMeters = Ints.tryParse(visibilityMetersMatcher.group(1));
      // http://www.lewis.army.mil/1ws/ftl-wx/taf.htm#Vis
      if (visibilityMeters == 9999) {
        return Visibility.greaterThanSixMiles();
      }
      throw new IllegalStateException("handle meters");
    }

    // rvr or statute mile visibility
    // this is for TAF support
    if (str.equals("P6SM")) {
      return Visibility.greaterThanSixMiles();
    } else {
      return Visibility.statuteMile(str);
    }
  }

  @Override
  public String toString() {
    if (isUnlimited()) {
      return "P6SM";
    } else if (rvr != null) {
      return "RVR" + rvr;
    } else {
      return statuteMile + "SM";
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(rvr, statuteMile, greaterThanSixMiles);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof Visibility)) {
      return false;
    }
    Visibility that = (Visibility) o;
    return Objects.equals(rvr, that.rvr)
        && Objects.equals(statuteMile, that.statuteMile)
        && Objects.equals(greaterThanSixMiles, that.greaterThanSixMiles);
  }
}
