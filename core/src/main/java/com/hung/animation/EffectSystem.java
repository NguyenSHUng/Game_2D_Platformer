package com.hung.animation;

import com.badlogic.gdx.utils.Array;
import com.hung.component.EffectComponent;
import com.hung.component.MovementComponent;
import com.hung.component.SpriteComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/**
 * Plays simple sprites (effects, bullets, casings) and removes effect entities when their time is
 * up. A casing that lands stops sliding and spinning.
 * Reads/Writes: SpriteComponent, EffectComponent, TransformComponent. Removes entities from the World.
 */
public class EffectSystem {

    private final World world;

    public EffectSystem(World world) {
        this.world = world;
    }

    public void update(float delta) {
        Array<Entity> entities = world.getEntities();
        // Backwards, so removing an entity doesn't skip the next one
        for (int i = entities.size - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            SpriteComponent sprite = entity.getSprite();
            EffectComponent effect = entity.getEffect();
            MovementComponent movement = entity.getMovement();
            boolean isSettled = effect != null && effect.settlesOnGround && movement != null && movement.isGrounded;
            if (isSettled) entity.getTransform().velocity.x = 0f;
            if (sprite != null && !isSettled) sprite.stateTime += delta;
            if (effect == null) continue;
            effect.timeLeft -= delta;
            if (effect.timeLeft <= 0f) world.removeEntity(entity);
        }
    }
}
