package com.hung.combat;

import com.hung.component.EffectComponent;
import com.hung.component.SpriteComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/** Spawns short-lived effect entities (muzzle flash, impact) that EffectSystem plays and removes. */
public final class Effects {

    private Effects() {
    }

    /**
     * Plays an effect once at (x, y). Art faces right; facing left mirrors it.
     * rotation: degrees counter-clockwise as seen facing right.
     */
    public static Entity spawn(World world, WeaponEffect effect, float x, float y, float rotation,
                               boolean isFacingRight, float scale) {
        if (effect == null) return null;
        Entity entity = new Entity();
        entity.getTransform().position.set(x, y);
        entity.setSprite(sprite(effect, rotation, isFacingRight, scale));
        EffectComponent life = new EffectComponent();
        life.timeLeft = effect.duration;
        entity.setEffect(life);
        world.addEntity(entity);
        return entity;
    }

    /** Gives an effect a short light that fades with it (muzzle flash, impact spark). */
    public static void light(Entity effect, float radius, float r, float g, float b) {
        if (effect == null || effect.getEffect() == null) return;
        EffectComponent life = effect.getEffect();
        life.lightRadius = radius;
        life.lightColor.set(r, g, b, 1f);
        life.lightDuration = life.timeLeft;
    }

    /** A sprite showing an effect strip with its origin on the entity position. */
    public static SpriteComponent sprite(WeaponEffect effect, float rotation, boolean isFacingRight, float scale) {
        SpriteComponent sprite = new SpriteComponent();
        sprite.animation = effect.animation;
        sprite.originX = effect.originX;
        sprite.originY = effect.originY;
        sprite.scale = scale;
        sprite.flipX = !isFacingRight;
        sprite.rotation = isFacingRight ? rotation : -rotation;
        return sprite;
    }
}
