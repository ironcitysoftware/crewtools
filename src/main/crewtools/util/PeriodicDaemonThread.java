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

package crewtools.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.joda.time.format.PeriodFormatter;

public abstract class PeriodicDaemonThread extends Thread {
  private final Logger logger = Logger.getLogger(PeriodicDaemonThread.class.getName());

  private static final Duration FAILURE_DURATION = Duration.standardSeconds(10);

  protected final Duration initialDelay;
  protected Duration interval;

  public enum WorkResult {
    COMPLETE,
    INCOMPLETE
  }

  public PeriodicDaemonThread(Duration initialDelay, Duration interval) {
    this.initialDelay = initialDelay;
    this.interval = interval;
  }

  @Override
  public void run() {
    String prefix = String.format("[%s]", getName());
    logger.info(String.format("%s initial:%s  interval:%s  failure:%s",
        prefix,
        prettyPrint(initialDelay),
        prettyPrint(interval),
        prettyPrint(FAILURE_DURATION)));

    if (initialDelay.getMillis() > 0) {
      safeSleep(prefix, initialDelay);
    }

    doInitialWork();

    int numFailures = 0;

    while (true) {
      if (doPeriodicWork() == WorkResult.INCOMPLETE) {
        safeSleep(prefix, FAILURE_DURATION);
        numFailures++;
        if (numFailures < getMaximumNumFailuresBeforeSleeping()) {
          continue;
        }
      }

      numFailures = 0;

      safeSleep(prefix, interval);
    }
  }

  private static final PeriodFormatter PERIOD_FORMAT = PeriodFormat.getDefault();

  private String prettyPrint(Duration duration) {
    return PERIOD_FORMAT.print(new org.joda.time.Period(duration.getMillis()))
        .replace(' ', '_');
  }

  private void safeSleep(String prefix, Duration sleepDuration) {
    if (sleepDuration.getMillis() > 0) {
      try {
        Thread.sleep(sleepDuration.getMillis());
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, prefix + "Error sleeping", e);
      }
    }
  }

  protected void doInitialWork() {
  }

  protected int getMaximumNumFailuresBeforeSleeping() {
    return 1;
  }

  /**
   * Returns COMPLETE if the work succeeded.
   * If INCOMPLETE is returned, the thread will sleep FAILURE_INTERVAL and retry.
   */
  protected abstract WorkResult doPeriodicWork();
}
