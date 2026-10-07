package com.hung.combat;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.hung.animation.AnimationFrames;
import com.hung.component.AnimationComponent;
import com.hung.component.AttackComponent;
import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;

/**
 * Melee attacks driven by the ATTACK animation:
 *  - a request starts the attack (AnimationSystem then plays ATTACK);
 *  - on frames with an attack box (stretched to the held blade's tip) the box damages the other side (player vs enemies)
 *    and breaks BREAKABLE tiles it touches, at most once per swing;
 *  - the attack ends when the animation has played.
 * Reads: AttackComponent, AnimationComponent, ColliderComponent. Writes: AttackComponent,
 * HealthComponent (through Damage), the tile grid and the map's terrain layer.
 */
public class AttackSystem {

    private final World world;
    private final TiledMapTileLayer terrainLayer;
    // Reused every frame to avoid allocating in the game loop
    private final Rectangle attackBox = new Rectangle();
    private final Rectangle targetBox = new Rectangle();

    public AttackSystem(World world) {
        this.world = world;
        MapLayer layer = world.getLevel().getMap().getLayers().get(Level.TERRAIN_LAYER);
        this.terrainLayer = layer instanceof TiledMapTileLayer ? (TiledMapTileLayer) layer : null;
    }

    public void update() {
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            AttackComponent attack = entity.getAttack();
            AnimationComponent animation = entity.getAnimation();
            if (attack == null || animation == null) continue;

            if (attack.isRequested) {
                attack.isRequested = false;
                if (!attack.isAttacking && animation.animations.containsKey(attack.animation) && canAct(entity)) {
                    attack.isAttacking = true;
                    attack.hasHit = false;
                }
            }
            // Wait until AnimationSystem has switched to this attack's animation
            if (!attack.isAttacking || animation.currentType != attack.animation) continue;

            if (AnimationFrames.isFinished(animation, attack.animation)) {
                attack.isAttacking = false;
                continue;
            }
            if (attack.hasHit || !Hitboxes.attack(entity, attackBox)) continue;
            boolean hasHitTarget = hitTargets(entity, attack);
            // Only the player breaks walls (enemies must not open secret rooms)
            boolean hasBrokenTile = entity == world.getPlayer() && breakTiles();
            attack.hasHit = hasHitTarget || hasBrokenTile;
        }
    }

    private static boolean canAct(Entity entity) {
        HealthComponent health = entity.getHealth();
        return health == null || (!health.isDead && health.hurtTime <= 0f);
    }

    /** Damages every living entity on the other side whose body touches the attack box. */
    private boolean hitTargets(Entity attacker, AttackComponent attack) {
        Entity player = world.getPlayer();
        boolean isPlayerAttacking = attacker == player;
        boolean hasHit = false;
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity target = entities.get(i);
            if (target == attacker || target.getCollider() == null || target.getHealth() == null) continue;
            if ((target == player) == isPlayerAttacking) continue;
            if (!Hitboxes.body(target, targetBox).overlaps(attackBox)) continue;
            hasHit |= Damage.apply(target, attack.damage, attacker.getTransform().position.x);
        }
        return hasHit;
    }

    /** Clears BREAKABLE tiles under the attack box from the grid and the drawn terrain. */
    private boolean breakTiles() {
        TileGrid grid = world.getLevel().getGrid();
        boolean hasBroken = false;
        int toColumn = grid.toTile(attackBox.x + attackBox.width);
        int toRow = grid.toTile(attackBox.y + attackBox.height);
        for (int row = grid.toTile(attackBox.y); row <= toRow; row++) {
            for (int column = grid.toTile(attackBox.x); column <= toColumn; column++) {
                if (grid.get(column, row) != TileType.BREAKABLE) continue;
                grid.set(column, row, TileType.EMPTY);
                if (terrainLayer != null) terrainLayer.setCell(column, row, null);
                hasBroken = true;
            }
        }
        return hasBroken;
    }
}
