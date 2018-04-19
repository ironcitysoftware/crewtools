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

package crewtools.flica.pojo;

import java.util.List;
import java.util.Objects;

import org.joda.time.LocalDate;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

/**
 * Pairing names can be repeated for different months and mean different things.
 * When doing any work involving multiple months, use this class to
 * disambiguate pairings.
 */
public class PairingKey {
  private LocalDate pairingDate;
  private String pairingName;

  public PairingKey(LocalDate pairingDate, String pairingName) {
    this.pairingDate = pairingDate;
    this.pairingName = pairingName;
  }

  public String getPairingName() {
    return pairingName;
  }

  public LocalDate getPairingDate() {
    return pairingDate;
  }

  // 2018-1-1:L1234
  public static PairingKey parse(String key) {
    List<String> parts = Splitter.on(":").splitToList(key);
    Preconditions.checkState(parts.size() == 2, "2018-1-1:L1234, not " + key);
    LocalDate date = LocalDate.parse(parts.get(0));
    return new PairingKey(date, parts.get(1));
  }

  @Override
  public int hashCode() {
    return Objects.hash(pairingDate, pairingName);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof PairingKey)) {
      return false;
    }
    PairingKey that = (PairingKey) o;
    return Objects.equals(pairingDate, that.pairingDate)
        && Objects.equals(pairingName, that.pairingName);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("date", pairingDate)
        .add("name", pairingName)
        .toString();
  }
}
