package com.hung.save;

import com.badlogic.gdx.math.Vector2;
import com.hung.component.ArsenalComponent;
import com.hung.component.HealthComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.ui.minimap.MinimapState;
import com.hung.world.Level;
import com.hung.world.RoomTransition;
import com.hung.world.World;

/**
 * Captures a run into SaveData and puts a loaded SaveData back into a freshly loaded World.
 * Knows the game state, not files (SaveManager) or the JSON layout (SaveData).
 */
public final class SaveSnapshot {

    private SaveSnapshot() {
    }

    /**
     * The run as SaveData; null without a player. Checkpoint saves, and saves while the player is
     * dead or behind a door fade, put the player on the respawn point.
     */
    public static SaveData capture(World world, GameSession session, MinimapState exploration, int checkpointIndex,
                                   String reason, long savedAt) {
        Entity player = world.getPlayer();
        if (player == null) return null;
        Level level = world.getLevel();
        SaveData data = new SaveData();
        data.mode = session.mode.name();
        data.mapPath = session.mode.mapPath;
        data.mapSignature = signature(level);
        data.savedAt = savedAt;
        data.playTime = session.playTime;
        data.reason = reason;
        data.isComplete = session.isComplete;

        Vector2 respawn = world.getRespawnPoint();
        HealthComponent health = player.getHealth();
        boolean isDead = health != null && health.isDead();
        boolean isSafeSpot = !isDead && world.getRoomTransition().phase == RoomTransition.Phase.NONE;
        Vector2 at = SaveData.REASON_CHECKPOINT.equals(reason) || !isSafeSpot ? respawn : player.getTransform().position;
        data.x = at.x;
        data.y = at.y;
        data.respawnX = respawn.x;
        data.respawnY = respawn.y;
        data.checkpointIndex = checkpointIndex;
        data.checkpointCount = level.getCheckpoints().size;
        if (health != null) {
            data.maxHealth = health.getMaxHealth();
            data.health = isDead ? health.getMaxHealth() : health.getHealth();
        }

        ArsenalComponent arsenal = player.getArsenal();
        if (arsenal != null) {
            data.weaponSlot = arsenal.current;
            data.weaponIds = new String[arsenal.slots.size];
            data.ammo = new int[arsenal.slots.size];
            for (int i = 0; i < arsenal.slots.size; i++) {
                WeaponComponent weapon = arsenal.slots.get(i);
                data.weaponIds[i] = weapon.equipped != null ? weapon.equipped.getId() : null;
                data.ammo[i] = weapon.ammo;
            }
        }

        int rooms = exploration.getRoomCount();
        int visited = 0;
        int known = 0;
        for (int i = 0; i < rooms; i++) {
            if (exploration.isVisited(i)) visited++;
            if (exploration.isKnown(i)) known++;
        }
        data.visitedRooms = new int[visited];
        data.knownRooms = new int[known];
        for (int i = 0, v = 0, k = 0; i < rooms; i++) {
            if (exploration.isVisited(i)) data.visitedRooms[v++] = i;
            if (exploration.isKnown(i)) data.knownRooms[k++] = i;
        }
        data.roomCount = rooms;
        data.roomId = exploration.getCurrentRoom() != null ? exploration.getCurrentRoom().id : null;
        return data;
    }

    /**
     * Applies a save to a World loaded from the same mode. Returns false when the map was re-exported
     * since (another signature): positions and rooms then mean nothing, so only health and weapons
     * are restored and the run starts from the level spawn.
     */
    public static boolean apply(SaveData data, World world, MinimapState exploration) {
        Entity player = world.getPlayer();
        if (player == null) return false;
        boolean isSameMap = signature(world.getLevel()).equals(data.mapSignature);
        if (isSameMap) {
            world.setRespawnPoint(data.respawnX, data.respawnY);
            player.getTransform().position.set(data.x, data.y);
            player.getTransform().velocity.setZero();
            exploration.restore(data.visitedRooms, data.knownRooms);
        }
        HealthComponent health = player.getHealth();
        if (health != null && data.health > 0) health.setHealthClamped(data.health);
        ArsenalComponent arsenal = player.getArsenal();
        if (arsenal != null && data.weaponIds != null && data.ammo != null) {
            for (WeaponComponent weapon : arsenal.slots) {
                if (weapon.equipped == null) continue;
                for (int i = 0; i < data.weaponIds.length && i < data.ammo.length; i++) {
                    if (weapon.equipped.getId().equals(data.weaponIds[i])) weapon.ammo = data.ammo[i];
                }
            }
            // WeaponSwitchSystem makes the switch (and the stance) on the first update
            if (data.weaponSlot >= 0 && data.weaponSlot < arsenal.slots.size) arsenal.requestedSlot = data.weaponSlot;
        }
        return isSameMap;
    }

    /** The level's shape: grid size, rooms and checkpoints. */
    public static String signature(Level level) {
        return level.getGrid().getWidth() + "x" + level.getGrid().getHeight() + "/" + level.getRooms().size
            + "r/" + level.getCheckpoints().size + "c";
    }
}
