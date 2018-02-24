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

import java.util.ArrayList;
import java.util.List;

import org.joda.time.LocalDate;

import com.google.common.base.Objects;

import crewtools.flica.Proto;

public class ThinLine {
  private final String lineName;
  private final List<PairingKey> pairingKeys;

  public ThinLine(Proto.ThinLine protoThinLine) {
    this.lineName = protoThinLine.getLineName();
    this.pairingKeys = new ArrayList<>();
    for (Proto.ThinPairing protoThinPairing : protoThinLine.getThinPairingList()) {
      LocalDate pairingDate = LocalDate.parse(protoThinPairing.getDate());
      pairingKeys.add(new PairingKey(pairingDate, protoThinPairing.getPairingName()));
    }
  }

  public String getLineName() {
    return lineName;
  }

  public List<PairingKey> getPairingKeys() {
    return pairingKeys;
  }

  @Override
  public boolean equals(Object that) {
    if (that == null) { return false; }
    if (!(that instanceof ThinLine)) { return false; }
    return Objects.equal(((ThinLine) that).lineName, lineName)
        && Objects.equal(((ThinLine) that).pairingKeys, pairingKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(lineName, pairingKeys);
  }

  @Override
  public String toString() {
    return lineName;
  }
}
