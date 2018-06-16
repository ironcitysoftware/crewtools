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

package crewtools.flica;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.LocalDate;
import org.joda.time.YearMonth;

import com.google.common.io.Files;

import crewtools.flica.Proto.Rank;

public class CachingFlicaService extends FlicaService {
  private final Logger logger = Logger.getLogger(FlicaService.class.getName());

  private static final File DIR = new File("/tmp/flica/");
  private boolean isConnected = false;

  public CachingFlicaService(FlicaConnection connection) {
    super(connection);
    DIR.mkdir();
  }

  @Override
  public void connect() throws IOException {
    // defer connecting until necessary
  }

  private void connectIfNecessary() throws IOException {
    if (!isConnected) {
      logger.info("Connecting to FLICA");
      super.connect();
      isConnected = true;
    }
  }

  @Override
  public String getBidAward(final AwardDomicile domicile, final Rank rank,
      final int round, final YearMonth yearMonth)
      throws IOException {
    File file = new File(DIR, "get-bid-award-" + domicile + rank + round + yearMonth + ".txt");
    if (file.exists()) {
      return Files.toString(file, StandardCharsets.UTF_8);
    } else {
      connectIfNecessary();
      String result = super.getBidAward(domicile, rank, round, yearMonth);
      Files.write(result, file, StandardCharsets.UTF_8);
      return result;
    }
  }

  @Override
  public String getSchedule(YearMonth yearMonth) throws IOException {
    File file = new File(DIR, "schedule-" + yearMonth + ".txt");
    if (file.exists()) {
      return Files.toString(file, StandardCharsets.UTF_8);
    } else {
      connectIfNecessary();
      String result = super.getSchedule(yearMonth);
      Files.write(result, file, StandardCharsets.UTF_8);
      return result;
    }
  }

  @Override
  public String getAllPairings(AwardDomicile domicile, Rank rank, int round, YearMonth yearMonth)
      throws IOException, URISyntaxException {
    File file = new File(DIR, "all-pairings-" + domicile + rank + round + yearMonth + ".txt");
    if (file.exists()) {
      return Files.toString(file, StandardCharsets.UTF_8);
    } else {
      connectIfNecessary();
      String result = super.getAllPairings(domicile, rank, round, yearMonth);
      Files.write(result, file, StandardCharsets.UTF_8);
      return result;
    }
  }

  @Override
  public String getAllLines(AwardDomicile domicile, Rank rank, 
      int round, YearMonth yearMonth) throws URISyntaxException, IOException {
    File file = new File(DIR, "all-lines-" + domicile + rank + round + yearMonth + ".txt");
    if (file.exists()) {
      return Files.toString(file, StandardCharsets.UTF_8);
    } else {
      connectIfNecessary();
      String result = super.getAllLines(domicile, rank, round, yearMonth);
      Files.write(result, file, StandardCharsets.UTF_8);
      return result;
    }
  }

  @Override
  public String getOpenTime(AwardDomicile domicile, Rank rank, 
      int round, YearMonth yearMonth) throws URISyntaxException, IOException {
    File file = new File(DIR, "opentime-" + domicile + rank + round + yearMonth + ".txt");
    if (file.exists()) {
      return Files.toString(file, StandardCharsets.UTF_8);
    } else {
      connectIfNecessary();
      String result = super.getOpenTime(domicile, rank, round, yearMonth);
      Files.write(result, file, StandardCharsets.UTF_8);
      return result;
    }
  }

  @Override
  public String getPairingDetail(String pairingName, LocalDate date)
      throws URISyntaxException, IOException {
    File file = new File(DIR, "pairing-detail-" + pairingName + date + ".txt");
    if (file.exists()) {
      return Files.toString(file, StandardCharsets.UTF_8);
    } else {
      connectIfNecessary();
      String result = super.getPairingDetail(pairingName, date);
      Files.write(result, file, StandardCharsets.UTF_8);
      return result;
    }
  }

  @Override
  public String getReserveGrid(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth) throws URISyntaxException, IOException {
    File file = new File(DIR, "reserve-grid-" + awardDomicile + rank + round + yearMonth + ".txt");
    if (file.exists()) {
      return Files.toString(file, StandardCharsets.UTF_8);
    } else {
      connectIfNecessary();
      String result = super.getReserveGrid(awardDomicile, rank, round, yearMonth);
      Files.write(result, file, StandardCharsets.UTF_8);
      return result;
    }
  }
  
  @Override
  public String submitLineBid(int round, YearMonth yearMonth,
      List<String> lines) throws URISyntaxException, IOException {
    connectIfNecessary();
    return super.submitLineBid(round, yearMonth, lines);
  }

  @Override
  public String getOpentimeRequests(int round, YearMonth yearMonth)
      throws URISyntaxException, IOException {
    File file = new File(DIR, "swap-requests-" + round + yearMonth + ".txt");
    if (file.exists()) {
      return Files.toString(file, StandardCharsets.UTF_8);
    } else {
      connectIfNecessary();
      String result = super.getOpentimeRequests(round, yearMonth);
      Files.write(result, file, StandardCharsets.UTF_8);
      return result;
    }
  }
  
  @Override
  public byte[] getDocument(AwardDomicile awardDomicile, Rank rank,
      int round, YearMonth yearMonth,
      int documentId, String title)
      throws URISyntaxException, IOException {
    File file = new File(DIR, "document-" + awardDomicile + rank + 
        round + yearMonth + documentId + title + ".txt");
    if (file.exists()) {
      return Files.toByteArray(file);
    } else {
      connectIfNecessary();
      byte result[] = super.getDocument(awardDomicile, rank, round, yearMonth,
          documentId, title);
      Files.write(result, file);
      return result;
    }
  }
}
