package com.hung.component;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.hung.combat.ChestTier;
import com.hung.world.Room;

/**
 * A treasure chest from the map (CHEST object). The player opens it once with the interact key;
 * it plays its opening, then drops its one item (ChestSystem). Its body is the entity's collider.
 */
public class ChestComponent {

    public enum State { CLOSED, OPENING, OPENED }

    /** Stable id: map path + "#" + the TMX object id. */
    public String id;
    public ChestTier tier;
    /** The room it stands in (loot never lands outside it); null on maps without rooms. */
    public Room room;
    public State state = State.CLOSED;
    /** Plays while it opens and holds its last frame once open; null = no art (opened chests are darkened). */
    public Animation<TextureRegion> opening;
    /** Seconds in the current state. */
    public float stateTime;
}
