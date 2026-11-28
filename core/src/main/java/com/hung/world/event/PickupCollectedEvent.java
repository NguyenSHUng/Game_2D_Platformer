package com.hung.world.event;

import com.hung.combat.PickupType;

/**
 * Published on the EventBus when the player picks an item up (PickupSystem); the game shows what
 * it gave. The publisher reuses one instance, so listeners copy what they keep.
 */
public class PickupCollectedEvent {

    public PickupType type;
    /** Hit points healed or rounds added (always above 0: useless items aren't picked up). */
    public int amount;

    public PickupCollectedEvent set(PickupType type, int amount) {
        this.type = type;
        this.amount = amount;
        return this;
    }
}
