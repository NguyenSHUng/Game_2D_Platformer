package com.hung.combat;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.HealthComponent;
import com.hung.component.ProjectileComponent;
import com.hung.component.SpriteComponent;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.TileGrid;
import com.hung.world.World;

/**
 * Bullets: moved in short steps so they never tunnel through a tile or a body. A bullet stops at
 * the first SOLID / BREAKABLE tile (one-way platforms and ladders let it through) or the first
 * living body of the other side (player ↔ everyone else), which takes the damage; an impact
 * effect plays where it stops. It vanishes after its range.
 * Reads/Writes: ProjectileComponent, TransformComponent; HealthComponent through Damage.
 */
public class ProjectileSystem {

    /** Longest step per collision test, world units (bodies are ≥ 7 units wide). */
    private static final float STEP = 2f;
    /** Sparks light a small circle while they last, world units. */
    private static final float IMPACT_LIGHT_RADIUS = 20f;

    private final World world;
    // Reused every frame to avoid allocating in the game loop
    private final Vector2 step = new Vector2();
    private final Rectangle body = new Rectangle();

    public ProjectileSystem(World world) {
        this.world = world;
    }

    public void update(float delta) {
        Array<Entity> entities = world.getEntities();
        // Backwards: hits remove bullets, impacts are appended at the end
        for (int i = entities.size - 1; i >= 0; i--) {
            if (i >= entities.size) continue;
            Entity bullet = entities.get(i);
            ProjectileComponent projectile = bullet.getProjectile();
            if (projectile == null) continue;
            if (fly(bullet, projectile, delta)) world.removeEntity(bullet);
        }
    }

    /** Moves one bullet; true when it is spent. */
    private boolean fly(Entity bullet, ProjectileComponent projectile, float delta) {
        Vector2 position = bullet.getTransform().position;
        Vector2 velocity = bullet.getTransform().velocity;
        float distance = velocity.len() * delta;
        int steps = Math.max(1, MathUtils.ceil(distance / STEP));
        step.set(velocity).scl(delta / steps);
        TileGrid grid = world.getLevel().getGrid();
        for (int s = 0; s < steps; s++) {
            position.add(step);
            projectile.rangeLeft -= step.len();
            if (grid.get(grid.toTile(position.x), grid.toTile(position.y)).isSolid()) {
                impact(bullet, projectile);
                return true;
            }
            Entity target = findTarget(projectile, position);
            if (target != null) {
                Damage.apply(target, projectile.damage, position.x - Math.signum(velocity.x));
                impact(bullet, projectile);
                return true;
            }
            if (projectile.rangeLeft <= 0f) return true;
        }
        return false;
    }

    private Entity findTarget(ProjectileComponent projectile, Vector2 point) {
        Entity player = world.getPlayer();
        boolean isPlayerShot = projectile.owner == player;
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity target = entities.get(i);
            if (target == projectile.owner || target.getCollider() == null) continue;
            HealthComponent health = target.getHealth();
            if (health == null || health.isDead()) continue;
            if ((target == player) == isPlayerShot) continue;
            if (Hitboxes.body(target, body).contains(point)) return target;
        }
        return null;
    }

    private void impact(Entity bullet, ProjectileComponent projectile) {
        SpriteComponent sprite = bullet.getSprite();
        float scale = sprite != null ? sprite.scale : 1f;
        Vector2 at = bullet.getTransform().position;
        Effects.light(Effects.spawn(world, projectile.impact, at.x, at.y, 0f, true, scale), IMPACT_LIGHT_RADIUS, 1f, 0.85f, 0.55f);
    }
}
