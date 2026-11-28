package com.hung.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HealthComponent.heal keeps 0 ≤ health ≤ max and never revives. */
class HealthComponentHealTest {

    @Test
    void healsByTheAmountAndReportsIt() {
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(40);
        assertEquals(25, health.heal(25));
        assertEquals(85, health.getHealth());
    }

    @Test
    void neverPastMaxHealth() {
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(10);
        assertEquals(10, health.heal(25));
        assertEquals(100, health.getHealth());
    }

    @Test
    void fullHealthTakesNothing() {
        HealthComponent health = new HealthComponent(100);
        assertEquals(0, health.heal(25));
        assertEquals(100, health.getHealth());
    }

    @Test
    void theDeadStayDead() {
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(100);
        assertEquals(0, health.heal(25));
        assertEquals(0, health.getHealth());
        assertTrue(health.isDead());
    }

    @Test
    void zeroOrNegativeAmountsDoNothing() {
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(50);
        assertEquals(0, health.heal(0));
        assertEquals(0, health.heal(-20));
        assertEquals(50, health.getHealth());
        assertFalse(health.isDead());
    }

    @Test
    void healingLeavesTheHitReactionAlone() {
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(30);
        health.hurtTime = 0.3f;
        health.invulnerableTime = 0.5f;
        health.heal(25);
        assertEquals(0.3f, health.hurtTime, 1e-6);
        assertEquals(0.5f, health.invulnerableTime, 1e-6);
    }
}
