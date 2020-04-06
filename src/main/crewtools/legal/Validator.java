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

package crewtools.legal;

import java.io.IOException;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;

import com.google.common.collect.ImmutableSet;

import crewtools.airport.Proto.Airport;
import crewtools.airport.Proto.Approach;
import crewtools.util.AirportDatabase;
import crewtools.wx.ParsedMetar;
import crewtools.wx.ParsedTaf;
import crewtools.wx.ParsedTaf.TafPeriod;
import crewtools.wx.Visibility;
import crewtools.wx.VisibilityComparator;
import crewtools.wx.WeatherVisitor;
import crewtools.wx.Wind;
import crewtools.wx.WindCalculator;

public class Validator {
  // TODO: document that this assumption is true.
  private static final Period ASSUMED_METAR_VALIDITY_PERIOD = Period.hours(24);

  private final AirportDatabase airportDatabase;
  private final ValidationContext context;
  private final Airport arrivalAirport;
  private final Result result = new Result();
  private final WindCalculator windCalculator = new WindCalculator();

  public Validator(ValidationContext context) throws IOException {
    this.airportDatabase = new AirportDatabase();
    this.context = context;
    this.arrivalAirport = airportDatabase.getAirport(context.arrivalFaaId);
  }

  public void validate() throws Exception {
    checkLegalToDispatch();
    checkLegalToTakeoff();
  }

  public Result getResult() {
    return result;
  }

  private void checkLegalToDispatch() {
    // at destination ETA, METAR/TAF or any combination must
    // show wx at or above ldg mins.

    // Need reference: consider only visibility
    // Need reference: METAR only applies if ETA is within 1 hour of issue.

    WindVisitor wv = new WindVisitor();
    visitWeather(context.arrivalMetar, context.arrivalTaf, context.arrivalEta, wv);
    Set<Wind> winds = wv.getWinds();

    Set<Visibility> suitableApproachMinimums = getSuitableApproachMinimums(winds);
    if (suitableApproachMinimums.isEmpty()) {
      result.addError("No suitable arrival approach minimums");
      return;
    }
    VisibilityVisitor vv = new VisibilityVisitor();
    visitWeather(context.arrivalMetar, context.arrivalTaf, context.arrivalEta, vv);
    Set<Visibility> visibilities = vv.getVisibilities();
    if (visibilities.isEmpty()) {
      result.addError("No suitable arrival obervation or forecasts");
      return;
    }

    VisibilityComparator visibilityComparator = new VisibilityComparator();

    Visibility lowestApproachVisibility = suitableApproachMinimums
        .stream()
        .min(visibilityComparator)
        .get();

    Visibility lowestReportedVisibility = visibilities
        .stream()
        .min(visibilityComparator)
        .get();

    if (visibilityComparator.compare(lowestReportedVisibility,
        lowestApproachVisibility) < 0) {
      result.addError(String.format(
          "lowest reported visibility %s is lower than lowest approach visibility %s",
          lowestReportedVisibility, lowestApproachVisibility));
      return;
    }

    // cat c vs d
    // 200 needs vis and ceiling due to flap AD
    // consider visibility only
    // only approaches to rwys of suitable l/w w/ suitable wind.
    // gusts are not limiting unless rcc <= 4
    // can consider cat ii
    // if conditional drops 1/2 of cat i vis but main body is legal, exemption 17347
  }

  private Set<Visibility> getSuitableApproachMinimums(Set<Wind> winds) {
    ImmutableSet.Builder<Visibility> builder = ImmutableSet.builder();
    for (Approach approach : arrivalAirport.getApproachList()) {
      if (!approach.hasRunway()) {
        addApproachVisibility(approach, context.categoryDAircraft, builder);
      } else {
        if (isRunwaySuitable(approach.getRunway(), arrivalAirport.getVariation(), winds,
            context.arrivalRunwayConditionCode)) {
          addApproachVisibility(approach, context.categoryDAircraft, builder);
        }
      }
    }
    return builder.build();
  }

  private boolean isRunwaySuitable(int runwayNumber, String variation, Set<Wind> winds,
      int conditionCode) {
    // POH 3.12.3
    if (conditionCode == 0) {
      result.addFact(
          String.format("Excluding runway %02d due to condition code 0", runwayNumber));
      return false;
    }
    // if any wind exceeds a limit, the runway is not suitable.
    // TODO: variation
    // TODO: collect measurements into worst xwind, worst tailwind, etc.?
    WindCalculator.Result worstSteadyWind = null;
    WindCalculator.Result worstGustyWind = null;
    for (Wind wind : winds) {
      WindCalculator.Result steadyWind = windCalculator.calculateExcludingGusts(wind,
          runwayNumber);
      worstSteadyWind = steadyWind.maximize(worstSteadyWind);
      WindCalculator.Result gustyWind = windCalculator.calculateExcludingGusts(wind,
          runwayNumber);
      worstGustyWind = gustyWind.maximize(worstGustyWind);
    }
    result.addFact(
        String.format("Runway %02d worst steady wind %s", runwayNumber, worstSteadyWind));
    result.addFact(
        String.format("Runway %02d worst gusty wind %s", runwayNumber, worstGustyWind));
    switch (conditionCode) {
      case 6:
      case 5:
        return worstSteadyWind.getCrosswindVelocity() <= 27 &&
            worstSteadyWind.getHeadwindVelocity() >= -10;
      case 4:
        return worstGustyWind.getCrosswindVelocity() <= 27 &&
            worstGustyWind.getHeadwindVelocity() >= -10;
      case 3:
        return worstGustyWind.getCrosswindVelocity() <= 15 &&
            worstGustyWind.getHeadwindVelocity() >= -5;
      case 2:
        return worstGustyWind.getCrosswindVelocity() <= 10 &&
            worstGustyWind.getHeadwindVelocity() >= -5;
      case 1:
        return worstGustyWind.getCrosswindVelocity() <= 10 &&
            worstGustyWind.getHeadwindVelocity() >= 0;
      default:
        throw new IllegalArgumentException("Unknown RCC " + conditionCode);
    }

    // TODO: CAT II prohibited when braking action less than medium
    // TODO: CAT II limitations
  }

  private void addApproachVisibility(Approach approach,
      boolean categoryD, ImmutableSet.Builder<Visibility> builder) {
    if (approach.hasVisibility()) {
      builder.add(Visibility.fromProto(approach.getVisibility()));
    } else if (categoryD && approach.hasDVisibility()) {
      builder.add(Visibility.fromProto(approach.getDVisibility()));
    } else if (!categoryD && approach.hasCVisibility()) {
      builder.add(Visibility.fromProto(approach.getCVisibility()));
    }
  }

  private void visitWeather(ParsedMetar metar, ParsedTaf taf,
      DateTime eta, WeatherVisitor visitor) {
    Interval metarValidity = new Interval(metar.issued,
        metar.issued.plus(ASSUMED_METAR_VALIDITY_PERIOD));
    if (metarValidity.contains(eta)) {
      visitor.visit(metar);
    }
    for (TafPeriod period : taf.getTafPeriodsAt(eta)) {
      visitor.visit(period, taf.getConditionsFor(period));
    }
  }

  public class VisibilityVisitor implements WeatherVisitor {
    private ImmutableSet.Builder<Visibility> builder = ImmutableSet.builder();

    @Override
    public void visit(ParsedMetar metar) {
      addMetarVisibilities(metar, "arrival METAR", builder);
    }

    @Override
    public void visit(TafPeriod period, ParsedMetar forecast) {
      addMetarVisibilities(forecast, "arrival TAF " + period, builder);
    }

    private void addMetarVisibilities(ParsedMetar metar, String description,
        ImmutableSet.Builder<Visibility> builder) {
      result.addFact(
          String.format("Adding %s visibility %s", description, metar.visibility));
      builder.add(metar.visibility);
      if (metar.rvr != null) {
        result.addFact(String.format("Adding %s rvr %s", description, metar.rvr));
        builder.add(metar.rvr);
      }
    }

    public Set<Visibility> getVisibilities() {
      return builder.build();
    }
  }

  public class WindVisitor implements WeatherVisitor {
    private ImmutableSet.Builder<Wind> builder = ImmutableSet.builder();

    @Override
    public void visit(ParsedMetar metar) {
      if (metar.wind != null) {
        result.addFact(String.format("Adding arrival METAR wind %s", metar.wind));
        builder.add(metar.wind);
      }
    }

    @Override
    public void visit(TafPeriod period, ParsedMetar forecast) {
      if (forecast.wind != null) {
        result.addFact(
            String.format("Adding arrival TAF wind %s at %s", forecast.wind, period));
        builder.add(forecast.wind);
      }
    }

    public Set<Wind> getWinds() {
      return builder.build();
    }
  }

  private void checkLegalToTakeoff() {

    // Legal for takeoff?
    // Meet or exceed takeoff mins - highest of FOM or specific runway mins
    // 10-9/10-9A
    // FOM 10.4.2
    // Far end RVR advisory only
    // See Takeoff Legaltiy table 10.4.3
    // Gusts are limiting rcc <= 4

  }
}
