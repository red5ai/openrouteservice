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
import org.heigit.ors.partitioning.BorderNodeDistanceSet;
import org.heigit.ors.partitioning.BorderNodeDistanceStorage;
import org.heigit.ors.partitioning.EccentricityStorage;
import org.heigit.ors.partitioning.IsochroneNodeStorage;

import java.util.PriorityQueue;

/**
 * Single-source shortest path algorithm bounded by isochrone limit.
 * <p>
 *
 * @author Hendrik Leuschner
 */
public class CoreRangeDijkstra extends AbstractRoutingAlgorithm {
    protected IntObjectMap<SPTEntry> fromMap;
    protected PriorityQueue<SPTEntry> fromHeap;
    protected IsochroneNodeStorage isochroneNodeStorage;
    protected EccentricityStorage eccentricityStorage;
    protected BorderNodeDistanceStorage borderNodeDistanceStorage;
    protected FastIsochroneAlgorithm fastIsochroneAlgorithm;
    protected SPTEntry currEdge;
    private int visitedNodes;
    private double isochroneLimit = 0;

    // ORS-GH MOD START Modification by Maxim Rylov: Added a new class variable used for computing isochrones.
    protected Boolean reverseDirection = false;
    // ORS-GH MOD END

    public CoreRangeDijkstra(FastIsochroneAlgorithm fastIsochroneAlgorithm)
    {
        super(fastIsochroneAlgorithm.graph, fastIsochroneAlgorithm.weighting, fastIsochroneAlgorithm.traversalMode);
        this.fastIsochroneAlgorithm = fastIsochroneAlgorithm;
        this.isochroneNodeStorage = fastIsochroneAlgorithm.isochroneNodeStorage;
        this.eccentricityStorage = fastIsochroneAlgorithm.eccentricityStorage;
        this.borderNodeDistanceStorage = fastIsochroneAlgorithm.borderNodeDistanceStorage;
//        this.processedBorderNodes = fastIsochroneAlgorithm.processedBorderNodes;
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
    }

    protected void initCollections(int size) {
        fromHeap = new PriorityQueue<>(size);
        fromMap = new GHIntObjectHashMap<>(size);
    }

    protected void initFrom(int from){
        currEdge = new SPTEntry(from, 0.0D);
        if (!traversalMode.isEdgeBased()) {
            fromMap.put(from, currEdge);
        }
        fromHeap.add(currEdge);
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

            /* check distance vs. range limit for Core-Graph Nodes only ! */
            if (isochroneNodeStorage.getBorderness(baseNode)) {
                int baseCell = isochroneNodeStorage.getCellId(baseNode);
                double baseNodeEccentricity = eccentricityStorage.getEccentricity(baseNode);

                BorderNodeDistanceSet bnds = borderNodeDistanceStorage.getBorderNodeDistanceSet(baseNode);

                for(int i = 0; i < bnds.getAdjBorderNodeIds().length; i++){
                    int id = bnds.getAdjBorderNodeIds()[i];
                    double addWeight = bnds.getAdjBorderNodeDistances()[i];

                    double weight = bnds.getAdjBorderNodeDistances()[i] + currEdge.weight;
                    if(weight > isochroneLimit)
                        continue;
                    if (Double.isInfinite(weight))
                        continue;

                    SPTEntry nEdge = fromMap.get(id);
                    if (nEdge == null) {
                        nEdge = new SPTEntry(EdgeIterator.NO_EDGE, id, weight);
                        nEdge.parent = currEdge;
                        fromMap.put(id, nEdge);
                        fromHeap.add(nEdge);
                    } else if (nEdge.weight > weight) {
                        fromHeap.remove(nEdge);
                        nEdge.edge = EdgeIterator.NO_EDGE;
                        // ORS-GH MOD START
                        nEdge.originalEdge = EdgeIterator.NO_EDGE;
                        // ORS-GH MOD END
                        nEdge.weight = weight;
                        nEdge.parent = currEdge;
                        fromHeap.add(nEdge);
                    } else
                        continue;
                }

                //Fully reachable cell
                if (fromMap.get(baseNode).getWeightOfVisitedPath() + baseNodeEccentricity < isochroneLimit
                        && eccentricityStorage.getFullyReachable(baseNode)) {
                    fastIsochroneAlgorithm.fullyReachableCells.add(baseCell);
                    fastIsochroneAlgorithm.addInactiveBorderNode(baseNode);
                    if (fastIsochroneAlgorithm.activeCells.contains(baseCell))
                        fastIsochroneAlgorithm.activeCells.remove(baseCell);
                }

                else {
//                    processedBorderNodes.add(baseNode);
                    if (!fastIsochroneAlgorithm.fullyReachableCells.contains(baseCell)) {
                        fastIsochroneAlgorithm.addActiveCell(baseCell);
                        fastIsochroneAlgorithm.addActiveBorderNode(baseNode);
                    }
                }
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
