package org.heigit.ors.partitioning;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.HashSet;
import java.util.Set;

import static org.heigit.ors.partitioning.MaxFlowMinCut._dummyEdgeId;
import static org.heigit.ors.partitioning.MaxFlowMinCut._dummyNodeId;

/**
 * Implementation of MaxFlowMinCut
 * <p>
 *
 * @author Hendrik Leuschner
 */
public class PartitioningDataBuilder {

    private Graph _graph;
    private EdgeExplorer _edgeExpl;
    private EdgeIterator _edgeIter;
    private PartitioningData pData;
    private EdgeFilter edgeFilter;

    private int maxEdgeId = -1;


    PartitioningDataBuilder(GraphHopperStorage ghStorage, PartitioningData pData) {
        this._graph = ghStorage.getBaseGraph();
        this._edgeExpl = _graph.createEdgeExplorer();
        this.pData = pData;

        initStatics();
    }

    public void run(){
        _dummyEdgeId = _graph.getAllEdges().length() + 1;
        _dummyNodeId = _graph.getNodes() + 1;
        //Need entries for all edges + one dummy edge for all nodes
        pData.createEdgeDataStructures(_dummyEdgeId);
        pData.fillFlowEdgeBaseNodes(_graph);
        pData.createNodeDataStructures(_dummyNodeId);
        buildStaticNetwork();
    }


    public void initStatics() {
//        this.flowEdgeMap = new HashMap<>();
        _dummyEdgeId = _graph.getAllEdges().length() + 1;
        _dummyNodeId = _graph.getNodes() + 1;
    }

    public void buildStaticNetwork() {
        Set<Integer> targSet = new HashSet<>();

        for (int baseId = 0; baseId < _graph.getNodes(); baseId++) {
            targSet.clear();
            _edgeIter = _edgeExpl.setBaseNode(baseId);
            while (_edgeIter.next()) {
                int targId = _edgeIter.getAdjNode();
                if(!acceptForPartitioning(_edgeIter))
                    continue;
                //>> eliminate Loops and MultiEdges
                if ((baseId != targId) && (!targSet.contains(targId))) {
                    targSet.add(targId);
                    addEdge(_edgeIter.getEdge(), baseId, targId);
                }
            }
        }
    }

    private int addEdge(int edgeId, int baseNode, int targNode) {
        pData.setFlowEdgeData(edgeId, baseNode,
                new FlowEdgeData(false, edgeId));
        pData.setFlowEdgeData(edgeId, targNode,
                new FlowEdgeData(false, edgeId));
        if(maxEdgeId < edgeId)
            maxEdgeId = edgeId;
        return edgeId;
    }

    public void setAdditionalEdgeFilter(EdgeFilter edgeFilter){
        this.edgeFilter = edgeFilter;
    }

    private boolean acceptForPartitioning(EdgeIterator edgeIterator){
        return edgeFilter.accept(edgeIterator);
    }
}