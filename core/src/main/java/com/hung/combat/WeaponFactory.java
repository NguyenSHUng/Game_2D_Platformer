package com.hung.combat;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.hung.animation.AnimationType;
import com.hung.animation.HandAnchor;
import com.hung.combat.definition.AttachmentProfile;
import com.hung.combat.definition.EffectDefinition;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.AnimationComponent;
import com.hung.component.ArsenalComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.util.ResourceLoader;

/**
 * Builds weapons (sprite + firearm effects) and puts them in an entity's hands: one weapon, or an
 * arsenal the entity switches between. Textures must already be loaded in the ResourceLoader.
 */
public final class WeaponFactory {

    /** Effect frames with their own durations are repeated once per tick (same as character animations). */
    private static final float FRAME_TICK_SECONDS = 0.01f;
    private static final float MILLIS_PER_SECOND = 1000f;
    private static final int DEFAULT_EFFECT_FRAME_MILLIS = 100;

    private WeaponFactory() {
    }

    /**
     * Gives an entity several weapons (first in hand); WeaponSwitchSystem swaps them and their stances.
     * Each slot comes from {@link #createWeaponSlot}.
     */
    public static void equipArsenal(Entity entity, Array<WeaponComponent> slots) {
        if (slots.size == 0) return;
        ArsenalComponent arsenal = new ArsenalComponent();
        arsenal.slots.addAll(slots);
        entity.setArsenal(arsenal);
        entity.setWeapon(slots.first());
        if (entity.getAnimation() != null) entity.getAnimation().useStance(slots.first().equipped.getStance());
    }

    /** A loaded weapon: its texture region plus, for firearms, the effect animations (textures already loaded). */
    public static Weapon createWeapon(WeaponDefinition definition, ResourceLoader resources) {
        Weapon weapon = new Weapon(definition, resources.getTexture(definition.texture));
        if (definition.effects != null) {
            for (ObjectMap.Entry<String, EffectDefinition> entry : definition.effects) {
                weapon.putEffect(entry.key, createEffect(entry.value, resources.getTexture(entry.value.texture)));
            }
        }
        return weapon;
    }

    /** Queues a weapon's textures (sprite + effect strips); call before ResourceLoader.finishLoading(). */
    public static void queueWeaponAssets(WeaponDefinition definition, ResourceLoader resources) {
        resources.queueTexture(definition.texture);
        if (definition.effects == null) return;
        for (EffectDefinition effect : definition.effects.values()) resources.queueTexture(effect.texture);
    }

    private static WeaponEffect createEffect(EffectDefinition definition, Texture sheet) {
        TextureRegion[] row = TextureRegion.split(sheet, definition.frameWidth, definition.frameHeight)[0];
        int count = Math.min(definition.frameCount, row.length);
        Array<TextureRegion> keyFrames = new Array<>(true, count, TextureRegion[]::new);
        for (int i = 0; i < count; i++) {
            int millis = definition.durations != null && i < definition.durations.length ? definition.durations[i] : DEFAULT_EFFECT_FRAME_MILLIS;
            int ticks = Math.max(1, Math.round(millis / MILLIS_PER_SECOND / FRAME_TICK_SECONDS));
            for (int t = 0; t < ticks; t++) keyFrames.add(row[i]);
        }
        Animation.PlayMode mode = definition.loop ? Animation.PlayMode.LOOP : Animation.PlayMode.NORMAL;
        float originX = definition.origin != null ? definition.origin.x : 0f;
        float originY = definition.origin != null ? definition.origin.y : 0f;
        return new WeaponEffect(new Animation<>(FRAME_TICK_SECONDS, keyFrames, mode), originX, originY);
    }

    /**
     * How an animated entity holds one weapon. The profile (nullable) customizes size, pivot and
     * hand anchors for this character/weapon pair; see docs/character-weapon-system.md, section 6.
     * Firearms start with a full magazine.
     */
    public static WeaponComponent createWeaponSlot(Entity entity, Weapon weapon, AttachmentProfile profile) {
        AnimationComponent animation = entity.getAnimation();
        if (animation == null) {
            throw new IllegalArgumentException("Only entities with an AnimationComponent can hold a weapon");
        }

        WeaponComponent component = new WeaponComponent();
        component.equipped = weapon;

        float scale = profile != null && profile.weaponScale != null
            ? profile.weaponScale * animation.drawScale
            : weapon.getDrawScale();
        Vector2 pivot = profile != null && profile.pivot != null ? profile.pivot : weapon.getPivot();
        TextureRegion region = weapon.getRegion();
        component.width = region.getRegionWidth() * scale;
        component.height = region.getRegionHeight() * scale;
        // Pivot is authored with a top-left origin; LibGDX draws with a bottom-left origin
        component.originX = pivot.x * scale;
        component.originY = (region.getRegionHeight() - pivot.y) * scale;

        if (profile != null && profile.animations != null) {
            for (ObjectMap.Entry<String, AttachmentProfile.AnimationAttachment> entry : profile.animations) {
                AnimationType type = parseAnimationType(entry.key);
                component.handAnchors.put(type, resolveHandAnchors(type, entry.value, animation));
            }
        }
        if (weapon.isFirearm()) component.ammo = weapon.getFire().magazine;
        return component;
    }

    private static AnimationType parseAnimationType(String name) {
        try {
            return AnimationType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown animation '" + name + "' in attachment profile", e);
        }
    }

    /**
     * final[i] = base[i] + adjust[i], where base is the profile's auto anchors or else the character's
     * defaults. Each list has 1 entry (shared by all frames) or one per frame.
     */
    private static HandAnchor[] resolveHandAnchors(AnimationType type, AttachmentProfile.AnimationAttachment attachment,
                                                   AnimationComponent animation) {
        Animation<TextureRegion> frames = animation.getAnimations().get(type);
        if (frames == null) {
            throw new IllegalArgumentException("Attachment profile has " + type + " but the character has no such animation");
        }
        int[] sources = animation.getKeyFrameSources().get(type);
        int frameCount = sources != null ? sources[sources.length - 1] + 1 : frames.getKeyFrames().length;

        HandAnchor[] base = attachment.auto != null
            ? attachment.auto.toArray(HandAnchor[]::new)
            : animation.getHandAnchors().get(type);
        if (base == null || base.length == 0) {
            throw new IllegalArgumentException(type + ": profile has no auto anchors and the character has no default hand");
        }
        Array<AttachmentProfile.Adjust> adjust = attachment.adjust;
        int adjustCount = adjust == null ? 0 : adjust.size;

        int count = Math.max(base.length, adjustCount);
        boolean isBaseValid = base.length == 1 || base.length == count;
        boolean isAdjustValid = adjustCount <= 1 || adjustCount == count;
        if (!isBaseValid || !isAdjustValid || (count != 1 && count != frameCount)) {
            throw new IllegalArgumentException(type + ": base has " + base.length + " anchors and adjust has "
                + adjustCount + "; each must be 1 or " + frameCount + " (the animation's frame count)");
        }

        HandAnchor[] resolved = new HandAnchor[count];
        for (int i = 0; i < count; i++) {
            HandAnchor from = base[base.length == 1 ? 0 : i];
            HandAnchor anchor = new HandAnchor();
            anchor.x = from.x;
            anchor.y = from.y;
            anchor.rotation = from.rotation;
            if (adjustCount > 0) {
                AttachmentProfile.Adjust delta = adjust.get(adjustCount == 1 ? 0 : i);
                anchor.x += delta.dx;
                anchor.y += delta.dy;
                anchor.rotation += delta.rotation;
            }
            resolved[i] = anchor;
        }
        return resolved;
    }
}
