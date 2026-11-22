package com.hung.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthComponentTest {

    @Test
    void startsFullAndAlive() {
        HealthComponent health = new HealthComponent(40);
        assertEquals(40, health.getHealth());
        assertEquals(40, health.getMaxHealth());
        assertFalse(health.isDead());
    }

    @Test
    void rejectsANonPositiveMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new HealthComponent(0));
    }

    @Test
    void damageNeverGoesBelowZeroAndKillsAtZero() {
        HealthComponent health = new HealthComponent(10);
        assertFalse(health.applyDamage(4));
        assertEquals(6, health.getHealth());
        assertTrue(health.applyDamage(100));
        assertEquals(0, health.getHealth());
        assertTrue(health.isDead());
    }

    @Test
    void negativeDamageDoesNotHeal() {
        HealthComponent health = new HealthComponent(10);
        health.applyDamage(3);
        health.applyDamage(-5);
        assertEquals(7, health.getHealth());
    }

    @Test
    void restoreFullRevives() {
        HealthComponent health = new HealthComponent(10);
        health.applyDamage(10);
        health.restoreFull();
        assertEquals(10, health.getHealth());
        assertFalse(health.isDead());
    }

    @Test
    void setHealthClampedStaysInRangeAndKeepsDeathConsistent() {
        HealthComponent health = new HealthComponent(100);
        health.setHealthClamped(250);
        assertEquals(100, health.getHealth());
        health.setHealthClamped(72);
        assertEquals(72, health.getHealth());
        assertFalse(health.isDead());
        health.setHealthClamped(-3);
        assertEquals(0, health.getHealth());
        assertTrue(health.isDead());
        health.setHealthClamped(5);
        assertFalse(health.isDead());
    }
}
