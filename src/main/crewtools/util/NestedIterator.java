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

package crewtools.util;

import java.util.Iterator;

public class NestedIterator<I, O extends Iterable<I>> implements Iterator<I> {
  private Iterator<O> outerIterator;
  private Iterator<I> innerIterator = null;

  public NestedIterator(Iterator<O> outerIterator) {
    this.outerIterator = outerIterator;
  }

  public Iterator<I> getIterator(O outer) {
    return outer.iterator();
  }

  @Override
  public boolean hasNext() {
    if (innerIterator == null) {
      if (!outerIterator.hasNext()) {
        return false;
      }
      innerIterator = getIterator(outerIterator.next());
    }
    while (!innerIterator.hasNext()) {
      if (!outerIterator.hasNext()) {
        return false;
      }
      innerIterator = getIterator(outerIterator.next());
    }
    return true;
  }

  @Override
  public I next() {
    return innerIterator.next();
  }
}
