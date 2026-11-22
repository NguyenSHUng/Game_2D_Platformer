package com.hung.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileGridTest {

    private static final float TILE = 16f;

    @Test
    void outsideTheMapSidesAreWallsAndAboveBelowIsAir() {
        TileGrid grid = new TileGrid(4, 4, TILE);
        assertEquals(TileType.SOLID, grid.get(-1, 0));
        assertEquals(TileType.SOLID, grid.get(4, 0));
        assertEquals(TileType.EMPTY, grid.get(0, -1));
        assertEquals(TileType.EMPTY, grid.get(0, 4));
    }

    @Test
    void supportIsSolidPlatformOrTheTopRungOfALadder() {
        TileGrid grid = new TileGrid(4, 4, TILE);
        grid.set(0, 0, TileType.SOLID);
        grid.set(1, 0, TileType.PLATFORM);
        grid.set(2, 0, TileType.LADDER);
        grid.set(2, 1, TileType.LADDER);
        assertTrue(grid.isSupport(0, 0));
        assertTrue(grid.isSupport(1, 0));
        assertFalse(grid.isSupport(2, 0), "a rung with a rung above");
        assertTrue(grid.isSupport(2, 1), "the top rung");
        assertFalse(grid.isSupport(3, 0));
    }

    @Test
    void floorAheadProbesOneUnitPastTheEdgeAndBelowTheFeet() {
        TileGrid grid = new TileGrid(10, 4, TILE);
        for (int x = 0; x <= 5; x++) grid.set(x, 0, TileType.SOLID);
        float feetY = TILE;
        // Edge of a 10-wide box at x + 5; the probe lands 1 unit further
        assertTrue(grid.hasFloorAhead(6 * TILE - 7f, feetY, 5f, 1f), "probe at 95: column 5");
        assertFalse(grid.hasFloorAhead(6 * TILE - 6f, feetY, 5f, 1f), "probe at 96: column 6");
        assertTrue(grid.hasFloorAhead(6 * TILE - 6f, feetY, 5f, -1f), "facing back over the floor");
    }
}
