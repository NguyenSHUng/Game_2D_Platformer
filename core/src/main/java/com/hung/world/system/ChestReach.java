package com.hung.world.system;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.ChestComponent;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.World;

/**
 * The one rule for "which chest can the player open now": among the CLOSED chests whose body, grown
 * by ChestSystem.REACH to each side, touches the player's body, the one nearest the player's feet.
 * ChestSystem opens that chest on the interact key; the HUD prompt shows it. Sharing the rule keeps
 * the two from ever picking different chests. Read-only; whether the player may act (alive, has
 * pressed the key) is the caller's business.
 */
public final class ChestReach {

    // Reused every call to avoid allocating in the game loop
    private final Rectangle playerBox = new Rectangle();
    private final Rectangle reachBox = new Rectangle();

    /** The nearest closed chest within reach, or null (no player, no player body, nothing in reach). */
    public Entity nearestClosedChest(World world) {
        Entity player = world.getPlayer();
        if (player == null || player.getCollider() == null) return null;
        Hitboxes.body(player, playerBox);
        Vector2 feet = player.getTransform().position;

        Entity nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            ChestComponent chest = entity.getChest();
            if (chest == null || chest.state != ChestComponent.State.CLOSED) continue;
            Hitboxes.body(entity, reachBox);
            reachBox.x -= ChestSystem.REACH;
            reachBox.width += 2f * ChestSystem.REACH;
            if (!reachBox.overlaps(playerBox)) continue;
            float distance = entity.getTransform().position.dst2(feet);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        return nearest;
    }
}
