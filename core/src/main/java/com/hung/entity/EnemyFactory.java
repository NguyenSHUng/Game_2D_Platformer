package com.hung.entity;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.hung.animation.AnimationType;
import com.hung.combat.WeaponEffect;
import com.hung.component.AnimationComponent;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.EnemyEffectsComponent;
import com.hung.component.EnemyBrainComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.util.ResourceLoader;
import com.hung.world.EnemySpawn;

/**
 * Enemies from the enemy generator's exports (build_asset/build_enemy):
 *  - SMALL_MELEE (phase 1): drawn 1:1, patrols its floor stretch, hurts by touch;
 *  - CRAWLER / FLYING / MINI_BOSS (phase 2): drawn at drawScale 0.5, with attack hitboxes per
 *    frame, a brain (EnemyBrainSystem: notice, chase, attack) and numbers derived here from the
 *    file's qualitative classes. Flyers hover (no gravity) and dive; the MINI_BOSS alternates its
 *    two attacks and shrugs off most knockback.
 * Numbers live here, in the game (docs/04-GAME-DESIGN.md damage scale), never in the art files.
 */
public final class EnemyFactory {

    /** Phase 1 enemies keep this health; phase 2 health comes from durabilityClass. */
    private static final int DEFAULT_ENEMY_HEALTH = 30;
    private static final int HEALTH_LOW = 20;
    private static final int HEALTH_MEDIUM = 40;
    private static final int HEALTH_HIGH = 300;
    /** Attack damage per damageClass (player: 100 HP; melee 5–15, boss 20–50). */
    private static final int DAMAGE_LOW = 8;
    private static final int DAMAGE_MEDIUM = 12;
    private static final int DAMAGE_HIGH = 25;
    private static final int ENEMY_COLLIDER_INSET = 4;
    private static final float MIN_COLLIDER_SIZE = 4f;
    private static final float FRAME_TICK_SECONDS = 0.01f;
    private static final float MILLIS_PER_SECOND = 1000f;
    /** A MAJOR_ENCOUNTER barely moves when hit. */
    private static final float BOSS_KNOCKBACK_RESISTANCE = 0.85f;

    // Brain tuning per role (world units, seconds)
    private static final float GROUND_AGGRO_HEIGHT = 48f;
    private static final float AIR_AGGRO_HEIGHT = 112f;
    private static final float CRAWLER_AGGRO = 160f;
    private static final float FLYER_AGGRO = 180f;
    private static final float BOSS_AGGRO = 220f;
    private static final float CRAWLER_COOLDOWN = 0.9f;
    private static final float FLYER_COOLDOWN = 1.4f;
    private static final float BOSS_COOLDOWN = 1.6f;
    private static final float CRAWLER_CHASE = 1.6f;
    private static final float FLYER_CHASE = 1.4f;
    private static final float BOSS_CHASE = 1.2f;
    /** Starts an attack when the player is within this share of the measured reach… */
    private static final float ATTACK_RANGE_RATIO = 0.8f;
    /** …plus about half the player's body width (the hitbox only has to touch the body). */
    private static final float TARGET_HALF_WIDTH = 4f;
    private static final float DEFAULT_ATTACK_RANGE = 16f;
    /** Flyers patrol this high above their floor and hunt this high above the player's feet. */
    public static final float FLYER_CRUISE_HEIGHT = 40f;
    private static final float FLYER_HUNT_HEIGHT = 18f;

    private EnemyFactory() {
    }

    /** Image of an enemy's sprite sheet, next to its character.json. */
    public static String sheetPath(EnemyDefinition definition, String definitionPath) {
        return besideDefinition(definition.spriteSheet.image, definitionPath);
    }

    /** A file named relative to character.json (sprite sheet, effects/<name>.png). */
    private static String besideDefinition(String file, String definitionPath) {
        int slash = definitionPath.lastIndexOf('/');
        String folder = slash >= 0 ? definitionPath.substring(0, slash + 1) : "";
        return folder + file;
    }

    /** Queues every image an enemy needs: its sprite sheet and the strips of its effects. */
    public static void queueTextures(EnemyDefinition definition, String definitionPath, ResourceLoader resources) {
        resources.queueTexture(sheetPath(definition, definitionPath));
        if (definition.effects == null) return;
        for (EnemyDefinition.Effect effect : definition.effects.values()) {
            resources.queueTexture(besideDefinition(effect.image, definitionPath));
        }
    }

    public static boolean isFlyer(EnemyDefinition definition) {
        return definition.gameplay != null && "AIR".equals(definition.gameplay.locomotion);
    }

    /**
     * Enemy at a spawn. Ground enemies stand with their feet on spawn.y and walk at the MOVE
     * animation's ground speed; flyers hover with their body bottom at spawn.y.
     */
    public static Entity createEnemy(EnemyDefinition definition, String definitionPath, ResourceLoader resources,
                                     EnemySpawn spawn) {
        float scale = definition.drawScale > 0f ? definition.drawScale : 1f;
        boolean isFlyer = isFlyer(definition);
        Entity entity = new Entity();
        entity.getTransform().position.set(spawn.x, spawn.y);
        entity.getTransform().isFacingRight = spawn.isFacingRight;

        MovementComponent movement = new MovementComponent();
        EnemyDefinition.Animation move = definition.animations.get("move");
        float speed = move != null && move.move != null ? Math.max(move.move.groundSpeed, move.move.airSpeed) : 0f;
        movement.moveSpeed = speed * scale;
        movement.isFlying = isFlyer;
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
        entity.setHealth(createHealth(definition));

        Texture sheet = resources.getTexture(sheetPath(definition, definitionPath));
        AnimationComponent animation = createAnimation(definition, sheet, scale, bounds);
        animation.strideSpeed = movement.moveSpeed;
        entity.setAnimation(animation);

        if (definition.gameplay != null) {
            entity.setAttack(createAttack(definition));
            entity.setBrain(createBrain(definition, animation, spawn));
        }
        if (definition.effects != null) entity.setEnemyEffects(createEffects(definition, definitionPath, resources));
        return entity;
    }

    /** The EFFECT events of every animation, as cues per authored frame with their loaded strips. */
    private static EnemyEffectsComponent createEffects(EnemyDefinition definition, String definitionPath, ResourceLoader resources) {
        EnemyEffectsComponent component = new EnemyEffectsComponent();
        ObjectMap<String, WeaponEffect> strips = new ObjectMap<>();
        for (ObjectMap.Entry<String, EnemyDefinition.Effect> entry : definition.effects) {
            strips.put(entry.key, loadEffect(entry.value, besideDefinition(entry.value.image, definitionPath), resources));
        }
        for (EnemyDefinition.Animation anim : definition.animations.values()) {
            AnimationType type = toAnimationType(anim.state);
            if (type == null || anim.events == null) continue;
            for (EnemyDefinition.Event event : anim.events) {
                WeaponEffect strip = "EFFECT".equals(event.type) ? strips.get(event.name) : null;
                if (strip == null || event.frame < 0 || event.frame >= anim.frames.size) continue;
                EnemyEffectsComponent.Cue[][] cues = component.cues.get(type);
                if (cues == null) {
                    cues = new EnemyEffectsComponent.Cue[anim.frames.size][];
                    component.cues.put(type, cues);
                }
                boolean isMirrored = !"SYMMETRIC".equals(definition.effects.get(event.name).mirror);
                EnemyEffectsComponent.Cue cue = new EnemyEffectsComponent.Cue(strip, event.x, event.y, isMirrored);
                EnemyEffectsComponent.Cue[] onFrame = cues[event.frame];
                if (onFrame == null) {
                    cues[event.frame] = new EnemyEffectsComponent.Cue[] {cue};
                } else {
                    EnemyEffectsComponent.Cue[] grown = new EnemyEffectsComponent.Cue[onFrame.length + 1];
                    System.arraycopy(onFrame, 0, grown, 0, onFrame.length);
                    grown[onFrame.length] = cue;
                    cues[event.frame] = grown;
                }
            }
        }
        return component;
    }

    /** An effect strip (one row of equal frames) as a playable effect, origin from the file. */
    private static WeaponEffect loadEffect(EnemyDefinition.Effect effect, String path, ResourceLoader resources) {
        Texture strip = resources.getTexture(path);
        Array<TextureRegion> frames = new Array<>(true, effect.frameCount, TextureRegion[]::new);
        for (int f = 0; f < effect.frameCount; f++) {
            frames.add(new TextureRegion(strip, f * effect.frameWidth, 0, effect.frameWidth, effect.frameHeight));
        }
        Animation<TextureRegion> animation = new Animation<>(effect.frameDuration / MILLIS_PER_SECOND, frames, Animation.PlayMode.NORMAL);
        float originX = effect.origin != null ? effect.origin.x : effect.frameWidth / 2f;
        float originY = effect.origin != null ? effect.origin.y : effect.frameHeight / 2f;
        return new WeaponEffect(animation, originX, originY);
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

    private static HealthComponent createHealth(EnemyDefinition definition) {
        String durability = definition.gameplay != null ? definition.gameplay.durabilityClass : null;
        HealthComponent health = new HealthComponent("HIGH".equals(durability) ? HEALTH_HIGH : "MEDIUM".equals(durability) ? HEALTH_MEDIUM
            : "LOW".equals(durability) ? HEALTH_LOW : DEFAULT_ENEMY_HEALTH);
        if (isBoss(definition)) health.knockbackResistance = BOSS_KNOCKBACK_RESISTANCE;
        return health;
    }

    private static AttackComponent createAttack(EnemyDefinition definition) {
        AttackComponent attack = new AttackComponent();
        String damage = definition.gameplay.damageClass;
        attack.damage = "HIGH".equals(damage) ? DAMAGE_HIGH : "MEDIUM".equals(damage) ? DAMAGE_MEDIUM : DAMAGE_LOW;
        return attack;
    }

    private static EnemyBrainComponent createBrain(EnemyDefinition definition, AnimationComponent animation, EnemySpawn spawn) {
        EnemyBrainComponent brain = new EnemyBrainComponent();
        boolean isBoss = isBoss(definition);
        brain.isFlying = isFlyer(definition);
        brain.aggroRange = isBoss ? BOSS_AGGRO : brain.isFlying ? FLYER_AGGRO : CRAWLER_AGGRO;
        brain.aggroHeight = brain.isFlying ? AIR_AGGRO_HEIGHT : GROUND_AGGRO_HEIGHT;
        brain.attackCooldown = isBoss ? BOSS_COOLDOWN : brain.isFlying ? FLYER_COOLDOWN : CRAWLER_COOLDOWN;
        brain.chaseSpeedScale = isBoss ? BOSS_CHASE : brain.isFlying ? FLYER_CHASE : CRAWLER_CHASE;
        brain.attacks = animation.getAnimations().containsKey(AnimationType.ATTACK_2)
            ? new AnimationType[] {AnimationType.ATTACK, AnimationType.ATTACK_2}
            : new AnimationType[] {AnimationType.ATTACK};
        brain.attackRange = DEFAULT_ATTACK_RANGE;
        if (definition.gameplay.attacks != null) {
            float shortest = Float.MAX_VALUE;
            for (EnemyDefinition.AttackInfo info : definition.gameplay.attacks.values()) {
                if (info.reach != null) shortest = Math.min(shortest, info.reach.world);
            }
            if (shortest < Float.MAX_VALUE) brain.attackRange = shortest * ATTACK_RANGE_RATIO + TARGET_HALF_WIDTH;
        }
        if (brain.isFlying) {
            brain.cruiseHeight = FLYER_CRUISE_HEIGHT;
            brain.huntHeight = FLYER_HUNT_HEIGHT;
            brain.homeFloorY = spawn.y - FLYER_CRUISE_HEIGHT;
            // After DEATH the wreck rests on the frame's bottom row (the generator's floor line)
            brain.crashFootOffset = 0f;
        }
        return brain;
    }

    private static boolean isBoss(EnemyDefinition definition) {
        return definition.gameplay != null && "MAJOR_ENCOUNTER".equals(definition.gameplay.role);
    }
}
