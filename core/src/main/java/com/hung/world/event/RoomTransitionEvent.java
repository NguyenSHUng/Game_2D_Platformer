package com.hung.world.event;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.hung.world.Room;

/**
 * Published on the EventBus whenever the player's current room changes: the new room,
 * its camera bounds and where the player now stands. Listeners (camera, minimap) react to
 * it without knowing about doors. The publisher (DoorSystem) reuses one instance, so
 * listeners copy what they keep.
 */
public class RoomTransitionEvent {

    /** Why the room changed. */
    public enum Reason {
        /** The level just started: the room holding the player spawn. */
        LEVEL_START,
        /** The player went through a door (published while the screen is black). */
        DOOR,
        /** The player was moved without a door (respawn at a checkpoint in another room). */
        RELOCATED
    }

    public Reason reason;
    /** The room the player left; null at level start. */
    public Room previousRoom;
    /** The room the player is now in. */
    public Room room;
    /** The new room's camera bounds, world units. */
    public final Rectangle cameraBounds = new Rectangle();
    /** The player's feet point in the new room, world units. */
    public final Vector2 spawn = new Vector2();
    /** Id of the door used; null unless reason is DOOR. */
    public String doorId;

    public RoomTransitionEvent set(Reason reason, Room previousRoom, Room room, Rectangle cameraBounds,
                                   Vector2 spawn, String doorId) {
        this.reason = reason;
        this.previousRoom = previousRoom;
        this.room = room;
        this.cameraBounds.set(cameraBounds);
        this.spawn.set(spawn);
        this.doorId = doorId;
        return this;
    }
}
