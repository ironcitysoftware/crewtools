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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.Duration;

public abstract class PeriodicDaemonThread extends Thread {
  private final Logger logger = Logger.getLogger(PeriodicDaemonThread.class.getName());

  private static final Duration FAILURE_DURATION = Duration.standardSeconds(10);

  private final Duration initialDelay;
  private final Duration interval;
  private final AtomicBoolean initialRunComplete;
  
  public PeriodicDaemonThread(Duration initialDelay, Duration interval) {
    this.initialDelay = initialDelay;
    this.interval = interval;
    this.initialRunComplete = new AtomicBoolean(false);
  }
  
  public void blockCurrentThreadUntilInitialRunIsComplete() {
    if (initialRunComplete.get()) {
      return;
    }
    synchronized (initialRunComplete) {
      try {
        initialRunComplete.wait();
      } catch (InterruptedException e) {
        String prefix = String.format("[%s] ", getName());
        logger.log(Level.WARNING, prefix + "Unable to wait for initial run", e);
      }
    }
  }
  
  @Override
  public void run() {
    String prefix = String.format("[%s] ", getName());
    if (initialDelay.getMillis() > 0) {
      logger.info(prefix + "Waiting " + initialDelay.toString() + " initially");
      safeSleep(prefix, initialDelay);
    }

    doInitialWork();

    int numFailures = 0;

    while (true) {
      if (doPeriodicWork()) {
        safeSleep(prefix, FAILURE_DURATION);
        numFailures++;
        if (numFailures < getMaximumNumFailuresBeforeSleeping()) {
          continue;
        }
      }
      
      numFailures = 0;

      initialRunComplete.set(true);
      synchronized (initialRunComplete) {
        initialRunComplete.notifyAll();
      }
      
      safeSleep(prefix, interval);
    }
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
   * Returns true if the work succeeded.
   * If false is returned, the thread will sleep FAILURE_INTERVAL and retry.
   */
  protected abstract boolean doPeriodicWork();
}
