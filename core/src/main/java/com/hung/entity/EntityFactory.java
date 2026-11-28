package com.hung.entity;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.hung.combat.ChestTier;
import com.hung.combat.PickupType;
import com.hung.component.ChestComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.DoorComponent;
import com.hung.component.PickupComponent;
import com.hung.component.SpriteComponent;
import com.hung.world.Room;

/** Builds entities that are not characters (player: PlayerFactory, enemies: EnemyFactory). */
public final class EntityFactory {

    /** Body of a pickup, world units (the items are drawn at the map's pixel density, about 12 px). */
    public static final float PICKUP_SIZE = 10f;

    private EntityFactory() {
    }

    /** A door between two rooms: a transform at the trigger's center plus the door data (no sprite, no collider). */
    public static Entity createDoor(DoorComponent door) {
        Entity entity = new Entity();
        door.trigger.getCenter(entity.getTransform().position);
        entity.setDoor(door);
        return entity;
    }

    /**
     * An item lying with its bottom-center on (x, y): the pickup data and a body to touch. No movement
     * (it stays put) and no sprite yet; RenderingSystem draws a placeholder until the item art exists.
     */
    public static Entity createPickup(PickupType type, float x, float y) {
        Entity entity = new Entity();
        entity.getTransform().position.set(x, y);
        PickupComponent pickup = new PickupComponent();
        pickup.type = type;
        entity.setPickup(pickup);
        ColliderComponent body = new ColliderComponent();
        body.width = PICKUP_SIZE;
        body.height = PICKUP_SIZE;
        entity.setCollider(body);
        return entity;
    }

    /**
     * A closed chest standing with its bottom-center on (x, y), drawn with the map's own chest tile
     * (one map pixel = one world unit) and a body of the tile's size.
     */
    public static Entity createChest(String id, ChestTier tier, Room room, TextureRegion closed, float x, float y) {
        return createChest(id, tier, room, closed, null, x, y);
    }

    /** The same with its opening strip (frame 0 = the closed tile; null = no art). */
    public static Entity createChest(String id, ChestTier tier, Room room, TextureRegion closed,
                                     Animation<TextureRegion> opening, float x, float y) {
        Entity entity = new Entity();
        entity.getTransform().position.set(x, y);
        ChestComponent chest = new ChestComponent();
        chest.id = id;
        chest.tier = tier;
        chest.room = room;
        chest.opening = opening;
        entity.setChest(chest);
        SpriteComponent sprite = new SpriteComponent();
        sprite.animation = new Animation<>(1f, closed);
        // Origin = the bottom-center pixel, so the tile covers x ± width / 2, y … y + height
        sprite.originX = closed.getRegionWidth() / 2f - 0.5f;
        sprite.originY = closed.getRegionHeight() - 0.5f;
        entity.setSprite(sprite);
        ColliderComponent body = new ColliderComponent();
        body.width = closed.getRegionWidth();
        body.height = closed.getRegionHeight();
        entity.setCollider(body);
        return entity;
    }
}
