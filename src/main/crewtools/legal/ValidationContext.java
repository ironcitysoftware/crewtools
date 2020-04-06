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

import org.joda.time.DateTime;

import crewtools.wx.ParsedMetar;
import crewtools.wx.ParsedTaf;

public class ValidationContext {
  public ParsedTaf departureTaf;
  public ParsedMetar departureMetar;
  public ParsedTaf arrivalTaf;
  public ParsedMetar arrivalMetar;

  public DateTime arrivalEta;

  public String arrivalFaaId;

  public int arrivalRunwayConditionCode = 6;

  public boolean categoryDAircraft = true;
}
