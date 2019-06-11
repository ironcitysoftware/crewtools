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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Splitter;

public class AutoBidderCommandLineConfig {
  private static final Splitter EQUALS = Splitter.on('=').trimResults();

  private final boolean cache;
  private final boolean useProto;
  private final boolean debug;
  private final boolean replay;

  public AutoBidderCommandLineConfig(String args[]) {
    Iterator<String> argIterator = Arrays.asList(args).iterator();
    boolean cache = false;
    boolean useProto = false;
    boolean debug = false;
    boolean replay = false;
    while (argIterator.hasNext()) {
      List<String> parameter = EQUALS.splitToList(argIterator.next());
      String arg = parameter.get(0);
      String value = parameter.size() == 1 ? "" : parameter.get(1);
      if (arg.equals("cache")) {
        cache = true;
      } else if (arg.equals("proto")) {
        useProto = true;
      } else if (arg.equals("debug")) {
        debug = true;
      } else if (arg.equals("replay")) {
        replay = true;
      } else {
        System.err.println("Unrecognized argument " + arg);
        System.exit(-1);
      }
    }
    this.cache = cache;
    this.useProto = useProto;
    this.debug = debug;
    this.replay = replay;
  }

  public boolean useCache() {
    return cache;
  }

  public boolean getUseProto() {
    return useProto;
  }

  public boolean isDebug() {
    return debug;
  }

  public boolean isReplay() {
    return replay;
  }
}
