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
import java.util.List;

import org.joda.time.DateTime;

import crewtools.rpc.Proto;
import crewtools.util.Clock;

public class RuntimeStats {
  private final Clock clock;
  private final ScheduleWrapperTree tree;
  private DateTime lastEmailTrip;
  private DateTime lastOpentimeTrip;
  private int numEmailTrips;
  private int numOpentimeTrips;
  private List<String> submittedSwaps;

  public RuntimeStats(Clock clock, ScheduleWrapperTree tree) {
    this.clock = clock;
    this.tree = tree;
    this.submittedSwaps = new ArrayList<>();
  }

  public synchronized void incrementEmailTrip() {
    numEmailTrips++;
    lastEmailTrip = clock.now();
  }

  public synchronized void incrementOpentimeTrip() {
    numOpentimeTrips++;
    lastOpentimeTrip = clock.now();
  }

  public synchronized void recordSwap(String swap) {
    submittedSwaps.add(swap);
  }

  public synchronized void populate(Proto.Status.Builder builder) {
    builder.setNumEmail(numEmailTrips);
    builder.setNumOpentime(numOpentimeTrips);
    builder.setNumSwaps(submittedSwaps.size());
    tree.populate(builder);
  }

  @Override
  public String toString() {
    return toStringInternal();
  }

  private synchronized String toStringInternal() {
    String result = "";
    result = String.format("%d trips from email (last %s)\n", numEmailTrips,
        numEmailTrips > 0 ? lastEmailTrip : "none");
    result += String.format("%d trips from opentime (last %s)\n", numOpentimeTrips,
        numOpentimeTrips > 0 ? lastOpentimeTrip : "none");
    for (String swap : submittedSwaps) {
      result += swap + "\n";
    }
    result += tree.toString();
    return result;
  }
}
