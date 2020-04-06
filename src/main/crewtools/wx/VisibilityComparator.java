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

import java.util.Comparator;

public class VisibilityComparator implements Comparator<Visibility> {
  @Override
  public int compare(Visibility left, Visibility right) {
    if (left.isUnlimited() && right.isUnlimited()) {
      return 0;
    }
    if (left.isUnlimited() ^ right.isUnlimited()) {
      return left.isUnlimited() ? 1 : -1;
    }
    return Integer.compare(left.getFeet(), right.getFeet());
  }
}
