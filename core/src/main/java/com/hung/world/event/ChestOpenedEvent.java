package com.hung.world.event;

import com.hung.combat.ChestTier;

/**
 * Published on the EventBus when a chest finishes opening and drops its item (ChestSystem). The
 * publisher reuses one instance, so listeners copy what they keep.
 */
public class ChestOpenedEvent {

    /** Stable chest id: map path + "#" + TMX object id. */
    public String chestId;
    public ChestTier tier;

    public ChestOpenedEvent set(String chestId, ChestTier tier) {
        this.chestId = chestId;
        this.tier = tier;
        return this;
    }
}
