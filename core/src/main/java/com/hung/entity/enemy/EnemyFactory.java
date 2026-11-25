package com.hung.entity.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.hung.animation.AnimationType;
import com.hung.component.AnimationComponent;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.util.ResourceLoader;
import com.hung.world.EnemySpawn;

/**
 * Builds enemies from the enemy generator's exports (build_asset/build_enemy). The file's
 * archetype picks the class (see {@link #newEnemy}); the class holds the behaviour and its numbers,
 * this factory adds the components from the file: sprite animations with attack hitboxes, body,
 * patrol stretch, health and damage from the qualitative classes (docs/04-GAME-DESIGN.md) scaled by the mode's EnemySkill.
 */
public final class EnemyFactory {

    /** Enemies are drawn, collide and reach this much bigger than their files say. */
    private static final float SIZE_SCALE = 1.2f;
    /** Files without a durabilityClass (small_melee_*) get this health. */
    private static final int DEFAULT_ENEMY_HEALTH = 30;
    private static final int HEALTH_LOW = 20;
    private static final int HEALTH_MEDIUM = 40;
    /** Attack damage per damageClass (player: 100 HP), before EnemySkill. */
    private static final int DAMAGE_LOW = 8;
    private static final int DAMAGE_MEDIUM = 12;
    private static final int ENEMY_COLLIDER_INSET = 4;
    private static final float MIN_COLLIDER_SIZE = 4f;
    private static final float FRAME_TICK_SECONDS = 0.01f;
    private static final float MILLIS_PER_SECOND = 1000f;
    /** Starts an attack when the player is within this share of the measured reach… */
    private static final float ATTACK_RANGE_RATIO = 0.8f;
    /** …plus about half the player's body width (the hitbox only has to touch the body). */
    private static final float TARGET_HALF_WIDTH = 4f;
    private static final float DEFAULT_ATTACK_RANGE = 16f;

    private EnemyFactory() {
    }

    /** Image of an enemy's sprite sheet, next to its character.json. */
    public static String sheetPath(EnemyDefinition definition, String definitionPath) {
        int slash = definitionPath.lastIndexOf('/');
        String folder = slash >= 0 ? definitionPath.substring(0, slash + 1) : "";
        return folder + definition.spriteSheet.image;
    }

    /** Queues the enemy's sprite sheet. */
    public static void queueTextures(EnemyDefinition definition, String definitionPath, ResourceLoader resources) {
        resources.queueTexture(sheetPath(definition, definitionPath));
    }

    /**
     * Enemy at a spawn. Ground enemies stand with their feet on spawn.y and walk at the MOVE
     * animation's ground speed; flyers hover with their body bottom at spawn.y.
     */
    public static Enemy createEnemy(EnemyDefinition definition, String definitionPath, ResourceLoader resources,
                                    EnemySpawn spawn, EnemySkill skill) {
        float scale = (definition.drawScale > 0f ? definition.drawScale : 1f) * SIZE_SCALE;
        Enemy entity = newEnemy(definition, spawn);
        entity.setSkill(skill);
        entity.getTransform().position.set(spawn.x, spawn.y);
        entity.getTransform().isFacingRight = spawn.isFacingRight;

        MovementComponent movement = new MovementComponent();
        EnemyDefinition.Animation move = definition.animations.get("move");
        float speed = move != null && move.move != null ? Math.max(move.move.groundSpeed, move.move.airSpeed) : 0f;
        movement.moveSpeed = speed * scale;
        movement.isFlying = entity instanceof FlyingEnemy;
        entity.setMovement(movement);

        EnemyDefinition.Bounds bounds = definition.animations.get("idle").frames.first().bounds;
        ColliderComponent collider = new ColliderComponent();
        collider.width = Math.max(MIN_COLLIDER_SIZE, (bounds.width - ENEMY_COLLIDER_INSET) * scale);
        collider.height = Math.max(MIN_COLLIDER_SIZE, bounds.height * scale);
        entity.setCollider(collider);

        PatrolComponent patrol = new PatrolComponent();
        patrol.minX = spawn.patrolMinX + collider.width / 2f;
        patrol.maxX = spawn.patrolMaxX - collider.width / 2f;
        patrol.direction = spawn.isFacingRight ? 1 : -1;
        entity.setPatrol(patrol);
        entity.setHealth(createHealth(definition, entity));

        Texture sheet = resources.getTexture(sheetPath(definition, definitionPath));
        AnimationComponent animation = createAnimation(definition, sheet, scale, bounds);
        animation.strideSpeed = movement.moveSpeed;
        entity.setAnimation(animation);

        entity.setAttack(createAttack(definition, skill));
        return entity;
    }

    private static AnimationComponent createAnimation(EnemyDefinition definition, Texture sheet, float scale,
                                                      EnemyDefinition.Bounds idleBounds) {
        AnimationComponent animation = new AnimationComponent();
        animation.drawScale = scale;
        // The ground line: feet (phase 1), footAnchor (phase 2 ground), else the idle body bottom (flyers)
        EnemyDefinition.Point ground = definition.anchors.feet != null ? definition.anchors.feet : definition.anchors.footAnchor;
        int groundRow = ground != null ? ground.y : idleBounds.y + idleBounds.height - 1;
        animation.footOffset = definition.spriteSheet.frameHeight - 1 - groundRow;

        for (ObjectMap.Entry<String, EnemyDefinition.Animation> entry : definition.animations) {
            AnimationType type = toAnimationType(entry.value.state);
            if (type == null) continue;
            // Typed backing array: Animation copies it, and getKeyFrames() must return TextureRegion[]
            Array<TextureRegion> keyFrames = new Array<>(true, entry.value.frames.size, TextureRegion[]::new);
            IntArray sources = new IntArray();
            Rectangle[] hitboxes = new Rectangle[entry.value.frames.size];
            boolean hasHitbox = false;
            for (int f = 0; f < entry.value.frames.size; f++) {
                EnemyDefinition.Frame frame = entry.value.frames.get(f);
                TextureRegion region = new TextureRegion(sheet, frame.x, frame.y, frame.width, frame.height);
                int ticks = Math.max(1, Math.round(frame.duration / MILLIS_PER_SECOND / FRAME_TICK_SECONDS));
                for (int i = 0; i < ticks; i++) {
                    keyFrames.add(region);
                    sources.add(f);
                }
                if (frame.attackActive && frame.hitbox != null) {
                    hitboxes[f] = new Rectangle(frame.hitbox.x, frame.hitbox.y, frame.hitbox.width, frame.hitbox.height);
                    hasHitbox = true;
                }
            }
            Animation.PlayMode mode = entry.value.loop ? Animation.PlayMode.LOOP : Animation.PlayMode.NORMAL;
            animation.getAnimations().put(type, new Animation<>(FRAME_TICK_SECONDS, keyFrames, mode));
            animation.getKeyFrameSources().put(type, sources.toArray());
            if (hasHitbox) animation.getAttackBoxes().put(type, hitboxes);
        }
        if (!animation.getAnimations().containsKey(AnimationType.IDLE)) {
            throw new IllegalArgumentException("Enemy '" + definition.id + "' needs an IDLE animation");
        }
        return animation;
    }

    /** Enemy generator state name → game animation; null for states the game doesn't use. */
    private static AnimationType toAnimationType(String state) {
        if ("MOVE".equals(state)) return AnimationType.WALK;
        if ("ATTACK_1".equals(state)) return AnimationType.ATTACK;
        try {
            return AnimationType.valueOf(state);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /** The class for this enemy, from the file's archetype. */
    private static Enemy newEnemy(EnemyDefinition definition, EnemySpawn spawn) {
        float attackRange = attackRange(definition);
        return switch (definition.archetype) {
            case SMALL_MELEE -> new MeleeEnemy(attackRange);
            case CRAWLER -> new CrawlerEnemy(attackRange);
            case FLYING -> new FlyingEnemy(attackRange, spawn.y);
        };
    }

    /** 80% of the shortest reach the enemy tool measured, plus half the player's width. */
    private static float attackRange(EnemyDefinition definition) {
        if (definition.gameplay == null || definition.gameplay.attacks == null) return DEFAULT_ATTACK_RANGE;
        float shortest = Float.MAX_VALUE;
        for (EnemyDefinition.AttackInfo info : definition.gameplay.attacks.values()) {
            if (info.reach != null) shortest = Math.min(shortest, info.reach.world);
        }
        return shortest < Float.MAX_VALUE ? shortest * SIZE_SCALE * ATTACK_RANGE_RATIO + TARGET_HALF_WIDTH : DEFAULT_ATTACK_RANGE;
    }

    private static HealthComponent createHealth(EnemyDefinition definition, Enemy enemy) {
        String durability = definition.gameplay != null ? definition.gameplay.durabilityClass : null;
        HealthComponent health = new HealthComponent(enemy.getSkill().health("MEDIUM".equals(durability) ? HEALTH_MEDIUM
            : "LOW".equals(durability) ? HEALTH_LOW : DEFAULT_ENEMY_HEALTH));
        health.knockbackResistance = enemy.getKnockbackResistance();
        return health;
    }

    private static AttackComponent createAttack(EnemyDefinition definition, EnemySkill skill) {
        AttackComponent attack = new AttackComponent();
        String damage = definition.gameplay != null ? definition.gameplay.damageClass : null;
        attack.damage = skill.damage("MEDIUM".equals(damage) ? DAMAGE_MEDIUM : DAMAGE_LOW);
        return attack;
    }
}
