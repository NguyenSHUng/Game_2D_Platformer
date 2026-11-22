package com.hung.physics;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.hung.animation.AnimationFrames;
import com.hung.animation.AnimationType;
import com.hung.animation.HandAnchor;
import com.hung.combat.Weapon;
import com.hung.component.AnimationComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.MovementComponent;
import com.hung.component.TransformComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;

/**
 * World-space boxes of an entity, shared by collision, combat, hazards and the debug overlay.
 * Results are written into a caller-owned Rectangle so nothing is allocated per frame.
 */
public final class Hitboxes {

    /** Margin around a blade tip added to the attack box, world units. */
    private static final float TIP_MARGIN = 1f;

    // Reused by attack(); systems run on one thread
    private static final Vector2 TIP = new Vector2();

    private Hitboxes() {
    }

    /**
     * The attack box of the current ATTACK frame: the frame's authored box, stretched to the tip
     * of the held weapon's blade (so a longer knife reaches further). False when this frame doesn't hit.
     */
    public static boolean attack(Entity entity, Rectangle out) {
        AnimationComponent animation = entity.getAnimation();
        if (animation == null) return false;
        AnimationType type = animation.currentType;
        if (type != AnimationType.ATTACK && type != AnimationType.ATTACK_2) return false;
        Rectangle[] boxes = animation.getAttackBoxes().get(type);
        Rectangle spriteBox = boxes != null ? boxes[AnimationFrames.authoredFrame(animation)] : null;
        if (spriteBox == null) return false;
        TextureRegion frame = animation.getAnimations().get(type).getKeyFrame(animation.stateTime);
        fromSprite(entity, spriteBox, frame.getRegionWidth(), frame.getRegionHeight(), out);
        if (weaponTip(entity, TIP)) {
            out.merge(TIP.x - TIP_MARGIN, TIP.y - TIP_MARGIN);
            out.merge(TIP.x + TIP_MARGIN, TIP.y + TIP_MARGIN);
        }
        return true;
    }

    /** World position of the held weapon's blade tip this frame (see {@link #weaponPoint}). */
    public static boolean weaponTip(Entity entity, Vector2 out) {
        WeaponComponent holder = entity.getWeapon();
        return holder != null && holder.equipped != null && weaponPoint(entity, holder.equipped.getTipAnchor(), out);
    }

    /**
     * Angle of the held weapon in the world this frame, degrees counter-clockwise from +x:
     * the hand's pose rotation (+ weapon / aim rotation), mirrored to 180 − angle facing left.
     */
    public static float weaponAngle(Entity entity) {
        WeaponComponent holder = entity.getWeapon();
        AnimationComponent animation = entity.getAnimation();
        HandAnchor hand = holder != null && animation != null ? AnimationFrames.handAnchor(holder, animation) : null;
        float local = (hand != null ? hand.rotation : 0f) + (holder != null && holder.equipped != null ? holder.equipped.getRotation() + holder.aimRotation : 0f);
        return entity.getTransform().isFacingRight ? local : 180f - local;
    }

    /**
     * World position of a point of the held weapon (weapon pixels, top-left origin) this frame —
     * the same placement RenderingSystem draws: pivot on the hand anchor, rotated by the hand's
     * rotation, mirrored when facing left. False when there is no weapon, point or hand.
     */
    public static boolean weaponPoint(Entity entity, Vector2 point, Vector2 out) {
        WeaponComponent holder = entity.getWeapon();
        AnimationComponent animation = entity.getAnimation();
        if (holder == null || holder.equipped == null || animation == null || point == null) return false;
        Weapon weapon = holder.equipped;
        Vector2 tip = point;
        HandAnchor hand = AnimationFrames.handAnchor(holder, animation);
        if (hand == null) return false;

        TransformComponent transform = entity.getTransform();
        TextureRegion frame = animation.getAnimations().get(animation.currentType).getKeyFrame(animation.stateTime);
        float direction = transform.isFacingRight ? 1f : -1f;
        float handX = transform.position.x + (hand.x - frame.getRegionWidth() / 2f) * animation.drawScale * direction;
        float handY = transform.position.y + (frame.getRegionHeight() - hand.y - animation.footOffset) * animation.drawScale;

        TextureRegion region = weapon.getRegion();
        float scale = holder.width / region.getRegionWidth();
        float dx = tip.x * scale - holder.originX;
        float dy = (region.getRegionHeight() - tip.y) * scale - holder.originY;
        float degrees = hand.rotation + weapon.getRotation() + holder.aimRotation;
        float cos = MathUtils.cosDeg(degrees), sin = MathUtils.sinDeg(degrees);
        out.set(handX + (dx * cos - dy * sin) * direction, handY + dx * sin + dy * cos);
        return true;
    }

    /** Current body height: the crouch height while crouching, else the full height. */
    public static float bodyHeight(ColliderComponent collider, MovementComponent movement) {
        boolean isCrouched = movement != null && movement.isCrouching && collider.crouchHeight > 0f;
        return isCrouched ? collider.crouchHeight : collider.height;
    }

    /** The collider box around the feet point; the entity must have a collider. */
    public static Rectangle body(Entity entity, Rectangle out) {
        TransformComponent transform = entity.getTransform();
        ColliderComponent collider = entity.getCollider();
        float height = bodyHeight(collider, entity.getMovement());
        return out.set(transform.position.x - collider.width / 2f, transform.position.y, collider.width, height);
    }

    /**
     * Converts a box authored in sprite pixels (top-left origin, sprite facing right) to world
     * units around the entity's feet, mirrored when the entity faces left.
     */
    public static Rectangle fromSprite(Entity entity, Rectangle spriteBox, int frameWidth, int frameHeight, Rectangle out) {
        TransformComponent transform = entity.getTransform();
        AnimationComponent animation = entity.getAnimation();
        float scale = animation.drawScale;
        float width = spriteBox.width * scale;
        float offsetX = (spriteBox.x - frameWidth / 2f) * scale;
        float left = transform.isFacingRight
            ? transform.position.x + offsetX
            : transform.position.x - offsetX - width;
        float bottom = transform.position.y
            + (frameHeight - spriteBox.y - spriteBox.height - animation.footOffset) * scale;
        return out.set(left, bottom, width, spriteBox.height * scale);
    }
}
