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

package crewtools.flica.stats;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import crewtools.flica.AwardDomicile;
import crewtools.flica.Proto;
import crewtools.flica.Proto.PairingList;
import crewtools.flica.adapters.PairingAdapter;
import crewtools.flica.pojo.Trip;
import crewtools.rpc.Proto.Flight;
import crewtools.rpc.Proto.FlightList;
import crewtools.rpc.Proto.FlightListFile;

public class Commutability {
  private final DataReader dataReader;
  private final YearMonth yearMonth;

  public static void main(String args[]) throws Exception {
    DataReader dataReader = new DataReader();
    YearMonth yearMonth = YearMonth.parse(args[0]);
    new Commutability(dataReader, yearMonth).run();
  }

  public Commutability(DataReader dataReader, YearMonth yearMonth) {
    this.dataReader = dataReader;
    this.yearMonth = yearMonth;
  }

  private static class Stat {
    int commutableBoth = 0;
    int commutableStart = 0;
    int commutableEnd = 0;
    int notCommutable = 0;

    public int getTotal() {
      return commutableBoth + commutableStart + commutableEnd + notCommutable;
    }

    public String toString() {
      float total = getTotal();
      String result = String.format("  Both %03d (%03.0f%%)\n", commutableBoth,
          commutableBoth * 100 / total);
      result += String.format("  Strt %03d (%03.0f%%)\n", commutableStart,
          commutableStart * 100 / total);
      result += String.format("  End  %03d (%03.0f%%)\n", commutableEnd,
          commutableEnd * 100 / total);
      result += String.format("  Sad  %03d (%03.0f%%)\n", notCommutable,
          notCommutable * 100 / total);
      return result;
    }
  }

  public void run() throws Exception {
    Map<LocalDate, LocalTime> earliestLegalStart = new HashMap<>();
    Map<LocalDate, LocalTime> lastFeasibleEnd = new HashMap<>();
    FlightListFile flightListFile = dataReader.readTimetable(yearMonth);
    Files.write(flightListFile.toString(), new File("/tmp/timetable.txt"),
        StandardCharsets.UTF_8);
    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      Stat stat = new Stat();
      populateMaps(flightListFile, awardDomicile, earliestLegalStart, lastFeasibleEnd);
      process(awardDomicile, earliestLegalStart, lastFeasibleEnd, stat);
      if (stat.getTotal() > 0) {
        System.out.println(awardDomicile + ":\n" + stat);
      }
    }
  }

  private DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();

  // TODO: customize by adding local airport to this list.
  private Set<String> HOME = ImmutableSet.of("CLT");

  private void populateMaps(FlightListFile file, AwardDomicile domicile,
      Map<LocalDate, LocalTime> start, Map<LocalDate, LocalTime> end) {
    start.clear();
    end.clear();
    for (FlightList flightList : file.getFlightListList()) {
      LocalDate date = LocalDate.parse(flightList.getDate());
      if (HOME.contains(flightList.getOrigin()) &&
        // commute to work
          flightList.getDestination().equals(domicile.name())) {
        List<LocalTime> arrivalTimes = new ArrayList<>();
        for (Flight flight : flightList.getFlightList()) {
          DateTime arrival = parser.parseDateTime(flight.getArrivalTimestamp());
          arrivalTimes.add(arrival.toLocalTime());
        }
        Collections.sort(arrivalTimes);
        // Earliest possible show
        if (arrivalTimes.size() > 1) {
          LocalTime earliestArrival = arrivalTimes.get(1);
          if (!start.containsKey(date) || start.get(date).isAfter(earliestArrival)) {
            start.put(date, earliestArrival);
          }
        }
      } else if (flightList.getOrigin().equals(domicile.name())
          && HOME.contains(flightList.getDestination())) {
        // commute from work
        List<LocalTime> departureTimes = new ArrayList<>();
        for (Flight flight : flightList.getFlightList()) {
          DateTime departure = parser.parseDateTime(flight.getDepartureTimestamp());
          departureTimes.add(departure.toLocalTime());
        }
        Collections.sort(departureTimes);
        // Last possible arrival time
        LocalTime latestDeparture = departureTimes.get(departureTimes.size() - 1);
        if (!end.containsKey(date) || end.get(date).isBefore(latestDeparture)) {
          end.put(date, latestDeparture);
        }
      }
    }
  }

  private void process(AwardDomicile awardDomicile,
      Map<LocalDate, LocalTime> start,
      Map<LocalDate, LocalTime> end,
      Stat stat) throws FileNotFoundException, IOException {
    if (awardDomicile.name().equals("CLT")) {
      return;
    }
    PairingList pairings = dataReader.readPairings(yearMonth, awardDomicile);
    PairingAdapter adapter = new PairingAdapter();
    for (Proto.Trip protoTrip : pairings.getTripList()) {
      Trip trip = adapter.adaptTrip(protoTrip);
      LocalDate startDate = trip.getDutyStart().toLocalDate();
      LocalTime startTime = trip.getDutyStart().toLocalTime();
      Preconditions.checkState(start.containsKey(startDate),
          "No start time for " + startDate + " for " + awardDomicile);
      // Realistically 15 minutes to deplane.
      LocalTime realisticStart = start.get(startDate).plusMinutes(15);
      boolean startCommutable = !realisticStart.isAfter(startTime);

      DateTime endPlusFifteen = trip.getLastSection().getEnd();
      DateTime realEnd = trip.getLastSection().getLegs().get(
          trip.getLastSection().getNumLegs() - 1).getArrivalTime();
      Preconditions.checkState(realEnd.plusMinutes(15).equals(endPlusFifteen));
      LocalDate endDate = realEnd.toLocalDate();
      LocalTime endTime = realEnd.toLocalTime();
      Preconditions.checkState(end.containsKey(endDate),
          "No end time for " + startDate + " for " + awardDomicile);
      // Realistically 23 minutes to get folks off and arrive 10 prior.
      LocalTime realisticEnd = end.get(endDate).minusMinutes(23);

      boolean endCommutable = !realisticEnd.isBefore(endTime);
      // System.out.println(trip.getPairingName()
      // + " start " + startTime
      // + "/comm " + start.get(startDate)
      // + "; end " + endTime
      // + "/comm " + end.get(endDate)
      // + "; START=" + startCommutable
      // + " END=" + endCommutable);
      if (startCommutable && endCommutable) {
        stat.commutableBoth++;
      } else if (startCommutable && !endCommutable) {
        stat.commutableStart++;
      } else if (!startCommutable && endCommutable) {
        stat.commutableEnd++;
      } else if (!startCommutable && !endCommutable) {
        stat.notCommutable++;
      } else {
        throw new IllegalStateException("Laws of physics are against you");
      }
    }
  }
}
