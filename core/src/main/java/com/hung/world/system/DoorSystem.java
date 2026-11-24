package com.hung.world.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.DoorComponent;
import com.hung.component.HealthComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.RoomTransition;
import com.hung.world.World;
import com.hung.world.event.RoomTransitionEvent;
import com.hung.world.loader.RoomLoader;

/**
 * Room transitions. Knows doors and rooms, nothing about the camera or the minimap:
 * it publishes a {@link RoomTransitionEvent} on the EventBus and lets listeners react.
 *
 *  - The player touching a door of the current room starts a short fade to black; the world
 *    pauses (GameScreen reads RoomTransition.phase), then the player is moved to the door's
 *    spawn in the target room — velocity kept, so a fall or a ladder climb simply continues —
 *    the event is published while the screen is black, and the picture fades back in.
 *  - Level start and "relocations" (respawn at a checkpoint in another room) publish the
 *    event immediately, so every listener learns about every room change the same way.
 * Reads: DoorComponent entities, player collider, Level rooms. Writes: World RoomTransition,
 * player position.
 */
public class DoorSystem {

    private static final String TAG = "DoorSystem";
    /** Seconds to fade to black before the player is moved (the world is paused meanwhile). */
    public static final float FADE_OUT_SECONDS = 0.15f;
    /** Seconds to fade back in once the player stands in the new room. */
    public static final float FADE_IN_SECONDS = 0.2f;

    private final World world;
    private final EventBus events;
    // Reused for every transition / frame: nothing is allocated in the game loop
    private final RoomTransitionEvent event = new RoomTransitionEvent();
    private final Rectangle body = new Rectangle();

    public DoorSystem(World world, EventBus events) {
        this.world = world;
        this.events = events;
    }

    /** Announces the room the player starts in. Call once, after the listeners subscribed. */
    public void start() {
        Level level = world.getLevel();
        if (level.getRooms().size == 0) return;
        Entity player = world.getPlayer();
        Vector2 at = player != null ? player.getTransform().position : level.getPlayerSpawn();
        enter(RoomLoader.roomAtOrNearest(level, at), at, RoomTransitionEvent.Reason.LEVEL_START);
    }

    /** Runs every frame, also while the world is paused by the fade (the fade itself lives here). */
    public void update(float delta) {
        RoomTransition transition = world.getRoomTransition();
        if (transition.currentRoom == null) return;
        if (transition.phase == RoomTransition.Phase.FADING_OUT) {
            transition.fade = Math.min(1f, transition.fade + delta / FADE_OUT_SECONDS);
            if (transition.fade >= 1f) arrive(transition);
            return;
        }
        if (transition.phase == RoomTransition.Phase.FADING_IN) {
            transition.fade = Math.max(0f, transition.fade - delta / FADE_IN_SECONDS);
            if (transition.fade <= 0f) transition.phase = RoomTransition.Phase.NONE;
        }

        Entity player = world.getPlayer();
        if (player == null || player.getCollider() == null) return;
        HealthComponent health = player.getHealth();
        if (health != null && health.isDead()) return;

        Hitboxes.body(player, body);
        DoorComponent door = touchedDoor(transition.currentRoom);
        if (door != null) {
            transition.phase = RoomTransition.Phase.FADING_OUT;
            transition.pendingDoor = door;
            return;
        }
        Vector2 feet = player.getTransform().position;
        if (!transition.currentRoom.bounds.contains(feet)) {
            Room room = world.getLevel().findRoomAt(feet.x, feet.y);
            if (room != null) enter(room, feet, RoomTransitionEvent.Reason.RELOCATED);
        }
    }

    /** First door of the given room whose trigger the player's body touches; null if none. */
    private DoorComponent touchedDoor(Room room) {
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            DoorComponent door = entities.get(i).getDoor();
            if (door != null && door.roomId.equals(room.id) && door.trigger.overlaps(body)) return door;
        }
        return null;
    }

    /** Screen is black: move the player through the door and announce the new room. */
    private void arrive(RoomTransition transition) {
        DoorComponent door = transition.pendingDoor;
        Room previous = transition.currentRoom;
        Room target = world.getLevel().getRoom(door.targetRoomId);
        transition.pendingDoor = null;
        transition.phase = RoomTransition.Phase.FADING_IN;
        if (target == null) return;

        Entity player = world.getPlayer();
        if (player != null) player.getTransform().position.set(door.spawn);
        transition.currentRoom = target;
        Gdx.app.log(TAG, previous.id + " → " + target.id + " via " + door.id);
        events.publish(event.set(RoomTransitionEvent.Reason.DOOR, previous, target, door.cameraBounds, door.spawn, door.id));
    }

    private void enter(Room room, Vector2 at, RoomTransitionEvent.Reason reason) {
        RoomTransition transition = world.getRoomTransition();
        Room previous = transition.currentRoom;
        transition.currentRoom = room;
        if (previous != null) Gdx.app.log(TAG, previous.id + " → " + room.id + " (" + reason + ")");
        events.publish(event.set(reason, previous, room, room.bounds, at, null));
    }
}
