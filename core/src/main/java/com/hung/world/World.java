package com.hung.world;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.entity.Entity;

/**
 * Container for the current level and every entity in it.
 * Holds no game logic; systems read and modify its contents.
 */
public class World {

    private final Level level;
    private final Array<Entity> entities = new Array<>();
    private Entity player;
    /** Where the player comes back after a hazard: the last checkpoint touched, else the spawn. */
    private final Vector2 respawnPoint = new Vector2();
    /** Current room and door fade (DoorSystem writes, renderers read). */
    private final RoomTransition roomTransition = new RoomTransition();

    public World(Level level) {
        this.level = level;
        this.respawnPoint.set(level.getPlayerSpawn());
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
    }

    public void removeEntity(Entity entity) {
        entities.removeValue(entity, true);
        if (entity == player) player = null;
    }

    public void setPlayer(Entity player) {
        this.player = player;
        addEntity(player);
    }

    public Level getLevel() { return level; }
    public Array<Entity> getEntities() { return entities; }
    public Entity getPlayer() { return player; }
    public Vector2 getRespawnPoint() { return respawnPoint; }
    public RoomTransition getRoomTransition() { return roomTransition; }
}
