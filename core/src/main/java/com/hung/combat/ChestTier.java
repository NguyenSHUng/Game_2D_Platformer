package com.hung.combat;

/** Kinds of treasure chest the map generator places (the CHEST object's "tier" property). */
public enum ChestTier {
    WOOD,
    GOLD;

    /** "gold" (any case) → GOLD; anything else, missing included → WOOD. */
    public static ChestTier fromName(String name) {
        return "gold".equalsIgnoreCase(name) ? GOLD : WOOD;
    }
}
