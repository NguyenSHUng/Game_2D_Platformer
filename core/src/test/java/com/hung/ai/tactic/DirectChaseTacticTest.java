package com.hung.ai.tactic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The actions DirectChaseTactic picks, in order, without an enemy or LibGDX. */
class DirectChaseTacticTest {

    private static List<String> decide(HuntContext context) {
        RecordingActions recorder = new RecordingActions();
        new DirectChaseTactic().decide(context, recorder);
        return recorder.calls;
    }

    /** A frame where backing off would be safe; DirectChase never backs off. */
    private static HuntContext context(float dx, float holdDistance, boolean isInReach, boolean canStrikeNow,
                                       boolean canMoveTowardTarget) {
        return new HuntContext(dx, 20f, holdDistance, isInReach, canStrikeNow, canMoveTowardTarget, true, 1f / 60f);
    }

    @Test
    void strikesWhenInReachAndReady() {
        assertEquals(List.of("face R", "strike"), decide(context(10f, 15f, true, true, true)));
    }

    @Test
    void inReachOnCooldownWaitsInsideTheHoldDistance() {
        assertEquals(List.of("face L", "hold"), decide(context(-10f, 15f, true, false, true)));
    }

    @Test
    void approachesFromBeyondTheHoldDistance() {
        assertEquals(List.of("face L", "approach -1.0"), decide(context(-40f, 15f, false, true, true)));
        // In reach but on cooldown, still beyond the hold distance: keeps closing in
        assertEquals(List.of("face R", "approach 1.0"), decide(context(18f, 15f, true, false, true)));
    }

    @Test
    void holdsAtTheRoomEdge() {
        assertEquals(List.of("face R", "hold"), decide(context(40f, 15f, false, true, false)));
    }

    @Test
    void facesLeftWhenTheTargetIsStraightAbove() {
        assertEquals(List.of("face L", "hold"), decide(context(0f, 15f, false, true, true)));
    }
}
