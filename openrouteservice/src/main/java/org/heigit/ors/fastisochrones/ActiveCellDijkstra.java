/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.heigit.ors.fastisochrones;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.EdgeIteratorStateHelper;
import com.graphhopper.routing.Path;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Parameters;
import org.heigit.ors.partitioning.EccentricityStorage;
import org.heigit.ors.partitioning.IsochroneNodeStorage;

import java.util.PriorityQueue;
import java.util.Set;

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 * <p>
 *
 * @author Peter Karich
 */
public class ActiveCellDijkstra extends AbstractRoutingAlgorithm {
    protected IntObjectMap<SPTEntry> fromMap;
    protected PriorityQueue<SPTEntry> fromHeap;
    protected IsochroneNodeStorage isochroneNodeStorage;
    protected EccentricityStorage eccentricityStorage;
    protected FastIsochroneAlgorithm fastIsochroneAlgorithm;
    Set<Integer> processedBorderNodes;
    protected SPTEntry currEdge;
    private int visitedNodes;
    private double isochroneLimit = 0;

    // ORS-GH MOD START Modification by Maxim Rylov: Added a new class variable used for computing isochrones.
    protected Boolean reverseDirection = false;
    // ORS-GH MOD END

    public ActiveCellDijkstra(FastIsochroneAlgorithm fastIsochroneAlgorithm)
    {
        super(fastIsochroneAlgorithm.graph, fastIsochroneAlgorithm.weighting, fastIsochroneAlgorithm.traversalMode);
        this.fastIsochroneAlgorithm = fastIsochroneAlgorithm;
        this.isochroneNodeStorage = fastIsochroneAlgorithm.isochroneNodeStorage;
        this.eccentricityStorage = fastIsochroneAlgorithm.eccentricityStorage;
        this.processedBorderNodes = fastIsochroneAlgorithm.processedBorderNodes;
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
    }

    protected void initCollections(int size) {
        fromHeap = new PriorityQueue<>(size);
        fromMap = new GHIntObjectHashMap<>(size);
    }

    protected void addInitialBordernode(int nodeId, double weight){
        SPTEntry entry = new SPTEntry(nodeId, weight);
        if (!traversalMode.isEdgeBased()) {
            fromMap.put(nodeId, entry);
        }

        fromHeap.add(entry);
    }

    protected void init(){
        if (fromHeap.peek() != null){
            currEdge = fromHeap.peek();
        }
    }

    // ORS-GH MOD START Modification by Maxim Rylov: Added a new method.
    public void setReverseDirection(Boolean reverse) {
        reverseDirection = reverse;
    }
    // ORS-GH MOD END

    protected void runAlgo() {
        EdgeExplorer explorer = outEdgeExplorer;
        while (true) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int baseNode = currEdge.adjNode;
            EdgeIterator iter = explorer.setBaseNode(baseNode);
            while (iter.next()) {
                if (!accept(iter, currEdge.edge))
                    continue;

                // ORS-GH MOD START
                // ORG CODE START
                //int traversalId = traversalMode.createTraversalId(iter, false);
                //double tmpWeight = weighting.calcWeight(iter, false, currEdge.edge) + currEdge.weight;
                // ORIGINAL END
                // TODO: MARQ24 WHY the heck the 'reverseDirection' is not used also for the traversal ID ???
                int traversalId = traversalMode.createTraversalId(iter, false);
                // Modification by Maxim Rylov: use originalEdge as the previousEdgeId
                double tmpWeight = weighting.calcWeight(iter, reverseDirection, currEdge.originalEdge) + currEdge.weight;
                // ORS-GH MOD END
                if (Double.isInfinite(tmpWeight))
                    continue;

                SPTEntry nEdge = fromMap.get(traversalId);
                if (nEdge == null) {
                    nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight);
                    nEdge.parent = currEdge;
                    // ORS-GH MOD START
                    // Modification by Maxim Rylov: Assign the original edge id.
                    nEdge.originalEdge = EdgeIteratorStateHelper.getOriginalEdge(iter);
                    // ORS-GH MOD END
                    fromMap.put(traversalId, nEdge);
                    fromHeap.add(nEdge);
                } else if (nEdge.weight > tmpWeight) {
                    fromHeap.remove(nEdge);
                    nEdge.edge = iter.getEdge();
                    // ORS-GH MOD START
                    nEdge.originalEdge = EdgeIteratorStateHelper.getOriginalEdge(iter);
                    // ORS-GH MOD END
                    nEdge.weight = tmpWeight;
                    nEdge.parent = currEdge;
                    fromHeap.add(nEdge);
                } else
                    continue;

            }


            if (fromHeap.isEmpty())
                break;

            currEdge = fromHeap.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }
    }

    @Override
    protected boolean finished() {
        return isLimitExceeded();
    }

    private boolean isLimitExceeded(){
        return currEdge.getWeightOfVisitedPath() > isochroneLimit;
    }

    public void setIsochroneLimit(double limit){
        isochroneLimit = limit;
    }

    @Override
    protected Path extractPath() {
        throw new IllegalStateException("Cannot calc a path with this algorithm");
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }


    @Override
    public Path calcPath(int from, int to) {
        throw new IllegalStateException("Cannot calc a path with this algorithm");
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.DIJKSTRA;
    }
}
