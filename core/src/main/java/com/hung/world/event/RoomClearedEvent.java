package com.hung.world.event;

import com.hung.world.Room;

/**
 * Published on the EventBus the first time every enemy of a room has been defeated (ScoreKeeper);
 * the game shows it. The publisher reuses one instance, so listeners copy what they keep.
 */
public class RoomClearedEvent {

    public Room room;
    /** Points it scored. */
    public int points;

    public RoomClearedEvent set(Room room, int points) {
        this.room = room;
        this.points = points;
        return this;
    }
}
