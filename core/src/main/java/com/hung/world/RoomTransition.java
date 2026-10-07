package com.hung.world;

import com.hung.component.DoorComponent;

/**
 * Room-transition state kept in the World (data only): DoorSystem writes it, the fade
 * renderer and the game loop read it.
 *
 *   NONE ──door touched──▶ FADING_OUT (world paused) ──black──▶ player moved, event published
 *        ◀──── faded in ──── FADING_IN (world runs)  ◀─────────┘
 */
public class RoomTransition {

    public enum Phase { NONE, FADING_OUT, FADING_IN }

    public Phase phase = Phase.NONE;
    /** Black overlay opacity, 0 (clear) .. 1 (black). */
    public float fade;
    /** The room the player is in; null before the level starts or on maps without rooms. */
    public Room currentRoom;
    /** The door being used while FADING_OUT. */
    public DoorComponent pendingDoor;
}
