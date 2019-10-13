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

package crewtools.logbook;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import crewtools.flica.pojo.Leg;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;

public class LegIterator implements Iterator<Leg> {
  private final Schedule schedule;
  private Iterator<Leg> legs;

  public LegIterator(Schedule schedule) {
    this.schedule = schedule;
    List<Leg> legs = new ArrayList<>();
    for (Trip trip : schedule.trips) {
      for (Leg leg : trip.getLegs()) {
        if (!leg.hasLegType() && !leg.isDeadhead()) {
          legs.add(leg);
        }
      }
    }
    this.legs = legs.iterator();
  }

  @Override
  public boolean hasNext() {
    return legs.hasNext();
  }

  @Override
  public Leg next() {
    return legs.next();
  }
}
