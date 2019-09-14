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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.junit.Test;

import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.PairingKey;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.test.FakeClock;
import crewtools.test.ScheduleBuilder;
import crewtools.test.TripBuilder;
import crewtools.util.Period;

public class SolverTest {
  private final Solver solver;
  private final List<FlicaTaskWrapper> tasks;
  private final FakeClock fakeClock;

  public SolverTest() throws IOException {
    Schedule schedule = new ScheduleBuilder()
        .withVacation(1, 7)
        .build();

    TripDatabase tripDatabase = new TripDatabase(null) {
      @Override
      public Trip getTrip(PairingKey key) {
        // A1000 will score lowest
        String overnight = "LGA";
        if (key.getPairingName().equals("A1000")) {
          overnight = "HVN";
        }
        return new TripBuilder()
            .withDayOfMonth(9)
            .withLeg("DCA", overnight, Period.hours(12))
            .withLayover(overnight, Period.hours(12))
            .withLeg(overnight, "DCA", Period.hours(10))
            .withName(key.getPairingName())
            .build();
      }
    };

    tasks = new ArrayList<>();
    fakeClock = new FakeClock(LocalDate.parse("2019-9-15"));

    BidConfig bidConfig = BidConfig.newBuilder()
        .addFavoriteOvernight("LGA")
        .build();

    solver = new Solver(schedule, tasks, YearMonth.parse("2019-10"),
        bidConfig, tripDatabase, fakeClock);
  }

  @Test
  public void testSelectBestCandidates() throws Exception {
    Set<FlicaTaskWrapper> candidates = new HashSet<>();
    FlicaTaskWrapper lowest = null;
    for (int i = 0; i < Solver.MAX_POWERSET_INPUT_SIZE + 1; ++i) {
      PairingKey key = new PairingKey(LocalDate.parse("2019-10-15"),
          "A1" + String.format("%03d", i));
      FlicaTaskWrapper wrapper = new FlicaTaskWrapper(
          new FlicaTask(key, Period.hours(1)));
      candidates.add(wrapper);
      if (i == 0) {
        lowest = wrapper;
      }
    }
    assertNotNull(lowest);
    assertEquals(31, candidates.size());
    assertTrue(candidates.contains(lowest));
    candidates = solver.selectBestCandidates(candidates);
    assertEquals("Wrong size: " + candidates.size(), 30, candidates.size());
    assertFalse("Contained lowest", candidates.contains(lowest));
  }
}
