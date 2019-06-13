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

package crewtools.flica.bid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.YearMonth;

import crewtools.flica.Proto;
import crewtools.flica.pojo.PairingKey;

public class ReplayManager {
  private static final Logger logger = Logger.getLogger(ReplayManager.class.getName());

  private final Path BASE_DIR = Paths.get(
      System.getProperty("java.io.tmpdir"));
  private Path REPLAY_DIR;
  private Path OPENTIME_DIR;
  private Path SCHEDULE_DIR;
  private Path PAIRING_DIR;
  private Path REQUEST_STATUS_DIR;
  private Path SWAP_RECORD;

  private long lastOpentimeTimestamp = -1;
  private long lastScheduleTimestamp = -1;
  private long lastRequestStatusTimestamp = -1;

  private final boolean isReplaying;

  public ReplayManager(boolean isReplaying) throws IOException {
    if (isReplaying) {
      REPLAY_DIR = Files
          .list(BASE_DIR)
          .filter(p -> p.getFileName().toString().startsWith("replay-"))
          .sorted(Collections.reverseOrder())
          .findFirst()
          .get();
      logger.info("Replaying from " + REPLAY_DIR);
      SWAP_RECORD = BASE_DIR.resolve("swaps.txt");
    } else {
      REPLAY_DIR = BASE_DIR.resolve("replay-" + System.currentTimeMillis());
      SWAP_RECORD = REPLAY_DIR.resolve("swaps.txt");
    }
    OPENTIME_DIR = REPLAY_DIR.resolve("opentime");
    SCHEDULE_DIR = REPLAY_DIR.resolve("schedule");
    PAIRING_DIR = REPLAY_DIR.resolve("pairings");
    REQUEST_STATUS_DIR = REPLAY_DIR.resolve("request-status");
    if (!isReplaying) {
      try {
        Files.createDirectories(OPENTIME_DIR);
        Files.createDirectories(SCHEDULE_DIR);
        Files.createDirectories(PAIRING_DIR);
        Files.createDirectories(REQUEST_STATUS_DIR);
      } catch (IOException ioe) {
        logger.log(Level.WARNING, "Error creating directories", ioe);
      }
    }
    this.isReplaying = isReplaying;
  }

  public boolean isReplaying() {
    return isReplaying;
  }

  public String getNextOpentime() {
    Path next = getNextPath(OPENTIME_DIR, lastOpentimeTimestamp);
    lastOpentimeTimestamp = getTimestamp(next);
    try {
      logger.info("Replaying opentime from " + next);
      return com.google.common.io.Files.toString(
          next.toFile(),
          StandardCharsets.UTF_8);
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    }
  }

  public void saveOpentimeForReplay(String opentime) {
    saveForReplay(OPENTIME_DIR, opentime);
  }

  public String getNextSchedule() {
    Path next = getNextPath(SCHEDULE_DIR, lastScheduleTimestamp);
    lastScheduleTimestamp = getTimestamp(next);
    try {
      logger.info("Replaying schedule from " + next);
      return com.google.common.io.Files.toString(
          next.toFile(),
          StandardCharsets.UTF_8);
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    }
  }

  public void saveScheduleForReplay(String schedule) {
    saveForReplay(SCHEDULE_DIR, schedule);
  }

  public String getNextRequestStatus() {
    Path next = getNextPath(REQUEST_STATUS_DIR, lastRequestStatusTimestamp);
    lastRequestStatusTimestamp = getTimestamp(next);
    try {
      logger.info("Replaying request status from " + next);
      return com.google.common.io.Files.toString(
          next.toFile(),
          StandardCharsets.UTF_8);
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    }
  }

  public void saveRequestStatusForReplay(String status) {
    saveForReplay(REQUEST_STATUS_DIR, status);
  }

  public Proto.PairingList readPairingList(YearMonth yearMonth) {
    Path input = PAIRING_DIR.resolve("" + yearMonth);
    try {
      return Proto.PairingList.parseFrom(Files.newInputStream(input));
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    }
  }

  public void writePairingList(YearMonth yearMonth, Proto.PairingList pairings) {
    Path output = PAIRING_DIR.resolve("" + yearMonth);
    try {
      pairings.writeTo(Files.newOutputStream(output));
    } catch (IOException ioe) {
      logger.log(Level.WARNING, "Error saving for replay", ioe);
    }
  }

  public String readPairing(PairingKey key) {
    Path input = PAIRING_DIR.resolve("" + key);
    try {
      return com.google.common.io.Files.toString(
          input.toFile(), StandardCharsets.UTF_8);
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    }
  }

  public void writePairing(PairingKey key, String pairing) {
    Path output = PAIRING_DIR.resolve("" + key);
    try {
      Files.newOutputStream(output).write(pairing.getBytes(StandardCharsets.UTF_8));
    } catch (IOException ioe) {
      logger.log(Level.WARNING, "Error saving for replay", ioe);
    }
  }

  public void recordSwap(Transition transition) {
    try {
      Files.write(
          SWAP_RECORD,
          transition.toString().getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.APPEND);
    } catch (IOException ioe) {
      logger.log(Level.WARNING, "Error recording swap", ioe);
    }
  }

  private void saveForReplay(Path path, String text) {
    try {
      Files.write(
          path.resolve("" + System.currentTimeMillis()),
          text.getBytes(StandardCharsets.UTF_8));
    } catch (IOException ioe) {
      logger.log(Level.WARNING, "Error saving for replay", ioe);
    }
  }

  public Path getNextPath(Path path, long lastTimestamp) {
    Predicate<Path> following = p -> getTimestamp(p) > lastTimestamp;
    try {
      Optional<Path> next = Files
          .list(path)
          .filter(following)
          .sorted()
          .findFirst();
      if (!next.isPresent()) {
        logger.warning("Out of data");
        return null;
      }
      return next.get();
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    }
  }

  private static long getTimestamp(Path path) {
    String filename = path.getFileName().toString();
    return Long.parseLong(filename);
  }
}
