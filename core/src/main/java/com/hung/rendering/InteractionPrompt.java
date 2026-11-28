package com.hung.rendering;

import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.world.World;
import com.hung.world.system.ChestReach;

/**
 * Finds what the HUD's "E - Open Chest" prompt points at: the chest the interact key would open now,
 * by the same rule ChestSystem uses (ChestReach: the nearest closed chest within reach), and only
 * while the player is alive and can interact. Read-only: it changes no chest.
 */
public class InteractionPrompt {

    public static final String OPEN_CHEST = "E - Open Chest";

    private final ChestReach reach = new ChestReach();

    /** The nearest closed chest within reach, or null (no player, dead player, nothing in reach). */
    public Entity chestInReach(World world) {
        Entity player = world.getPlayer();
        if (player == null || player.getInteract() == null) return null;
        HealthComponent health = player.getHealth();
        if (health != null && health.isDead()) return null;
        return reach.nearestClosedChest(world);
    }
}
