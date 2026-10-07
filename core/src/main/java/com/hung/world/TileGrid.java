package com.hung.world;

import com.badlogic.gdx.math.MathUtils;

import java.util.Arrays;

/**
 * Collision tiles of the level. Row 0 is the bottom row (y up, like the world).
 * Outside the map, the left/right sides are walls and above/below is open air.
 */
public class TileGrid {

    private final int width;
    private final int height;
    private final float tileSize;
    private final TileType[] cells;

    public TileGrid(int width, int height, float tileSize) {
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.cells = new TileType[width * height];
        Arrays.fill(cells, TileType.EMPTY);
    }

    public TileType get(int tileX, int tileY) {
        if (tileX < 0 || tileX >= width) return TileType.SOLID;
        if (tileY < 0 || tileY >= height) return TileType.EMPTY;
        return cells[tileY * width + tileX];
    }

    public void set(int tileX, int tileY, TileType type) {
        if (tileX >= 0 && tileX < width && tileY >= 0 && tileY < height) {
            cells[tileY * width + tileX] = type;
        }
    }

    /** Can be landed on from above: solid, one-way platform, or the top rung of a ladder. */
    public boolean isSupport(int tileX, int tileY) {
        TileType type = get(tileX, tileY);
        if (type.isSolid() || type == TileType.PLATFORM) return true;
        return type == TileType.LADDER && get(tileX, tileY + 1) != TileType.LADDER;
    }

    /** Tile index containing a world coordinate. */
    public int toTile(float worldCoordinate) {
        return MathUtils.floor(worldCoordinate / tileSize);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public float getTileSize() { return tileSize; }
}
