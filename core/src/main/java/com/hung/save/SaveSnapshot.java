package com.hung.save;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.PickupType;
import com.hung.component.ArsenalComponent;
import com.hung.component.HealthComponent;
import com.hung.component.PickupComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.score.RunStats;
import com.hung.ui.minimap.MinimapState;
import com.hung.world.Level;
import com.hung.world.RoomTransition;
import com.hung.world.World;
import com.hung.world.system.ChestSystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            data.reserve = new int[arsenal.slots.size];
            for (int i = 0; i < arsenal.slots.size; i++) {
                WeaponComponent weapon = arsenal.slots.get(i);
                data.weaponIds[i] = weapon.equipped != null ? weapon.equipped.getId() : null;
                data.ammo[i] = weapon.ammo;
                data.reserve[i] = weapon.reserve;
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

        RunStats stats = session.stats;
        data.score = stats.getScore();
        data.kills = stats.getKills();
        data.chestsOpened = stats.getChestsOpened();
        data.roomsCleared = stats.getRoomsCleared();
        data.defeatedEnemies = stats.getDefeatedEnemies().toArray(new String[0]);
        data.openedChests = stats.getOpenedChests().toArray(new String[0]);
        data.clearedRooms = stats.getClearedRooms().toArray(new String[0]);
        data.pickups = pickups(world);
        return data;
    }

    /** Every item in the world where it comes to rest (a flying item at its landing point). */
    private static SaveData.PickupData[] pickups(World world) {
        List<SaveData.PickupData> list = new ArrayList<>();
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            PickupComponent pickup = entities.get(i).getPickup();
            if (pickup == null || pickup.type == null) continue;
            Vector2 at = entities.get(i).getTransform().position;
            SaveData.PickupData item = new SaveData.PickupData();
            item.type = pickup.type.name();
            item.x = pickup.isFlying ? pickup.landingX : at.x;
            item.y = pickup.isFlying ? pickup.landingY : at.y;
            list.add(item);
        }
        return list.toArray(new SaveData.PickupData[0]);
    }

    /** {@link #apply(SaveData, World, MinimapState, RunStats)} without restoring a score. */
    public static boolean apply(SaveData data, World world, MinimapState exploration) {
        return apply(data, world, exploration, null);
    }

    /**
     * Applies a save to a World loaded from the same mode. Returns false when the map was re-exported
     * since (another signature): positions, rooms, chests, lying items and scored ids then name things
     * of the old export, so only health, weapons (magazines and reserves) and the score totals are
     * restored and the run starts from the level spawn. Data a version 1 save lacks keeps the fresh
     * world's values (full reserves, closed chests, no score).
     */
    public static boolean apply(SaveData data, World world, MinimapState exploration, RunStats stats) {
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
                    if (!weapon.equipped.getId().equals(data.weaponIds[i])) continue;
                    weapon.ammo = data.ammo[i];
                    if (data.reserve != null && i < data.reserve.length) {
                        weapon.reserve = Math.max(0, Math.min(weapon.maxReserve, data.reserve[i]));
                    }
                }
            }
            // WeaponSwitchSystem makes the switch (and the stance) on the first update
            if (data.weaponSlot >= 0 && data.weaponSlot < arsenal.slots.size) arsenal.requestedSlot = data.weaponSlot;
        }
        if (stats != null) {
            stats.restore(data.score, data.kills, data.chestsOpened, data.roomsCleared, data.isComplete,
                isSameMap ? data.defeatedEnemies : null, isSameMap ? data.openedChests : null,
                isSameMap ? data.clearedRooms : null);
        }
        if (isSameMap) {
            openChests(world, data.openedChests);
            placePickups(world, data.pickups);
        }
        return isSameMap;
    }

    /** Chests the save lists as opened stay open (and give nothing again). */
    private static void openChests(World world, String[] ids) {
        if (ids == null) return;
        Set<String> opened = new HashSet<>();
        for (String id : ids) if (id != null) opened.add(id);
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            if (entity.getChest() != null && opened.contains(entity.getChest().id)) ChestSystem.markOpened(entity);
        }
    }

    /** Items the save found lying in the world, back where they lay; unknown kinds are skipped. */
    private static void placePickups(World world, SaveData.PickupData[] pickups) {
        if (pickups == null) return;
        for (SaveData.PickupData item : pickups) {
            PickupType type = pickupType(item != null ? item.type : null);
            if (type != null) world.addEntity(EntityFactory.createPickup(type, item.x, item.y));
        }
    }

    private static PickupType pickupType(String name) {
        if (name == null) return null;
        for (PickupType type : PickupType.values()) if (type.name().equals(name)) return type;
        return null;
    }

    /** The level's shape: grid size, rooms and checkpoints. */
    public static String signature(Level level) {
        return level.getGrid().getWidth() + "x" + level.getGrid().getHeight() + "/" + level.getRooms().size
            + "r/" + level.getCheckpoints().size + "c";
    }
}
