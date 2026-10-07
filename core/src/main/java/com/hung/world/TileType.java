package com.hung.world;

/**
 * Gameplay meaning of a map tile, read from the "type" property of the terrain
 * tileset (written by the map generator; editable in Tiled).
 */
public enum TileType {
    EMPTY,
    SOLID,
    PLATFORM,
    SPIKE,
    LADDER,
    TOXIC,
    BREAKABLE;

    /** Unknown or missing names (START, EXIT, decoration) count as empty air. */
    public static TileType fromName(String name) {
        if (name == null) return EMPTY;
        for (TileType type : values()) {
            if (type.name().equals(name)) return type;
        }
        return EMPTY;
    }

    /** Blocks movement from every side (breakable walls until destroyed). */
    public boolean isSolid() {
        return this == SOLID || this == BREAKABLE;
    }

    public boolean isHazard() {
        return this == SPIKE || this == TOXIC;
    }
}
