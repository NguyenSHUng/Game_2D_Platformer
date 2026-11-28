package com.hung.ai.tactic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/** Which tactic a policy gives each region, and a fresh one every time. */
class TacticPolicyTest {

    private static final String[] REGIONS = {"region_1", "region_2", "region_3", "region_4"};

    @Test
    void directOnlyChasesDirectlyEverywhere() {
        for (String region : REGIONS) assertInstanceOf(DirectChaseTactic.class, TacticPolicy.DIRECT_ONLY.create(region));
        assertInstanceOf(DirectChaseTactic.class, TacticPolicy.DIRECT_ONLY.create(null));
    }

    @Test
    void hitAndRunFromItsRegionOn() {
        TacticPolicy policy = new TacticPolicy(3);
        assertInstanceOf(DirectChaseTactic.class, policy.create("region_1"));
        assertInstanceOf(DirectChaseTactic.class, policy.create("region_2"));
        assertInstanceOf(HitAndRunTactic.class, policy.create("region_3"));
        assertInstanceOf(HitAndRunTactic.class, policy.create("region_4"));
    }

    @Test
    void unknownRegionsChaseDirectly() {
        TacticPolicy policy = new TacticPolicy(1);
        for (String region : new String[] {null, "", "region_", "region_x", "region_-2", "zone_3", "REGION_3"}) {
            assertInstanceOf(DirectChaseTactic.class, policy.create(region), String.valueOf(region));
        }
    }

    @Test
    void everyEnemyGetsItsOwnInstance() {
        TacticPolicy policy = new TacticPolicy(3);
        assertNotSame(policy.create("region_4"), policy.create("region_4"));
        assertNotSame(policy.create("region_1"), policy.create("region_1"));
    }

    @Test
    void readsTheRegionNumber() {
        assertEquals(1, TacticPolicy.regionNumber("region_1"));
        assertEquals(12, TacticPolicy.regionNumber("region_12"));
        assertEquals(0, TacticPolicy.regionNumber("region_abc"));
        assertEquals(0, TacticPolicy.regionNumber(null));
    }
}
