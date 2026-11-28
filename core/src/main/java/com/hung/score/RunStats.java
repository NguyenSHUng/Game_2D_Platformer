package com.hung.score;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The score and statistics of one run. Every scoring event is kept by its stable id (enemy spawn,
 * chest, room), so each counts once: an enemy that comes back on Continue scores nothing the second
 * time. The counts are kept apart from the ids, so a save of a re-exported map can drop the ids (they
 * name objects of the old export) and keep the totals. Only ScoreKeeper adds to it; SaveSnapshot restores it.
 */
public class RunStats {

    private int score;
    private int kills;
    private int chestsOpened;
    private int roomsCleared;
    private boolean isMapComplete;
    private final Set<String> defeatedEnemies = new LinkedHashSet<>();
    private final Set<String> openedChests = new LinkedHashSet<>();
    private final Set<String> clearedRooms = new LinkedHashSet<>();

    public int getScore() { return score; }
    /** Different enemies defeated (an enemy defeated again after Continue counts once). */
    public int getKills() { return kills; }
    public int getChestsOpened() { return chestsOpened; }
    public int getRoomsCleared() { return roomsCleared; }
    public boolean isMapComplete() { return isMapComplete; }

    public Set<String> getDefeatedEnemies() { return Collections.unmodifiableSet(defeatedEnemies); }
    public Set<String> getOpenedChests() { return Collections.unmodifiableSet(openedChests); }
    public Set<String> getClearedRooms() { return Collections.unmodifiableSet(clearedRooms); }

    /**
     * Back to a saved state: the totals, and the ids already scored (null = none, e.g. a save of another
     * map export or from before ids were saved).
     */
    public void restore(int score, int kills, int chestsOpened, int roomsCleared, boolean isMapComplete,
                        String[] defeatedEnemies, String[] openedChests, String[] clearedRooms) {
        this.score = Math.max(0, score);
        this.kills = Math.max(0, kills);
        this.chestsOpened = Math.max(0, chestsOpened);
        this.roomsCleared = Math.max(0, roomsCleared);
        this.isMapComplete = isMapComplete;
        refill(this.defeatedEnemies, defeatedEnemies);
        refill(this.openedChests, openedChests);
        refill(this.clearedRooms, clearedRooms);
    }

    /** True (and points added) the first time this enemy is defeated. */
    boolean addKill(String spawnId, int points) {
        if (!add(defeatedEnemies, spawnId, points)) return false;
        kills++;
        return true;
    }

    boolean addChest(String chestId, int points) {
        if (!add(openedChests, chestId, points)) return false;
        chestsOpened++;
        return true;
    }

    boolean addRoom(String roomId, int points) {
        if (!add(clearedRooms, roomId, points)) return false;
        roomsCleared++;
        return true;
    }

    boolean completeMap(int points) {
        if (isMapComplete) return false;
        isMapComplete = true;
        score += points;
        return true;
    }

    private boolean add(Set<String> ids, String id, int points) {
        if (id == null || !ids.add(id)) return false;
        score += points;
        return true;
    }

    private static void refill(Set<String> set, String[] ids) {
        set.clear();
        if (ids == null) return;
        for (String id : ids) if (id != null) set.add(id);
    }
}
