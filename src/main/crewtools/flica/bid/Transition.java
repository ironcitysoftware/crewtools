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

package crewtools.flica.bid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import crewtools.flica.pojo.PairingKey;

public class Transition {
  private final List<PairingKey> addKeys;
  private final List<PairingKey> dropKeys;

  public Transition(Set<PairingKey> addKeys, Set<PairingKey> dropKeys) {
    this.addKeys = new ArrayList<>(addKeys);
    this.dropKeys = new ArrayList<>(dropKeys);
    Collections.sort(this.addKeys);
    Collections.sort(this.dropKeys);
  }

  public List<PairingKey> getAddKeys() {
    return addKeys;
  }

  public List<PairingKey> getDropKeys() {
    return dropKeys;
  }

  @Override
  public int hashCode() {
    return Objects.hash(addKeys, dropKeys);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof Transition)) {
      return false;
    }
    Transition that = (Transition) o;
    return addKeys.equals(that.addKeys)
        && dropKeys.equals(that.dropKeys);
  }

  @Override
  public String toString() {
    return String.format("Drop %s; Add %s", dropKeys, addKeys);
  }
}
