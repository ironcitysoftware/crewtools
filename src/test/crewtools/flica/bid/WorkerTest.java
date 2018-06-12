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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.http.client.ClientProtocolException;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

import crewtools.flica.FlicaService;
import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.Schedule;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.BidConfig;
import crewtools.test.FakeClock;
import crewtools.test.ScheduleBuilder;
import crewtools.test.TripBuilder;
import crewtools.util.Clock;
import crewtools.util.Period;

public class WorkerTest {  
  private final Trip firstTrip;
  private final Trip secondTrip;
  private final Trip thirdTrip;
  private final Schedule schedule;
  private final ArrayBlockingQueue<Trip> queue;
  private final FlicaService service;
  private final ScheduleWrapperTree tree;
  private final ScheduleWrapper wrapper;
  private final Worker worker;
  private final RuntimeStats stats;

  private static final int ROUND = 10;
  
  private static final Clock FAKE_CLOCK =
      new FakeClock(TripBuilder.DEFAULT_DAY.minusDays(1));
  
  public WorkerTest() {
    firstTrip = new TripBuilder()
        .withDayOfMonth(1)
        .withLeg("CLT", "SFO", Period.hours(12))
        .withLayover("SFO", Period.hours(12))
        .withLeg("SFO", "CLT", Period.hours(10))
        .build();
    secondTrip = new TripBuilder()
        .withDayOfMonth(10)
        .withLeg("CLT", "TYS", Period.hours(12))
        .withLayover("TYS", Period.hours(12))
        .withLeg("TYS", "CLT", Period.hours(10))
        .build();
    thirdTrip = new TripBuilder()
        .withDayOfMonth(20)
        .withLeg("CLT", "DCA", Period.hours(12))
        .withLayover("DCA", Period.hours(12))
        .withLeg("DCA", "CLT", Period.hours(10))
        .build();
    schedule = new ScheduleBuilder().withTrips(firstTrip, secondTrip, thirdTrip).build();
    queue = new ArrayBlockingQueue<>(5);
    service = Mockito.mock(FlicaService.class);
    tree = new ScheduleWrapperTree();
    stats = new RuntimeStats(FAKE_CLOCK, tree);
    wrapper = new ScheduleWrapper(
        schedule,
        TripBuilder.DEFAULT_YEAR_MONTH,
        FAKE_CLOCK,
        BidConfig.getDefaultInstance());
    tree.setRootScheduleWrapper(wrapper);
    worker = new Worker(queue, service, tree, TripBuilder.DEFAULT_YEAR_MONTH, ROUND,
        FAKE_CLOCK, stats,
        BidConfig.newBuilder().addFavoriteOvernight("SFO").build(), false);
  }
  
  @Test
  public void testWorseTripNotSwapped() throws ParseException {
    Trip worseTrip = new TripBuilder()
        .withName("LWorse")
        .withDayOfMonth(21)
        .withLeg("CLT", "DCA", Period.hours(4))
        .withLeg("DCA", "CLT", Period.hours(4))
        .withLeg("CLT", "LYH", Period.hours(4))
        .withLayover("LYH", Period.hours(12))
        .withLeg("CLT", "CLT", Period.hours(10))
        .build();
    tree.setRootScheduleWrapper(wrapper);
    queue.add(worseTrip);
    worker.doWork();
    verifyZeroInteractions(service);
  }
  
  @Test
  public void testBetterTripSwapped() throws ParseException, ClientProtocolException, URISyntaxException, IOException {
    Trip betterTrip = new TripBuilder()
        .withName("LBetter")
        .withDayOfMonth(20)
        .withLeg("CLT", "SFO", Period.hours(12))
        .withLayover("SFO", Period.hours(12))
        .withLeg("SFO", "CLT", Period.hours(10))
        .build();
    queue.add(betterTrip);
    worker.doWork();
    verify(service).submitSwap(
        ROUND, 
        TripBuilder.DEFAULT_YEAR_MONTH,
        FAKE_CLOCK.today(),
        ImmutableList.of(betterTrip.getPairingKey()),
        ImmutableList.of(thirdTrip.getPairingKey()));
  }

  @Test
  public void testScheduleUpdate() throws ParseException, ClientProtocolException, URISyntaxException, IOException {
    Trip betterTrip = new TripBuilder()
        .withName("LBetter")
        .withDayOfMonth(20)
        .withLeg("CLT", "SFO", Period.hours(12))
        .withLayover("SFO", Period.hours(12))
        .withLeg("SFO", "CLT", Period.hours(10))
        .build();
    queue.add(betterTrip);
    worker.doWork();
    
    Schedule newSchedule = new ScheduleBuilder().withTrips(firstTrip, secondTrip, betterTrip).build();
    ScheduleWrapper newWrapper = new ScheduleWrapper(
        newSchedule,
        TripBuilder.DEFAULT_YEAR_MONTH,
        FAKE_CLOCK,
        BidConfig.getDefaultInstance());
    tree.setRootScheduleWrapper(newWrapper);
    
    verify(service).submitSwap(
        ROUND, 
        TripBuilder.DEFAULT_YEAR_MONTH,
        FAKE_CLOCK.today(),
        ImmutableList.of(betterTrip.getPairingKey()),
        ImmutableList.of(thirdTrip.getPairingKey()));
    
    ScheduleWrapperTree expectedTree = new ScheduleWrapperTree();
    expectedTree.setRootScheduleWrapper(newWrapper);
    assertEquals(expectedTree, tree);
  }
}
