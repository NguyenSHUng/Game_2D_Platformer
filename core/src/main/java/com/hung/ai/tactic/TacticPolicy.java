package com.hung.ai.tactic;

/**
 * Which tactic a mode's hunters use in each region of the map (region_1 … region_4, the map tool's
 * progression from the start to the exit). A fresh instance per enemy, since tactics keep state.
 * Enemies whose region is unknown (no rooms, a room without a region) chase directly.
 *
 * @param hitAndRunFromRegion first region whose hunters hit and run; 0 = never
 */
public record TacticPolicy(int hitAndRunFromRegion) {

    public static final TacticPolicy DIRECT_ONLY = new TacticPolicy(0);
    private static final String REGION_PREFIX = "region_";

    public HuntTactic create(String regionId) {
        int region = regionNumber(regionId);
        boolean isHitAndRun = hitAndRunFromRegion > 0 && region >= hitAndRunFromRegion;
        return isHitAndRun ? new HitAndRunTactic() : new DirectChaseTactic();
    }

    /** "region_3" → 3; 0 for null or any other name. */
    static int regionNumber(String regionId) {
        if (regionId == null || !regionId.startsWith(REGION_PREFIX)) return 0;
        try {
            return Math.max(0, Integer.parseInt(regionId.substring(REGION_PREFIX.length())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
