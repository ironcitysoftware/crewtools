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

package crewtools.flica.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

import crewtools.rpc.Proto.GridObservation;
import crewtools.rpc.Proto.Observation;

public class GridEvaluator {
  private final Logger logger = Logger.getLogger(GridEvaluator.class.getName());

  private final YearMonth yearMonth;

  private final GridObservation fromGrid;
  private final GridObservation toGrid;
  private final Map<LocalDate, GridEntry> fromEntries;
  private final Map<LocalDate, GridEntry> toEntries;

  public static class GridEntry {
    public final int netReserves;
    public final int minRequired;

    public GridEntry(int netReserves, int minRequired) {
      this.netReserves = netReserves;
      this.minRequired = minRequired;
    }
  }

  public static class GridEvaluation {
    public final boolean swappable;
    public final boolean requiresCrewScheduling;

    public GridEvaluation(boolean swappable, boolean requiresCrewScheduling) {
      this.swappable = swappable;
      this.requiresCrewScheduling = requiresCrewScheduling;
    }
  }

  public GridEvaluator(YearMonth yearMonth, GridObservation fromGrid,
      GridObservation toGrid) {
    this.fromGrid = fromGrid;
    this.toGrid = toGrid;
    this.yearMonth = yearMonth;
    this.fromEntries = getEntryMapping(fromGrid);
    this.toEntries = getEntryMapping(toGrid);
  }

  private Map<LocalDate, GridEntry> getEntryMapping(GridObservation gridObservation) {
    ImmutableMap.Builder<LocalDate, GridEntry> result = ImmutableMap.builder();
    for (Observation observation : gridObservation.getObservationList()) {
      Preconditions.checkState(observation.getMonth() == yearMonth.getMonthOfYear());
      LocalDate date = new LocalDate(yearMonth.getYear(), yearMonth.getMonthOfYear(),
          observation.getDay());
      int netReserves = observation.getAvailableReserve() - observation.getOpenDutyPeriods();
      result.put(date, new GridEntry(netReserves, observation.getMinRequired()));
    }
    return result.build();
  }

  public GridEvaluation evaluate(Set<LocalDate> fromDates, Set<LocalDate> toDates) {
    DateEvaluation fromEval = evaluate(fromDates, fromEntries);
    DateEvaluation toEval = evaluate(toDates, toEntries);
    if (fromEval.allGreen) {
      return new GridEvaluation(true /* swappable */, false /* without CS */);
    }
    // Begin hand-waving.
    List<Integer> fromNets = Ordering.natural().sortedCopy(fromEval.nets);
    List<Integer> toNets = Ordering.natural().sortedCopy(toEval.nets);
    int len = Math.min(fromNets.size(), toNets.size());
    for (int i = 0; i < len; ++i) {
      String message = String.format("Considering ordered net reserves "
          + "%d of from:%03d vs to:%03d", i, fromNets.get(i), toNets.get(i));
      logger.info(message);
      if (fromNets.get(i) < toNets.get(i)) {
        return new GridEvaluation(false, false);
      }
    }
    // End hand-waving.
    return new GridEvaluation(true, true);
  }

  private DateEvaluation evaluate(Set<LocalDate> dates, Map<LocalDate, GridEntry> entries) {
    List<Integer> nets = new ArrayList<>();
    boolean allGreen = true;
    for (LocalDate date : Ordering.natural().sortedCopy(dates)) {
      GridEntry entry = Preconditions.checkNotNull(entries.get(date));
      if (entry.netReserves <= entry.minRequired) {
        allGreen = false;
      }
      nets.add(entry.netReserves);
    }
    return new DateEvaluation(allGreen, nets);
  }

  public static class DateEvaluation {
    public final boolean allGreen;
    public final List<Integer> nets;

    public DateEvaluation(boolean allGreen, List<Integer> nets) {
      this.allGreen = allGreen;
      this.nets = nets;
    }
  }
}
