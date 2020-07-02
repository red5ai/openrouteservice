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

import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.heigit.ors.fastisochrones.partitioning.storage.EccentricityStorage;
import org.heigit.ors.fastisochrones.partitioning.storage.IsochroneNodeStorage;

import static org.heigit.ors.fastisochrones.partitioning.FastIsochroneParameters.ACTIVECELLDIJKSTRA;

/**
 * Calculates shortest paths within an active isochrones cell.
 * Starts from a given set of entry nodes and ends when isochrone limit reached.
 * <p>
 *
 * @author Hendrik Leuschner
 */
public class ActiveCellDijkstra extends AbstractIsochroneDijkstra {
    protected IsochroneNodeStorage isochroneNodeStorage;
    protected EccentricityStorage eccentricityStorage;
    protected FastIsochroneAlgorithm fastIsochroneAlgorithm;
    private double isochroneLimit = 0;

    public ActiveCellDijkstra(FastIsochroneAlgorithm fastIsochroneAlgorithm) {
        super(fastIsochroneAlgorithm.graph, fastIsochroneAlgorithm.weighting, fastIsochroneAlgorithm.traversalMode);
        this.fastIsochroneAlgorithm = fastIsochroneAlgorithm;
        this.isochroneNodeStorage = fastIsochroneAlgorithm.isochroneNodeStorage;
        this.eccentricityStorage = fastIsochroneAlgorithm.eccentricityStorage;
    }

    protected void addInitialBordernode(int nodeId, double weight) {
        SPTEntry entry = new SPTEntry(nodeId, weight);
        fromHeap.add(entry);
    }

    protected void init() {
        if (fromHeap.peek() != null) {
            currEdge = fromHeap.peek();
        }
    }

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
                double tmpWeight = weighting.calcWeight(iter, reverseDirection, currEdge.originalEdge) + currEdge.weight;
                if (Double.isInfinite(tmpWeight))
                    continue;
                SPTEntry nEdge = fromMap.get(traversalId);
                if (nEdge == null) {
                    createEntry(iter, traversalId, tmpWeight);
                } else if (nEdge.weight > tmpWeight) {
                    updateEntry(nEdge, iter, tmpWeight);
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

    private boolean isLimitExceeded() {
        return currEdge.getWeightOfVisitedPath() > isochroneLimit;
    }

    public void setIsochroneLimit(double limit) {
        isochroneLimit = limit;
    }

    @Override
    public String getName() {
        return ACTIVECELLDIJKSTRA;
    }
}
