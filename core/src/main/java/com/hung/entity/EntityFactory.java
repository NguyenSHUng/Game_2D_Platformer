package com.hung.entity;

import com.hung.component.DoorComponent;

/** Builds entities that are not characters (player: PlayerFactory, enemies: EnemyFactory). */
public final class EntityFactory {

    private EntityFactory() {
    }

    /** A door between two rooms: a transform at the trigger's center plus the door data (no sprite, no collider). */
    public static Entity createDoor(DoorComponent door) {
        Entity entity = new Entity();
        door.trigger.getCenter(entity.getTransform().position);
        entity.setDoor(door);
        return entity;
    }
}
