package com.hung.combat.system;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.Damage;
import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.entity.enemy.Enemy;
import com.hung.physics.Hitboxes;
import com.hung.world.World;

/**
 * Enemies hurt the player by touching their body ({@link Enemy#getContactDamage()}), on top of
 * their own attacks (AttackSystem).
 * Reads: ColliderComponent, HealthComponent. Writes: the player's HealthComponent (through Damage).
 */
public class ContactDamageSystem {

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
        if (playerHealth == null || playerHealth.isDead() || playerHealth.invulnerableTime > 0f) return;

        Hitboxes.body(player, playerBox);
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            if (!(entities.get(i) instanceof Enemy enemy) || enemy.getCollider() == null) continue;
            HealthComponent enemyHealth = enemy.getHealth();
            if (enemyHealth != null && enemyHealth.isDead()) continue;
            if (!Hitboxes.body(enemy, enemyBox).overlaps(playerBox)) continue;
            Damage.apply(player, enemy.getContactDamage(), enemy.getTransform().position.x);
            return;
        }
    }
}
