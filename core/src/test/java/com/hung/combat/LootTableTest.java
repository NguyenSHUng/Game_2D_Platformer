package com.hung.combat;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The loot of a chest depends only on its id and tier, with the agreed chances. */
class LootTableTest {

    private static Map<PickupType, Integer> share(ChestTier tier, int chests) {
        Map<PickupType, Integer> counts = new EnumMap<>(PickupType.class);
        for (int i = 0; i < chests; i++) {
            counts.merge(LootTable.DEFAULT.pick("maps/test/map.tmx#" + i, tier), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void theAgreedChances() {
        assertEquals(new LootTable.Weights(40, 40, 20), LootTable.DEFAULT.wood());
        assertEquals(new LootTable.Weights(30, 20, 50), LootTable.DEFAULT.gold());
    }

    @Test
    void theSameChestAlwaysHoldsTheSameItem() {
        String id = "maps/bunker_medium/map_medium.tmx#57";
        PickupType first = LootTable.DEFAULT.pick(id, ChestTier.GOLD);
        for (int i = 0; i < 5; i++) {
            LootTable fresh = new LootTable(new LootTable.Weights(30, 20, 50), new LootTable.Weights(30, 20, 50));
            assertSame(first, fresh.pick(id, ChestTier.GOLD));
        }
    }

    @Test
    void manyChestsFollowTheChances() {
        int chests = 20_000;
        Map<PickupType, Integer> wood = share(ChestTier.WOOD, chests);
        assertEquals(0.40, wood.get(PickupType.HEALTH_PACK) / (double) chests, 0.02);
        assertEquals(0.40, wood.get(PickupType.PISTOL_AMMO) / (double) chests, 0.02);
        assertEquals(0.20, wood.get(PickupType.RIFLE_AMMO) / (double) chests, 0.02);
        Map<PickupType, Integer> gold = share(ChestTier.GOLD, chests);
        assertEquals(0.30, gold.get(PickupType.HEALTH_PACK) / (double) chests, 0.02);
        assertEquals(0.20, gold.get(PickupType.PISTOL_AMMO) / (double) chests, 0.02);
        assertEquals(0.50, gold.get(PickupType.RIFLE_AMMO) / (double) chests, 0.02);
    }

    /** Map object ids run in sequence; their rolls must not (uniform pairs differ by 33 on average). */
    @Test
    void neighbouringChestsDoNotShareTheirLuck() {
        double total = 0;
        int pairs = 5000;
        for (int i = 0; i < pairs; i++) {
            total += Math.abs(LootTable.roll("maps/x/map.tmx#" + i, 100) - LootTable.roll("maps/x/map.tmx#" + (i + 1), 100));
        }
        assertEquals(33.3, total / pairs, 3.0);
    }

    @Test
    void aZeroWeightNeverComes() {
        LootTable onlyHealth = new LootTable(new LootTable.Weights(1, 0, 0), new LootTable.Weights(0, 0, 3));
        for (int i = 0; i < 200; i++) {
            assertSame(PickupType.HEALTH_PACK, onlyHealth.pick("c" + i, ChestTier.WOOD));
            assertSame(PickupType.RIFLE_AMMO, onlyHealth.pick("c" + i, ChestTier.GOLD));
        }
    }

    @Test
    void rollsStayInRange() {
        for (int i = 0; i < 1000; i++) {
            int roll = LootTable.roll("chest" + i, 7);
            assertTrue(roll >= 0 && roll < 7);
        }
    }

    @Test
    void tiersFromTheMapFile() {
        assertSame(ChestTier.GOLD, ChestTier.fromName("gold"));
        assertSame(ChestTier.GOLD, ChestTier.fromName("GOLD"));
        assertSame(ChestTier.WOOD, ChestTier.fromName("wood"));
        assertSame(ChestTier.WOOD, ChestTier.fromName(null));
        assertSame(ChestTier.WOOD, ChestTier.fromName("silver"));
    }
}
