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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import crewtools.flica.Proto.Equipment;
import crewtools.flica.Proto.Trip;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.parser.ParseException;
import crewtools.rpc.Proto.BidConfig;
import crewtools.util.Period;

public class TripScoreTest {
  
  private final Trip SAMPLE_TRIP;
  
  public TripScoreTest() {
    Trip.Builder builder = Trip.newBuilder();
    builder.setStartDate("2018-01-01");
    builder.addSectionBuilder()
        .setLayoverAirportCode("SFO")
        .setLayoverDuration("0100")
        .setLocalDutyStartDate("2018-01-01")
        .setLocalDutyStartTime("0800")
        .setLocalDutyEndTime("0830")
        .addLegBuilder()
            .setDayOfMonth(1)
            .setBlockDuration("0015")
            .setDepartureLocalTime("0800")
            .setArrivalLocalTime("0815");
    this.SAMPLE_TRIP = builder.build();
  }
      
  @Test
  public void testFavoriteOvernightPeriodAndNum() throws ParseException {
    Trip.Builder trip = Trip.newBuilder(SAMPLE_TRIP);
    TripScore score = new TripScore(new PairingAdapter().adaptTrip(trip.build()),
        BidConfig.newBuilder().addFavoriteOvernight("SFO").build());
    assertEquals(Period.hours(1), score.getFavoriteOvernightPeriod());
    assertEquals(1, score.getNumFavoriteOvernights());
  }

  @Test
  public void testStartTimePoints() throws ParseException {
    Trip.Builder trip = Trip.newBuilder(SAMPLE_TRIP);
    TripScore score = new TripScore(new PairingAdapter().adaptTrip(trip.build()),
        BidConfig.getDefaultInstance());
    assertEquals(0, score.getStartTimePoints());
    
    trip.getSectionBuilder(0).setLocalDutyStartTime("1000");
    score = new TripScore(new PairingAdapter().adaptTrip(trip.build()),
        BidConfig.getDefaultInstance());
    assertEquals(1, score.getStartTimePoints());
  }
  
  @Test
  public void testEndTimePoints() throws ParseException {
    Trip.Builder trip = Trip.newBuilder(SAMPLE_TRIP);
    trip.getSectionBuilder(0).setLocalDutyEndTime("2330");
    TripScore score = new TripScore(new PairingAdapter().adaptTrip(trip.build()),
        BidConfig.getDefaultInstance());
    assertEquals(0, score.getEndTimePoints());
    
    trip.getSectionBuilder(0).setLocalDutyEndTime("0830");
    score = new TripScore(new PairingAdapter().adaptTrip(trip.build()),
        BidConfig.getDefaultInstance());
    assertEquals(1, score.getEndTimePoints());
  }
  
  @Test
  public void testNumLegs() throws ParseException {
    Trip.Builder trip = Trip.newBuilder(SAMPLE_TRIP);
    TripScore score = new TripScore(new PairingAdapter().adaptTrip(trip.build()),
        BidConfig.getDefaultInstance());
    assertEquals(1, score.getNumLegs());
  }
  
  @Test
  public void testEquipment() throws ParseException {
    Trip.Builder trip = Trip.newBuilder(SAMPLE_TRIP);
    TripScore score = new TripScore(new PairingAdapter().adaptTrip(trip.build()),
        BidConfig.getDefaultInstance());
    assertFalse(score.hasEquipmentTwoHundredSegments());
    
    trip.getSectionBuilder(0).getLegBuilder(0).setEquipment(Equipment.RJ2);
    score = new TripScore(new PairingAdapter().adaptTrip(trip.build()),
        BidConfig.getDefaultInstance());
    assertTrue(score.hasEquipmentTwoHundredSegments());
  }
}
