package org.heigit.ors.fastisochrones.partitioning;

import com.carrotsearch.hppc.IntHashSet;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import org.heigit.ors.fastisochrones.partitioning.storage.CellStorage;
import org.heigit.ors.fastisochrones.partitioning.storage.IsochroneNodeStorage;
import org.junit.Test;

import static org.junit.Assert.*;

public class CellAndIsochroneNodeStorageTest {
    private final CarFlagEncoder carEncoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = EncodingManager.create(carEncoder);

    GraphHopperStorage createGHStorage() {
        return new GraphBuilder(encodingManager).create();
    }

    @Test
    public void testIsochroneNodeStorage() {
        GraphHopperStorage ghStorage = createGHStorage();
        IsochroneNodeStorage ins = new IsochroneNodeStorage(5, ghStorage.getDirectory());
        assertFalse(ins.loadExisting());
        int[] cellIds = new int[]{2, 2, 3, 3, 3};
        boolean[] borderNess = new boolean[]{true, false, true, false, false};
        ins.setCellIds(cellIds);
        ins.setBorderness(borderNess);
        assertEquals(2, ins.getCellId(0));
        assertEquals(2, ins.getCellId(1));
        assertEquals(3, ins.getCellId(2));
        assertEquals(3, ins.getCellId(3));
        assertEquals(3, ins.getCellId(4));

        assertTrue(ins.getBorderness(0));
        assertFalse(ins.getBorderness(1));
        assertTrue(ins.getBorderness(2));
        assertFalse(ins.getBorderness(3));
        assertFalse(ins.getBorderness(4));

        IntHashSet expectedCellIds = new IntHashSet();
        expectedCellIds.addAll(2, 3);
        assertEquals(expectedCellIds, ins.getCellIds());
    }

    @Test(expected = IllegalStateException.class)
    public void testUnfilledCells() {
        GraphHopperStorage ghStorage = createGHStorage();
        IsochroneNodeStorage ins = new IsochroneNodeStorage(5, ghStorage.getDirectory());
        int[] cellIds = new int[]{2, 2, 3, 3, 3};
        boolean[] borderNess = new boolean[]{true, false, true, false, false};
        ins.setCellIds(cellIds);
        ins.setBorderness(borderNess);

        CellStorage cs = new CellStorage(5, ghStorage.getDirectory(), ins);
        cs.init();
        //Storage not filled, should throw exception
        cs.getNodesOfCell(2);
    }

    @Test(expected = IllegalStateException.class)
    public void testUnfilledContour() {
        GraphHopperStorage ghStorage = createGHStorage();
        IsochroneNodeStorage ins = new IsochroneNodeStorage(5, ghStorage.getDirectory());
        int[] cellIds = new int[]{2, 2, 3, 3, 3};
        boolean[] borderNess = new boolean[]{true, false, true, false, false};
        ins.setCellIds(cellIds);
        ins.setBorderness(borderNess);

        CellStorage cs = new CellStorage(5, ghStorage.getDirectory(), ins);
        cs.init();
        //Storage not filled, should throw exception
        cs.getCellContourOrder(2);
    }

    @Test(expected = IllegalStateException.class)
    public void testUnfilledSuperCell() {
        GraphHopperStorage ghStorage = createGHStorage();
        IsochroneNodeStorage ins = new IsochroneNodeStorage(5, ghStorage.getDirectory());
        int[] cellIds = new int[]{2, 2, 3, 3, 3};
        boolean[] borderNess = new boolean[]{true, false, true, false, false};
        ins.setCellIds(cellIds);
        ins.setBorderness(borderNess);

        CellStorage cs = new CellStorage(5, ghStorage.getDirectory(), ins);
        cs.init();
        //Storage not filled, should throw exception
        cs.getCellsOfSuperCellAsList(2);
    }

    @Test
    public void testCellStorage() {
        GraphHopperStorage ghStorage = createGHStorage();
        IsochroneNodeStorage ins = new IsochroneNodeStorage(5, ghStorage.getDirectory());
        int[] cellIds = new int[]{2, 2, 3, 3, 3};
        boolean[] borderNess = new boolean[]{true, false, true, false, false};
        ins.setCellIds(cellIds);
        ins.setBorderness(borderNess);

        CellStorage cs = new CellStorage(5, ghStorage.getDirectory(), ins);
        cs.init();
        cs.calcCellNodesMap();
        IntHashSet nodesCell2 = new IntHashSet();
        nodesCell2.addAll(0,1);
        IntHashSet nodesCell3 = new IntHashSet();
        nodesCell3.addAll(2,3,4);
        assertEquals(nodesCell2, cs.getNodesOfCell(2));
        assertEquals(nodesCell3, cs.getNodesOfCell(3));
    }
}