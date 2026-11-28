package com.hung.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.ChestComponent;
import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.World;
import com.hung.world.system.ChestSystem;

/**
 * Finds what the HUD's "E - Open Chest" prompt points at: the closed chest nearest the living player
 * among those the interact key would open now (the player's body touching the chest's body grown by
 * ChestSystem.REACH to each side, the same test ChestSystem makes). Read-only: it changes no chest.
 */
public class InteractionPrompt {

    public static final String OPEN_CHEST = "E - Open Chest";

    // Reused every frame to avoid allocating in the game loop
    private final Rectangle playerBox = new Rectangle();
    private final Rectangle reachBox = new Rectangle();

    /** The nearest closed chest within reach, or null (no player, dead player, nothing in reach). */
    public Entity chestInReach(World world) {
        Entity player = world.getPlayer();
        if (player == null || player.getCollider() == null || player.getInteract() == null) return null;
        HealthComponent health = player.getHealth();
        if (health != null && health.isDead()) return null;
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
