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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import crewtools.flica.pojo.FlicaTask;
import crewtools.flica.pojo.Schedule;

public class Collector {
  private final Logger logger = Logger.getLogger(Collector.class.getName());

  private Schedule schedule;
  private Set<FlicaTask> tasks;

  private Schedule currentSchedule;
  private Set<FlicaTask> currentTasks;

  public synchronized void offer(Schedule schedule) {
    this.schedule = schedule;
    this.notify();
  }

  public synchronized void offer(Set<FlicaTask> tasks) {
    this.tasks = tasks;
    this.notify();
  }

  public synchronized void beginWork() {
    while (schedule == null || tasks == null || tasks.size() == 0) {
      try {
        this.wait();
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, "interrupt", e);
      }
    }
    currentSchedule = schedule;
    currentTasks = new HashSet<>(tasks);
  }

  public Schedule getCurrentSchedule() {
    return currentSchedule;
  }

  public Set<FlicaTask> getCurrentTasks() {
    return currentTasks;
  }
}
