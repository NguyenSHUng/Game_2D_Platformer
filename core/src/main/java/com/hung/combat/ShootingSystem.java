package com.hung.combat;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.animation.AnimationFrames;
import com.hung.animation.AnimationType;
import com.hung.component.AnimationComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.EffectComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.ProjectileComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.World;

/**
 * Firearms, driven by the weapon's "fire" data (weapon generator JSON):
 *  - a trigger pull fires when the cooldown is over and the magazine isn't empty; an empty
 *    magazine starts a reload instead; R reloads early; a reload refills after reloadSeconds;
 *  - a shot spawns a projectile at the muzzle (world position of the weapon's muzzle anchor,
 *    along the weapon's angle ± spread), a muzzle flash and a casing from the ejection port,
 *    and keeps the SHOOT pose up for one SHOOT animation.
 * Reads: WeaponComponent (+ its Weapon), HealthComponent, MovementComponent. Writes: WeaponComponent,
 * new projectile / effect / casing entities.
 */
public class ShootingSystem {

    /** Pose time after a shot when the entity has no SHOOT animation, seconds. */
    private static final float DEFAULT_SHOOT_TIME = 0.25f;
    /** Casings fall for this long, then disappear, seconds. */
    private static final float CASING_LIFETIME = 1.5f;
    /** Random extra casing speed, as a fraction. */
    private static final float CASING_SPEED_JITTER = 0.3f;
    private static final float CASING_SIZE = 1f;
    private static final float MIN_ROUNDS_PER_SECOND = 0.1f;
    /** A shot lights the surroundings for the flash's length, world units. */
    private static final float MUZZLE_LIGHT_RADIUS = 56f;

    private final World world;
    // Reused every shot to avoid allocating in the game loop
    private final Vector2 muzzle = new Vector2();
    private final Vector2 port = new Vector2();

    public ShootingSystem(World world) {
        this.world = world;
    }

    public void update(float delta) {
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            WeaponComponent weapon = entity.getWeapon();
            if (weapon == null || weapon.equipped == null || !weapon.equipped.isFirearm()) continue;
            FireDefinition fire = weapon.equipped.getFire();

            weapon.cooldown = Math.max(0f, weapon.cooldown - delta);
            weapon.shootTime = Math.max(0f, weapon.shootTime - delta);
            if (weapon.reloadTime > 0f) {
                weapon.reloadTime -= delta;
                if (weapon.reloadTime <= 0f) {
                    weapon.reloadTime = 0f;
                    weapon.ammo = fire.magazine;
                }
            }

            boolean canAct = canAct(entity);
            if (weapon.isReloadRequested) {
                weapon.isReloadRequested = false;
                if (canAct && weapon.reloadTime <= 0f && weapon.ammo < fire.magazine) weapon.reloadTime = fire.reloadSeconds;
            }
            if (!weapon.isFireRequested) continue;
            weapon.isFireRequested = false;
            if (!canAct || weapon.cooldown > 0f || weapon.reloadTime > 0f) continue;
            if (weapon.ammo <= 0) {
                weapon.reloadTime = fire.reloadSeconds;
                continue;
            }
            shoot(entity, weapon, fire);
            if (weapon.ammo == 0) weapon.reloadTime = fire.reloadSeconds;
        }
    }

    private static boolean canAct(Entity entity) {
        HealthComponent health = entity.getHealth();
        MovementComponent movement = entity.getMovement();
        boolean isAlive = health == null || (!health.isDead() && health.hurtTime <= 0f);
        return isAlive && (movement == null || !movement.isClimbing);
    }

    private void shoot(Entity shooter, WeaponComponent weapon, FireDefinition fire) {
        Weapon gun = weapon.equipped;
        float tile = world.getLevel().getGrid().getTileSize();
        AnimationComponent animation = shooter.getAnimation();
        boolean isFacingRight = shooter.getTransform().isFacingRight;
        float scale = animation != null ? animation.drawScale : 1f;

        weapon.ammo--;
        weapon.cooldown = 1f / Math.max(MIN_ROUNDS_PER_SECOND, fire.roundsPerSecond);
        float shootDuration = animation != null ? AnimationFrames.duration(animation, AnimationType.SHOOT) : 0f;
        weapon.shootTime = shootDuration > 0f ? shootDuration : DEFAULT_SHOOT_TIME;
        if (animation != null && animation.currentType == AnimationType.SHOOT) animation.stateTime = 0f;

        if (!Hitboxes.weaponPoint(shooter, gun.getMuzzleAnchor(), muzzle)) muzzle.set(shooter.getTransform().position);
        float angle = Hitboxes.weaponAngle(shooter);
        float local = isFacingRight ? angle : 180f - angle;

        // Projectile along the weapon, within the spread cone
        float spread = MathUtils.random(-fire.spreadDegrees, fire.spreadDegrees);
        float shotAngle = angle + spread;
        Entity bullet = new Entity();
        bullet.getTransform().position.set(muzzle);
        bullet.getTransform().velocity.set(MathUtils.cosDeg(shotAngle), MathUtils.sinDeg(shotAngle)).scl(fire.projectileSpeedTiles * tile);
        bullet.getTransform().isFacingRight = isFacingRight;
        ProjectileComponent projectile = new ProjectileComponent();
        projectile.damage = fire.damage;
        projectile.owner = shooter;
        projectile.rangeLeft = fire.rangeTiles * tile;
        projectile.impact = gun.getEffect("impact");
        bullet.setProjectile(projectile);
        WeaponEffect tracer = gun.getEffect("tracer");
        if (tracer != null) bullet.setSprite(Effects.sprite(tracer, local + (isFacingRight ? spread : -spread), isFacingRight, scale));
        world.addEntity(bullet);

        Entity flash = Effects.spawn(world, gun.getEffect("muzzleFlash"), muzzle.x, muzzle.y, local, isFacingRight, scale);
        Effects.light(flash, MUZZLE_LIGHT_RADIUS, 1f, 0.78f, 0.45f);
        ejectCasing(shooter, gun, fire, tile, scale, isFacingRight);
    }

    /** A brass casing flying up and back from the ejection port, falling under gravity. */
    private void ejectCasing(Entity shooter, Weapon gun, FireDefinition fire, float tile, float scale, boolean isFacingRight) {
        WeaponEffect casing = gun.getEffect("casing");
        if (casing == null || fire.shellEject == null || !Hitboxes.weaponPoint(shooter, gun.getEjectionAnchor(), port)) return;
        float angle = isFacingRight ? fire.shellEject.angleDegrees : 180f - fire.shellEject.angleDegrees;
        float speed = fire.shellEject.speedTiles * tile * (1f + MathUtils.random(-CASING_SPEED_JITTER, CASING_SPEED_JITTER));
        Entity shell = new Entity();
        shell.getTransform().position.set(port);
        shell.getTransform().velocity.set(MathUtils.cosDeg(angle) * speed, MathUtils.sinDeg(angle) * speed);
        shell.setSprite(Effects.sprite(casing, 0f, isFacingRight, scale));
        shell.setMovement(new MovementComponent());
        ColliderComponent collider = new ColliderComponent();
        collider.width = CASING_SIZE;
        collider.height = CASING_SIZE;
        shell.setCollider(collider);
        EffectComponent life = new EffectComponent();
        life.timeLeft = CASING_LIFETIME;
        life.settlesOnGround = true;
        shell.setEffect(life);
        world.addEntity(shell);
    }
}
