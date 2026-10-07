package com.hung.ui;

import com.badlogic.gdx.utils.Array;
import com.hung.core.EventListener;
import com.hung.world.Room;
import com.hung.world.RoomTransitionEvent;

/**
 * What the player has explored, driven only by {@link RoomTransitionEvent}s:
 *  - the entered room becomes visited (hidden / dark → visible) and current (player icon);
 *  - rooms one door away from a visited room become known (drawn as dark outlines).
 * MinimapRenderer draws it; nothing here touches doors, the camera or GL.
 */
public class MinimapState implements EventListener<RoomTransitionEvent> {

    /** Per room (Room.index). */
    private final boolean[] isVisited;
    private final boolean[] isKnown;
    private int visitedCount;
    private Room currentRoom;

    public MinimapState(int roomCount) {
        this.isVisited = new boolean[roomCount];
        this.isKnown = new boolean[roomCount];
    }

    @Override
    public void onEvent(RoomTransitionEvent event) {
        currentRoom = event.room;
        if (currentRoom == null || isVisited[currentRoom.index]) return;
        isVisited[currentRoom.index] = true;
        isKnown[currentRoom.index] = true;
        visitedCount++;
        Array<Room> neighbors = currentRoom.neighbors;
        for (int i = 0; i < neighbors.size; i++) isKnown[neighbors.get(i).index] = true;
    }

    public Room getCurrentRoom() { return currentRoom; }
    public boolean isVisited(int roomIndex) { return isVisited[roomIndex]; }
    public boolean isKnown(int roomIndex) { return isKnown[roomIndex]; }
    public int getVisitedCount() { return visitedCount; }
    public int getRoomCount() { return isVisited.length; }
}
