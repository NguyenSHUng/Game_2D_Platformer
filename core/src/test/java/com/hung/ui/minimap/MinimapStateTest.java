package com.hung.ui.minimap;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.hung.world.Room;
import com.hung.world.event.RoomTransitionEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapStateTest {

    @Test
    void restoreMarksRoomsAndSkipsIndicesOutsideTheLevel() {
        MinimapState state = new MinimapState(4);
        state.restore(new int[] {0, 2, 9, -1}, new int[] {1, 3, 7});
        assertTrue(state.isVisited(0));
        assertTrue(state.isVisited(2));
        assertFalse(state.isVisited(1));
        assertTrue(state.isKnown(1));
        assertTrue(state.isKnown(3));
        assertEquals(2, state.getVisitedCount());
    }

    @Test
    void restoreToleratesMissingArraysAndDoesNotCountTwice() {
        MinimapState state = new MinimapState(3);
        state.restore(null, null);
        assertEquals(0, state.getVisitedCount());
        state.restore(new int[] {1, 1}, null);
        assertEquals(1, state.getVisitedCount());
    }

    @Test
    void enteringARoomVisitsItAndRevealsItsNeighbours() {
        MinimapState state = new MinimapState(3);
        Room a = new Room("room_1", 0, "START", "r", true, new Rectangle());
        Room b = new Room("room_2", 1, "NORMAL", "r", true, new Rectangle());
        a.neighbors.add(b);
        state.onEvent(new RoomTransitionEvent().set(RoomTransitionEvent.Reason.LEVEL_START, null, a, new Rectangle(), new Vector2(), null));
        assertTrue(state.isVisited(0));
        assertTrue(state.isKnown(1));
        assertFalse(state.isVisited(1));
        assertEquals(a, state.getCurrentRoom());
    }
}
