package com.hung.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * One room of the level (from the map's "rooms" layer), all in world units, y up.
 * The camera stays inside {@link #bounds} while the player is in this room; doors
 * (DoorComponent) move the player from room to room.
 */
public class Room {

    /** Id from the map tool, e.g. "room_7" or "branch_room_2". */
    public final String id;
    /** Position in Level.getRooms(), for per-room arrays (minimap exploration). */
    public final int index;
    /** STANDARD, START, ARENA, VERTICAL_SHAFT, BRANCH, … (map tool room kinds). */
    public final String kind;
    public final String regionId;
    /** On the main START → EXIT route (false for optional side rooms). */
    public final boolean isOnPath;
    /** Camera bounds: the room interior plus its walls (and its secret pocket). */
    public final Rectangle bounds = new Rectangle();
    /** Rooms one door away (filled by the loader). */
    public final Array<Room> neighbors = new Array<>();
    /** Center of every door trigger leading out of this room (filled by the loader). */
    public final Array<Vector2> doorways = new Array<>();

    public Room(String id, int index, String kind, String regionId, boolean isOnPath, Rectangle bounds) {
        this.id = id;
        this.index = index;
        this.kind = kind;
        this.regionId = regionId;
        this.isOnPath = isOnPath;
        this.bounds.set(bounds);
    }
}
