package com.hung.animation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.Effects;
import com.hung.component.AnimationComponent;
import com.hung.component.EnemyEffectsComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/**
 * Spawns the effects an enemy's animation calls for (EnemyEffectsComponent) when the animation
 * reaches their frame: each cue once per play, also when a short frame was skipped in one update.
 * The spawn point (frame pixels) is placed like the sprite itself: around the feet, mirrored when
 * the enemy faces left. Runs after AnimationSystem; EffectSystem then plays and removes the effects.
 * Reads: EnemyEffectsComponent, AnimationComponent, TransformComponent. Adds effect entities.
 */
public class EnemyEffectSystem {

    private final World world;
    private final Array<Entity> spawning = new Array<>();

    public EnemyEffectSystem(World world) {
        this.world = world;
    }

    public void update() {
        // Collected first: spawning adds entities to the list being walked
        spawning.clear();
        for (Entity entity : world.getEntities()) {
            if (entity.getEnemyEffects() != null && entity.getAnimation() != null) spawning.add(entity);
        }
        for (Entity entity : spawning) update(entity);
    }

    private void update(Entity entity) {
        EnemyEffectsComponent effects = entity.getEnemyEffects();
        AnimationComponent animation = entity.getAnimation();
        AnimationType type = animation.currentType;
        int frame = AnimationFrames.authoredFrame(animation);
        // A new animation, a restart or a loop wrapping round: its cues may fire again
        boolean isNewPlay = type != effects.lastType || animation.stateTime < effects.lastStateTime || frame < effects.lastFrame;
        if (isNewPlay) effects.lastFrame = -1;
        EnemyEffectsComponent.Cue[][] cues = effects.cues.get(type);
        if (cues != null) {
            for (int f = effects.lastFrame + 1; f <= frame && f < cues.length; f++) {
                if (cues[f] == null) continue;
                for (EnemyEffectsComponent.Cue cue : cues[f]) spawn(entity, animation, cue);
            }
        }
        effects.lastType = type;
        effects.lastFrame = frame;
        effects.lastStateTime = animation.stateTime;
    }

    private void spawn(Entity enemy, AnimationComponent animation, EnemyEffectsComponent.Cue cue) {
        TransformComponent transform = enemy.getTransform();
        TextureRegion frame = animation.getAnimations().get(animation.currentType).getKeyFrames()[0];
        float scale = animation.drawScale;
        float direction = transform.isFacingRight ? 1f : -1f;
        float x = transform.position.x + (cue.x - frame.getRegionWidth() / 2f) * scale * direction;
        float y = transform.position.y + (frame.getRegionHeight() - cue.y - animation.footOffset) * scale;
        Effects.spawn(world, cue.effect, x, y, 0f, transform.isFacingRight || !cue.isMirrored, scale);
    }
}
