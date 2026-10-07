package com.hung.component;

import com.badlogic.gdx.utils.Array;

/**
 * The weapons an entity carries (one WeaponComponent per weapon) and which one is in hand.
 * Input sets a request; WeaponSwitchSystem makes the switch (stance included).
 */
public class ArsenalComponent {

    public final Array<WeaponComponent> slots = new Array<>();

    /** Index of the weapon in hand. */
    public int current;

    /** Slot asked for this frame (-1 = none). */
    public int requestedSlot = -1;

    /** Next weapon asked for this frame. */
    public boolean isNextRequested;
}
