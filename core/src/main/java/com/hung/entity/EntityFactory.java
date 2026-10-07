package com.hung.entity;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.hung.animation.AnimationDefinition;
import com.hung.animation.AnimationFrames;
import com.hung.animation.AnimationSet;
import com.hung.animation.AnimationType;
import com.hung.animation.FrameDefinition;
import com.hung.animation.HandAnchor;
import com.hung.component.AnimationComponent;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.DoorComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.input.Controller;
import com.hung.util.ResourceLoader;
import com.hung.world.PhysicsSettings;

/**
 * Builds the player and doors from definitions and equips them (enemies: EnemyFactory).
 * Textures must already be loaded in the ResourceLoader.
 */
public final class EntityFactory {

    /** Player box when the character JSON doesn't set one, as a fraction of the drawn frame. */
    private static final float DEFAULT_COLLIDER_WIDTH_RATIO = 0.4f;
    private static final float DEFAULT_COLLIDER_HEIGHT_RATIO = 0.8f;
    /**
     * Per-frame durations (ms) are honored with LibGDX's fixed-step Animation by repeating
     * each frame once per tick.
     */
    private static final float FRAME_TICK_SECONDS = 0.01f;
    private static final float MILLIS_PER_SECOND = 1000f;

    /** Placeholder combat numbers until characters/enemies carry their own stats (docs/04-GAME-DESIGN.md). */
    private static final int PLAYER_MAX_HEALTH = 100;
    private static final int PLAYER_KNIFE_DAMAGE = 10;
    /** Seconds the player can't be hurt again after a hit. */
    private static final float PLAYER_INVULNERABILITY = 1f;

    private EntityFactory() {
    }

    /** Queues every texture the character needs; call before ResourceLoader.finishLoading(). */
    public static void queueCharacterAssets(CharacterDefinition definition, String definitionPath, ResourceLoader resources) {
        for (AnimationDefinition animationDefinition : definition.animations) {
            resources.queueTexture(sheetPath(animationDefinition.sheet, definitionPath));
            if (animationDefinition.handSheet != null) {
                resources.queueTexture(sheetPath(animationDefinition.handSheet, definitionPath));
            }
        }
    }

    /**
     * Player character. Walk / jump / climb speeds come from the level so they match what the
     * map was built for; the box comes from the definition (sprite pixels x drawScale).
     */
    public static Entity createCharacter(CharacterDefinition definition, String definitionPath, ResourceLoader resources,
                                         float x, float y, Controller controller, PhysicsSettings physics) {
        Entity entity = new Entity();
        entity.getTransform().position.set(x, y);

        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = physics.playerMoveSpeed;
        movement.jumpSpeed = physics.playerJumpSpeed;
        movement.climbSpeed = physics.playerClimbSpeed;
        entity.setMovement(movement);

        ColliderComponent collider = new ColliderComponent();
        collider.width = definition.colliderWidth > 0f ? definition.colliderWidth * definition.drawScale
            : definition.frameWidth * definition.drawScale * DEFAULT_COLLIDER_WIDTH_RATIO;
        collider.height = definition.colliderHeight > 0f ? definition.colliderHeight * definition.drawScale
            : definition.frameHeight * definition.drawScale * DEFAULT_COLLIDER_HEIGHT_RATIO;
        collider.crouchHeight = crouchHeight(definition);
        entity.setCollider(collider);

        AttackComponent attack = new AttackComponent();
        attack.damage = PLAYER_KNIFE_DAMAGE;
        entity.setAttack(attack);
        HealthComponent health = createHealth(PLAYER_MAX_HEALTH);
        health.invulnerability = PLAYER_INVULNERABILITY;
        entity.setHealth(health);

        entity.setAnimation(createAnimation(definition, definitionPath, resources));
        entity.setController(controller);
        return entity;
    }

    /**
     * Where a character sheet lives: a bare file name sits next to the character JSON
     * (player generator exports), a path with folders is relative to the asset root.
     */
    public static String sheetPath(String sheet, String definitionPath) {
        if (sheet.indexOf('/') >= 0) return sheet;
        int slash = definitionPath.lastIndexOf('/');
        return slash >= 0 ? definitionPath.substring(0, slash + 1) + sheet : sheet;
    }

    /** A door between two rooms: a transform at the trigger's center plus the door data (no sprite, no collider). */
    public static Entity createDoor(DoorComponent door) {
        Entity entity = new Entity();
        door.trigger.getCenter(entity.getTransform().position);
        entity.setDoor(door);
        return entity;
    }

    private static HealthComponent createHealth(int maxHealth) {
        HealthComponent health = new HealthComponent();
        health.maxHealth = maxHealth;
        health.health = maxHealth;
        return health;
    }

    /** Box height while crouching, from the CROUCH pose's hurtbox; 0 when the character can't crouch. */
    private static float crouchHeight(CharacterDefinition definition) {
        for (AnimationDefinition animationDefinition : definition.animations) {
            if (animationDefinition.type == AnimationType.CROUCH && animationDefinition.hurtbox != null) {
                return animationDefinition.hurtbox.height * definition.drawScale;
            }
        }
        return 0f;
    }

    private static AnimationComponent createAnimation(CharacterDefinition definition, String definitionPath,
                                                      ResourceLoader resources) {
        AnimationComponent animation = new AnimationComponent();
        animation.drawScale = definition.drawScale;
        animation.footOffset = definition.footOffset;

        for (AnimationDefinition animationDefinition : definition.animations) {
            // Each animation goes into its stance's set (the default set when it has none)
            String stance = animationDefinition.stance != null ? animationDefinition.stance : AnimationSet.DEFAULT;
            if (!animation.stances.containsKey(stance)) animation.stances.put(stance, new AnimationSet());
            AnimationFrames.useStance(animation, stance);
            Texture sheet = resources.getTexture(sheetPath(animationDefinition.sheet, definitionPath));
            addSheetAnimation(animation, sheet, animationDefinition, definition.frameWidth, definition.frameHeight);
            if (animationDefinition.handSheet != null) {
                Texture handSheet = resources.getTexture(sheetPath(animationDefinition.handSheet, definitionPath));
                animation.overlays.put(animationDefinition.type, createOverlay(animation, animationDefinition.type,
                    handSheet, definition.frameWidth, definition.frameHeight));
            }

            HandAnchor[] handAnchors = createHandAnchors(animationDefinition);
            if (handAnchors != null) {
                animation.handAnchors.put(animationDefinition.type, handAnchors);
            }
            if (animationDefinition.walk != null) {
                animation.strideSpeed = animationDefinition.walk.groundSpeed * definition.drawScale;
            }
        }

        AnimationSet base = animation.stances.get(AnimationSet.DEFAULT);
        for (AnimationSet set : animation.stances.values()) {
            if (set != base) set.inheritMissing(base);
        }
        AnimationFrames.useStance(animation, AnimationSet.DEFAULT);
        if (!animation.animations.containsKey(AnimationType.IDLE)) {
            throw new IllegalArgumentException("Character '" + definition.name + "' needs an IDLE animation");
        }
        return animation;
    }

    /** The front-hand strip as an animation with exactly the body animation's key frames and timing. */
    private static Animation<TextureRegion> createOverlay(AnimationComponent animation, AnimationType type,
                                                         Texture sheet, int frameWidth, int frameHeight) {
        TextureRegion[] row = TextureRegion.split(sheet, frameWidth, frameHeight)[0];
        Animation<TextureRegion> body = animation.animations.get(type);
        int[] sources = animation.keyFrameSources.get(type);
        int keyFrameCount = body.getKeyFrames().length;
        Array<TextureRegion> frames = new Array<>(true, keyFrameCount, TextureRegion[]::new);
        for (int k = 0; k < keyFrameCount; k++) {
            int authored = sources != null ? sources[k] : k;
            if (authored >= row.length) {
                throw new IllegalArgumentException(type + ": hand strip has " + row.length + " frames, needs " + (authored + 1));
            }
            frames.add(row[authored]);
        }
        return new Animation<>(body.getFrameDuration(), frames, body.getPlayMode());
    }

    /**
     * One sheet row -> an animation. With per-frame durations each frame is repeated once per
     * tick, and keyFrameSources maps the repeats back to the authored frame.
     */
    private static void addSheetAnimation(AnimationComponent animation, Texture sheet, AnimationDefinition definition,
                                          int frameWidth, int frameHeight) {
        TextureRegion[] row = TextureRegion.split(sheet, frameWidth, frameHeight)[0];
        if (definition.frameCount > row.length) {
            throw new IllegalArgumentException(definition.sheet + " has " + row.length
                + " frames but " + definition.frameCount + " were requested");
        }
        AnimationType type = definition.type;
        if (definition.frames == null || definition.frames.size == 0) {
            Array<TextureRegion> frames = new Array<>(true, row, 0, definition.frameCount);
            animation.animations.put(type, new Animation<>(definition.frameDuration, frames, definition.playMode));
            return;
        }

        Array<TextureRegion> keyFrames = new Array<>(true, definition.frameCount, TextureRegion[]::new);
        IntArray sources = new IntArray();
        Rectangle[] attackBoxes = new Rectangle[definition.frameCount];
        boolean hasAttackBox = false;
        for (int i = 0; i < definition.frameCount; i++) {
            FrameDefinition frame = i < definition.frames.size ? definition.frames.get(i) : null;
            float seconds = frame != null && frame.duration > 0 ? frame.duration / MILLIS_PER_SECOND : definition.frameDuration;
            int ticks = Math.max(1, Math.round(seconds / FRAME_TICK_SECONDS));
            for (int t = 0; t < ticks; t++) {
                keyFrames.add(row[i]);
                sources.add(i);
            }
            if (frame != null && frame.attackBox != null) {
                attackBoxes[i] = frame.attackBox;
                hasAttackBox = true;
            }
        }
        animation.animations.put(type, new Animation<>(FRAME_TICK_SECONDS, keyFrames, definition.playMode));
        animation.keyFrameSources.put(type, sources.toArray());
        if (hasAttackBox) animation.attackBoxes.put(type, attackBoxes);
    }

    /** One anchor for all frames, one per frame, or null when the animation has no hand. */
    private static HandAnchor[] createHandAnchors(AnimationDefinition definition) {
        if (definition.handFrames != null) {
            if (definition.handFrames.size != definition.frameCount) {
                throw new IllegalArgumentException(definition.type + " has " + definition.frameCount
                    + " frames but " + definition.handFrames.size + " handFrames");
            }
            return definition.handFrames.toArray(HandAnchor[]::new);
        }
        if (definition.hand != null) {
            return new HandAnchor[] {definition.hand};
        }
        return null;
    }
}
