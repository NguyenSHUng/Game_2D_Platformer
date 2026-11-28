package com.hung.combat.system;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.Effects;
import com.hung.combat.ItemArt;
import com.hung.combat.LootDrop;
import com.hung.combat.Pickups;
import com.hung.component.PickupComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.physics.Hitboxes;
import com.hung.world.World;
import com.hung.world.event.PickupCollectedEvent;

/**
 * Items in the world:
 *  - a dropped item flies its arc under LootDrop.GRAVITY, bounces once on its landing point and then
 *    lies still (it moves itself, like a bullet; PhysicsSystem ignores it), kicking up dust where it lands;
 *  - the player picks a lying item up by touching it: its rule ({@link Pickups#apply}) runs, and an
 *    item that gave something disappears and is reported (PickupCollectedEvent). An item of no use
 *    right now (full health, full reserve, dead player) stays where it lies. A picked item flashes.
 * Reads: PickupComponent, ColliderComponent. Writes: PickupComponent, TransformComponent, the player's
 * Health / Weapon reserve (through Pickups); removes picked items, adds effect entities. Publishes on the EventBus.
 */
public class PickupSystem {

    private final World world;
    private final EventBus events;
    private final ItemArt art;
    // Reused every frame to avoid allocating in the game loop
    private final Rectangle playerBox = new Rectangle();
    private final Rectangle itemBox = new Rectangle();
    private final PickupCollectedEvent collected = new PickupCollectedEvent();

    /** Without loot art (tests): no landing dust, no pickup flash. */
    public PickupSystem(World world, EventBus events) {
        this(world, events, ItemArt.NONE);
    }

    public PickupSystem(World world, EventBus events, ItemArt art) {
        this.world = world;
        this.events = events;
        this.art = art;
    }

    public void update(float delta) {
        Entity player = world.getPlayer();
        boolean canPick = player != null && player.getCollider() != null;
        if (canPick) Hitboxes.body(player, playerBox);
        Array<Entity> entities = world.getEntities();
        // Backwards, so removing an item doesn't skip the next one
        for (int i = entities.size - 1; i >= 0; i--) {
            Entity item = entities.get(i);
            PickupComponent pickup = item.getPickup();
            if (pickup == null || item.getCollider() == null) continue;
            if (pickup.isFlying) {
                fly(item, pickup, delta);
                continue;
            }
            if (!canPick || !Hitboxes.body(item, itemBox).overlaps(playerBox)) continue;
            int amount = Pickups.apply(player, pickup.type);
            if (amount <= 0) continue;
            world.removeEntity(item);
            Vector2 at = item.getTransform().position;
            Effects.spawn(world, art.pickup(), at.x, at.y + EntityFactory.PICKUP_SIZE / 2f, 0f, true, 1f);
            events.publish(collected.set(pickup.type, amount));
        }
    }

    /** One step of the arc; on the landing point the first touch bounces straight up, the second ends the flight. */
    private void fly(Entity item, PickupComponent pickup, float delta) {
        Vector2 position = item.getTransform().position;
        Vector2 velocity = item.getTransform().velocity;
        velocity.y -= LootDrop.GRAVITY * delta;
        position.mulAdd(velocity, delta);
        if (velocity.y >= 0f || position.y > pickup.landingY) return;
        position.set(pickup.landingX, pickup.landingY);
        if (!pickup.hasBounced) {
            pickup.hasBounced = true;
            Effects.spawn(world, art.landing(), pickup.landingX, pickup.landingY, 0f, true, 1f);
            velocity.set(0f, -velocity.y * LootDrop.BOUNCE);
        } else {
            velocity.setZero();
            pickup.isFlying = false;
        }
    }
}
