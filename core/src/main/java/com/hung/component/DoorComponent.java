package com.hung.component;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * A door between two rooms (from the map's "doors" layer), all in world units, y up.
 * Touching {@link #trigger} while in {@link #roomId} sends the player to {@link #spawn}
 * in {@link #targetRoomId}, whose camera bounds are {@link #cameraBounds}.
 */
public class DoorComponent {

    public enum Direction { LEFT, RIGHT, UP, DOWN }

    public String id;
    /** The room this door leads out of; it only works while the player is in that room. */
    public String roomId;
    public String targetRoomId;
    /** The paired door in the target room leading back; null for one-way doors (drop holes). */
    public String targetDoorId;
    /** SIDE (doorway), LADDER (shaft) or DROP (one-way hole). */
    public String kind;
    public Direction direction;
    /** Touching this area uses the door. */
    public final Rectangle trigger = new Rectangle();
    /** Feet point where the player arrives in the target room (TargetDoorPosition). */
    public final Vector2 spawn = new Vector2();
    /** Camera bounds of the target room. */
    public final Rectangle cameraBounds = new Rectangle();
}
