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
      try {
        Thread.sleep(initialDelay.getMillis());
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, prefix + "Error sleeping for initial delay", e);
      }
    }

    doInitialWork();

    while (true) {
      doPeriodicWork();
      
      initialRunComplete.set(true);
      synchronized (initialRunComplete) {
        initialRunComplete.notifyAll();
      }
      
      if (interval.getMillis() > 0) {
        try {
          Thread.sleep(interval.getMillis());
        } catch (InterruptedException e) {
          logger.log(Level.SEVERE, prefix + "Error sleeping for interval", e);
        }
      }
    }
  }

  protected void doInitialWork() {
  }

  protected abstract void doPeriodicWork();
}
