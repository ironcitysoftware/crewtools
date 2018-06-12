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

import static org.junit.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import crewtools.flica.parser.ParseException;
import crewtools.flica.pojo.PairingKey;
import crewtools.rpc.Proto.BidConfig;
import crewtools.test.FakeClock;
import crewtools.test.ScheduleBuilder;
import crewtools.test.TripBuilder;
import crewtools.util.Clock;

public class ScheduleWrapperTreeTest {
  private static final Clock FAKE_CLOCK =
      new FakeClock(TripBuilder.DEFAULT_DAY.minusDays(1));

  private LocalDate uniquePairingKeyDate = TripBuilder.DEFAULT_DAY;
  
  private class Adder extends ScheduleWrapperTree.Visitor {
    private final ScheduleWrapper from;
    private final ScheduleWrapper to;
    
    Adder(ScheduleWrapperTree tree, ScheduleWrapper from, ScheduleWrapper to) {
      tree.super();
      this.from = from;
      this.to = to;
    }
    
    public void visit(ScheduleWrapper wrapper) {
      if (from != null && !from.equals(wrapper)) {
        return;
      }
      Transition transition = new Transition(
          ImmutableList.of(new PairingKey(uniquePairingKeyDate, "LFrom")),
          ImmutableList.of(new PairingKey(uniquePairingKeyDate.plusDays(1), "LTo")));
      uniquePairingKeyDate = uniquePairingKeyDate.plusDays(2);
      add(wrapper, transition, to);
    }
  }
  
  private ScheduleWrapper newScheduleWrapper(String name) {
    return new ScheduleWrapper(
        new ScheduleBuilder().build(name),
        TripBuilder.DEFAULT_YEAR_MONTH,
        FAKE_CLOCK, BidConfig.getDefaultInstance());
  }

  @Test
  public void testReRootSameSchedule() throws ParseException {
    ScheduleWrapperTree tree = new ScheduleWrapperTree();
    ScheduleWrapper wrapper = newScheduleWrapper("test");
    tree.setRootScheduleWrapper(wrapper);
    int oldHash = tree.hashCode();
    tree.setRootScheduleWrapper(wrapper);
    assertEquals(oldHash, tree.hashCode());
  }
  
  @Test
  public void testSingleLevelReRoot() throws ParseException {
    ScheduleWrapperTree tree = new ScheduleWrapperTree();
    ScheduleWrapper oldRoot = newScheduleWrapper("oldRoot");
    tree.setRootScheduleWrapper(oldRoot);
    ScheduleWrapper left = newScheduleWrapper("leftChild");
    ScheduleWrapper right = newScheduleWrapper("rightChild");
    tree.visit(new Adder(tree, oldRoot, left));
    tree.visit(new Adder(tree, oldRoot, right));

    tree.setRootScheduleWrapper(left);

    ScheduleWrapperTree expectedTree = new ScheduleWrapperTree();
    expectedTree.setRootScheduleWrapper(left);
    assertEquals(expectedTree, tree);
  }

  @Test
  public void testMultiLevelReRoot() throws ParseException {
    ScheduleWrapperTree tree = new ScheduleWrapperTree();
    ScheduleWrapper oldRoot = newScheduleWrapper("oldRoot");
    tree.setRootScheduleWrapper(oldRoot);
    ScheduleWrapper left = newScheduleWrapper("leftChild");
    ScheduleWrapper right = newScheduleWrapper("rightChild");
    tree.visit(new Adder(tree, oldRoot, left));
    tree.visit(new Adder(tree, oldRoot, right));

    ScheduleWrapper leftleft = newScheduleWrapper("leftleftGrandchild");
    ScheduleWrapper leftright = newScheduleWrapper("leftrightGrandhild");
    tree.visit(new Adder(tree, left, leftleft));
    tree.visit(new Adder(tree, left, leftright));

    ScheduleWrapper rightleft = newScheduleWrapper("rightleftGrandchild");
    ScheduleWrapper rightright = newScheduleWrapper("rightrightGrandhild");
    tree.visit(new Adder(tree, right, rightleft));
    tree.visit(new Adder(tree, right, rightright));

    tree.setRootScheduleWrapper(right);

    ScheduleWrapperTree expectedTree = new ScheduleWrapperTree();
    expectedTree.setRootScheduleWrapper(right);
    expectedTree.visit(new Adder(expectedTree, right, rightleft));
    expectedTree.visit(new Adder(expectedTree, right, rightright));
    assertEquals(expectedTree, tree);
  }
}
