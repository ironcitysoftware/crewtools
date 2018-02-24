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

package crewtools.aa.pojo;

public class Flight {
  String aircraftType;
  boolean allowFSN;
  String arrivalDate;
  String boardingTime;
  String departureDate;
  String destinationAirportCode;
  String destinationCity;
  int flightNumber;
  FlightStatus flightStatus;
  String marketingCarrierCode;
  String marketingCarrierName;
  String miles;  // null
  String operatingCarrierCode;
  String operatingCarrierName;
  String operationalDisclosureText;
  String originAirportCode;
  String originCity;
  String priorLegFlightInfo; // ????
  String refreshTime;
  String seatNo;
  boolean showUpgradeStandbyList;
  String upgradeRequired;
  boolean wifiCarrier;
}
