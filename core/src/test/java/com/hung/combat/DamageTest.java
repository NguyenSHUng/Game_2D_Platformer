package com.hung.combat;

import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageTest {

    private static Entity target(int maxHealth) {
        Entity entity = new Entity();
        HealthComponent health = new HealthComponent(maxHealth);
        health.invulnerability = 0.5f;
        entity.setHealth(health);
        return entity;
    }

    @Test
    void hitReducesHealthAndStartsHurtAndInvulnerability() {
        Entity entity = target(30);
        assertTrue(Damage.apply(entity, 12, -10f));
        HealthComponent health = entity.getHealth();
        assertEquals(18, health.getHealth());
        assertFalse(health.isDead());
        assertEquals(0.3f, health.hurtTime, 1e-6f);
        assertEquals(0.5f, health.invulnerableTime, 1e-6f);
    }

    @Test
    void lethalHitClampsAtZeroAndKills() {
        Entity entity = target(10);
        assertTrue(Damage.apply(entity, 25, 0f));
        HealthComponent health = entity.getHealth();
        assertEquals(0, health.getHealth());
        assertTrue(health.isDead());
        assertEquals(0f, health.hurtTime);
        assertEquals(0f, health.deadTime);
    }

    @Test
    void hitsAreIgnoredWhileInvulnerableOrDead() {
        Entity entity = target(30);
        Damage.apply(entity, 5, 0f);
        assertFalse(Damage.apply(entity, 5, 0f), "invulnerable right after a hit");
        assertEquals(25, entity.getHealth().getHealth());

        Entity dead = target(5);
        Damage.apply(dead, 5, 0f);
        dead.getHealth().invulnerableTime = 0f;
        assertFalse(Damage.apply(dead, 5, 0f), "already dead");
    }

    @Test
    void knockbackPushesAwayFromTheSourceScaledByResistance() {
        Entity entity = target(100);
        entity.getHealth().knockbackResistance = 0.5f;
        MovementComponent movement = new MovementComponent();
        movement.isGrounded = true;
        movement.jumpSpeed = 200f;
        entity.setMovement(movement);
        entity.getTransform().position.set(10f, 0f);

        Damage.apply(entity, 1, 50f);
        assertEquals(-45f, entity.getTransform().velocity.x, 1e-4f);
        assertEquals(35f, entity.getTransform().velocity.y, 1e-4f);
    }
}
