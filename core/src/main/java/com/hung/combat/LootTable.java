package com.hung.combat;

/**
 * What a chest gives, by tier: one item, chosen by weight from a roll that depends only on the
 * chest's stable id, so the same chest always holds the same item (a continued run included).
 */
public record LootTable(Weights wood, Weights gold) {

    /** Chance of each item, in parts (they need not add up to 100). */
    public record Weights(int healthPack, int pistolAmmo, int rifleAmmo) {

        int total() {
            return healthPack + pistolAmmo + rifleAmmo;
        }
    }

    /** Wood: health 40 %, pistol rounds 40 %, rifle rounds 20 %. Gold: 30 %, 20 %, 50 %. */
    public static final LootTable DEFAULT = new LootTable(new Weights(40, 40, 20), new Weights(30, 20, 50));

    public PickupType pick(String chestId, ChestTier tier) {
        Weights weights = tier == ChestTier.GOLD ? gold : wood;
        int roll = roll(chestId, weights.total());
        if (roll < weights.healthPack) return PickupType.HEALTH_PACK;
        if (roll < weights.healthPack + weights.pistolAmmo) return PickupType.PISTOL_AMMO;
        return PickupType.RIFLE_AMMO;
    }

    /**
     * 0 … bound−1 from the id alone (String.hashCode is fixed by the language), spread by SplitMix64 so
     * neighbouring ids ("…#66", "…#67") don't land on neighbouring rolls.
     */
    static int roll(String chestId, int bound) {
        long z = chestId.hashCode() + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (int) Long.remainderUnsigned(z, bound);
    }
}
