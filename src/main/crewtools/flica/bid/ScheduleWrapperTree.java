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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;

import crewtools.flica.pojo.PairingKey;
import crewtools.rpc.Proto.BidConfig;
import crewtools.rpc.Proto.ScheduleNode;
import crewtools.rpc.Proto.Status;

// DAG where each node represents a schedule state and each 
// line represents a swap request.
public class ScheduleWrapperTree {
  private final Logger logger = Logger.getLogger(ScheduleWrapperTree.class.getName());

  private final SetMultimap<ScheduleWrapper, ScheduleWrapper> nodes =
      MultimapBuilder.hashKeys().hashSetValues().build();
  
  private final Map<Transition, ScheduleWrapper> transitions = new HashMap<>();
  
  private ScheduleWrapper root;
  private final Set<PairingKey> processedKeys = new HashSet<>();
  private final BidConfig bidConfig;

  public ScheduleWrapperTree(BidConfig bidConfig) {
    this.bidConfig = bidConfig;
  }
  
  public abstract class Visitor {
    public abstract void visit(ScheduleWrapper wrapper);
    public void add(ScheduleWrapper wrapper, Transition transition, ScheduleWrapper newChild) {
      nodes.put(wrapper, newChild);
      Preconditions.checkState(!transitions.containsKey(transition));
      transitions.put(transition, newChild);
    }
  }
  
  /**
   * Keeps track of processed keys here, rather than in worker,
   * because we want to clear this list when we reroot the tree.
   */  
  public synchronized boolean shouldProcess(PairingKey key) {
    return !processedKeys.contains(key);
  }
  
  public synchronized void markProcessed(PairingKey key) {
    processedKeys.add(key);
  }
  
  public synchronized void visit(Visitor visitor) {
    if (root == null) {
      logger.warning("No nodes to visit");
    } else {
      visitNodeAndChildren(visitor, root);
    }
  }
  
  private void visitNodeAndChildren(Visitor visitor, ScheduleWrapper startingNode) {
    visitor.visit(startingNode);
    for (ScheduleWrapper node : nodes.get(startingNode)) {
      visitNodeAndChildren(visitor, node);
    }
  }
  
  /** 
   * We process requests as well as schedule refreshes because the denials 
   * help trim the tree faster than schedule refreshes (an approval-only change).
   */
  
  /** Called by ScheduleLoaderThread. */
  public synchronized void setRootScheduleWrapper(ScheduleWrapper wrapper) {
    if (root == null) {
      logger.info("Rooting empty tree");
      root = wrapper;
      transitions.clear();
      processedKeys.clear();
    } else if (root.equals(wrapper)) {
      logger.info("Ignoring unchanged schedule");
      // no change
    } else {
      if (!nodes.keys().contains(wrapper) && !nodes.values().contains(wrapper)) {
        logger.severe("!!! Tree does not contain schedule.  This is bad.  Rerooting");
        root = wrapper;
        nodes.clear();
        transitions.clear();
        processedKeys.clear();
      } else {
        removeAllNodesButSubtree(wrapper);
        root = wrapper;
        processedKeys.clear();
      }
    }
  }
  
  void removeAllNodesButSubtree(ScheduleWrapper wrapper) {
    Set<ScheduleWrapper> toKeep = new HashSet<>();
    Queue<ScheduleWrapper> toTraverse = new LinkedList<>();
    toKeep.add(wrapper);
    toTraverse.add(wrapper);
    while (!toTraverse.isEmpty()) {
      ScheduleWrapper next = toTraverse.remove();
      for (ScheduleWrapper node : nodes.get(next)) {
        toKeep.add(node);
        toTraverse.add(node);
      }
    }
    removeUnassociatedTransitions(toKeep);
    nodes.keys().retainAll(toKeep);
    nodes.values().retainAll(toKeep);
  }
  
  void removeSubtree(ScheduleWrapper wrapper) {
    Set<ScheduleWrapper> toRemove = new HashSet<>();
    Queue<ScheduleWrapper> toTraverse = new LinkedList<>();
    toRemove.add(wrapper);
    toTraverse.add(wrapper);
    while (!toTraverse.isEmpty()) {
      ScheduleWrapper next = toTraverse.remove();
      for (ScheduleWrapper node : nodes.get(next)) {
        toRemove.add(node);
        toTraverse.add(node);
      }
    }
    removeAssociatedTransitions(toRemove);
    nodes.keys().removeAll(toRemove);
    nodes.values().removeAll(toRemove);
  }
  
  void removeAssociatedTransitions(Collection<ScheduleWrapper> wrappers) {
    transitions.entrySet().removeIf(entry -> wrappers.contains(entry.getValue()));
  }

  void removeUnassociatedTransitions(Collection<ScheduleWrapper> wrappers) {
    Set<Transition> toRemove = new HashSet<>();
    for (Entry<Transition, ScheduleWrapper> entry : transitions.entrySet()) {
      if (!wrappers.contains(entry.getValue())) {
        toRemove.add(entry.getKey());
      }
    }
    toRemove.forEach((transition) -> transitions.remove(transition));
  }

  /** Called by OpentimeRequestLoaderThread. */
  public synchronized void markApproved(Transition transition) {
    if (!transitions.containsKey(transition)) {
      // The transition could have been removed by a reroot.
      logger.info("Approved transition missing: " + transition);
      return;
    }
    logger.info("Transition approved: " + transition);
    ScheduleWrapper survivor = transitions.get(transition);

    if (bidConfig.getEnableRerootOnApprovedTransition()) {
      setRootScheduleWrapper(survivor);
      return;
    }

    // whereever this node appears as a child, remove all other children.
    Set<ScheduleWrapper> toRemove = new HashSet<>();
    for (Map.Entry<ScheduleWrapper, Collection<ScheduleWrapper>> entry : nodes.asMap().entrySet()) {
      if (entry.getValue().contains(survivor) && entry.getValue().size() > 1) {
        logger.info("Removing " + (entry.getValue().size() - 1) + " peer(s) for " + transition);
        for (ScheduleWrapper sibling : entry.getValue()) {
          if (!sibling.equals(survivor)) {
            toRemove.add(sibling);
          }
        }
        break;
      }
    }
    toRemove.forEach((wrapper) -> removeSubtree(wrapper));
  }
  
  public synchronized void markDenied(Transition transition) {
    if (!transitions.containsKey(transition)) {
      // The transition could have been removed by a reroot or approval.
      logger.info("Denied transition missing: " + transition);
      return;
    }
    logger.info("Transition denied: " + transition);
    removeSubtree(transitions.get(transition));
  }
  
  @Override
  public int hashCode() {
    return Objects.hashCode(root, nodes);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof ScheduleWrapperTree)) {
      return false;
    }
    ScheduleWrapperTree that = (ScheduleWrapperTree) o;
    return Objects.equal(this.root, that.root)
        && Objects.equal(this.nodes, that.nodes);
  }

  // @formatter:off
  @Override
  public synchronized String toString() {
    return MoreObjects.toStringHelper(this)
        .add("root", root)
        .add("nodes", nodes)
        .add("transitions", transitions)
        .toString();
  }
  // @formatter:on

  public synchronized void populate(Status.Builder builder) {
    if (root == null) {
      return;
    }
    recurse(root, builder.getRootBuilder());
  }

  private void recurse(ScheduleWrapper wrapper, ScheduleNode.Builder builder) {
    wrapper.populate(builder);
    for (ScheduleWrapper node : nodes.get(wrapper)) {
      recurse(node, builder.addChildBuilder());
    }
  }
}
