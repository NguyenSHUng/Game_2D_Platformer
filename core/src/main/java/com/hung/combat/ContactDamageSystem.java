package com.hung.combat;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.World;

/**
 * Enemies hurt the player by touching them (until enemies get attack AI).
 * Reads: ColliderComponent, HealthComponent. Writes: the player's HealthComponent (through Damage).
 */
public class ContactDamageSystem {

    /** Damage per touch; placeholder until enemy definitions carry stats (docs/04-GAME-DESIGN.md). */
    public static final int ENEMY_CONTACT_DAMAGE = 10;

    private final World world;
    // Reused every frame to avoid allocating in the game loop
    private final Rectangle playerBox = new Rectangle();
    private final Rectangle enemyBox = new Rectangle();

    public ContactDamageSystem(World world) {
        this.world = world;
    }

    public void update() {
        Entity player = world.getPlayer();
        if (player == null || player.getCollider() == null) return;
        HealthComponent playerHealth = player.getHealth();
        if (playerHealth == null || playerHealth.isDead || playerHealth.invulnerableTime > 0f) return;

        Hitboxes.body(player, playerBox);
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity enemy = entities.get(i);
            if (enemy == player || enemy.getCollider() == null || enemy.getPatrol() == null) continue;
            HealthComponent enemyHealth = enemy.getHealth();
            if (enemyHealth != null && enemyHealth.isDead) continue;
            if (!Hitboxes.body(enemy, enemyBox).overlaps(playerBox)) continue;
            Damage.apply(player, ENEMY_CONTACT_DAMAGE, enemy.getTransform().position.x);
            return;
        }
    }
}
