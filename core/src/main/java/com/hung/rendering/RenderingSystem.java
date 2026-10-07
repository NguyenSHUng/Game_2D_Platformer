package com.hung.rendering;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;
import com.hung.animation.AnimationFrames;
import com.hung.animation.HandAnchor;
import com.hung.combat.Weapon;
import com.hung.component.AnimationComponent;
import com.hung.component.HealthComponent;
import com.hung.component.SpriteComponent;
import com.hung.component.TransformComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/**
 * Draws the level's tile layers and the entities between them (2D side view):
 *   walls → decor_back → terrain → props → entities → decor_front
 * Tile layers are rendered by LibGDX's OrthogonalTiledMapRenderer (only what the camera sees).
 * Each entity is drawn as its character frame followed by its weapon (docs/character-weapon-system.md).
 */
public class RenderingSystem implements Disposable {

    /** Tile layers drawn behind the entities, back to front (missing layers are skipped). */
    private static final String[] BACK_LAYERS = {"walls", "decor_back", "terrain", "props"};
    /** Tile layers drawn in front of the entities. */
    private static final String[] FRONT_LAYERS = {"decor_front"};
    /** Sprite origins name a pixel; its centre is half a pixel in. */
    private static final float PIXEL_CENTRE = 0.5f;
    /** While invulnerable an entity blinks: hidden every other interval, in seconds. */
    private static final float BLINK_INTERVAL = 0.08f;

    private final World world;
    private final OrthogonalTiledMapRenderer mapRenderer;
    private final int[] backLayers;
    private final int[] frontLayers;

    public RenderingSystem(World world, Batch batch) {
        this.world = world;
        // Unit scale 1: one map pixel = one world unit; shares the game's batch
        this.mapRenderer = new OrthogonalTiledMapRenderer(world.getLevel().getMap(), 1f, batch);
        MapLayers layers = world.getLevel().getMap().getLayers();
        this.backLayers = layerIndices(layers, BACK_LAYERS);
        this.frontLayers = layerIndices(layers, FRONT_LAYERS);
    }

    /** Draws everything for this frame; manages batch.begin()/end() itself. */
    public void render(Batch batch, OrthographicCamera camera) {
        mapRenderer.setView(camera);
        if (backLayers.length > 0) mapRenderer.render(backLayers);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            if (entity.getAnimation() != null) {
                if (!isBlinkedOut(entity)) drawEntity(batch, entity);
            } else if (entity.getSprite() != null) {
                drawSprite(batch, entity.getSprite(), entity.getTransform().position);
            }
        }
        batch.end();

        if (frontLayers.length > 0) mapRenderer.render(frontLayers);
    }

    @Override
    public void dispose() {
        // The renderer doesn't own the shared batch, so this only frees its own resources
        mapRenderer.dispose();
    }

    private static int[] layerIndices(MapLayers layers, String[] names) {
        IntArray indices = new IntArray();
        for (String name : names) {
            int index = layers.getIndex(name);
            if (index >= 0) indices.add(index);
        }
        return indices.toArray();
    }

    /**
     * An effect / bullet / casing: the frame's origin pixel sits on the position; flipX mirrors
     * around it and rotation turns around it (degrees counter-clockwise).
     */
    private static void drawSprite(Batch batch, SpriteComponent sprite, Vector2 position) {
        TextureRegion frame = sprite.animation.getKeyFrame(sprite.stateTime);
        float width = frame.getRegionWidth() * sprite.scale;
        float height = frame.getRegionHeight() * sprite.scale;
        float originX = (sprite.originX + PIXEL_CENTRE) * sprite.scale;
        float originY = (frame.getRegionHeight() - sprite.originY - PIXEL_CENTRE) * sprite.scale;
        batch.draw(frame, position.x - originX, position.y - originY, originX, originY, width, height,
            sprite.flipX ? -1f : 1f, 1f, sprite.rotation);
    }

    private static boolean isBlinkedOut(Entity entity) {
        HealthComponent health = entity.getHealth();
        if (health == null || health.isDead || health.invulnerableTime <= 0f) return false;
        return (int) (health.invulnerableTime / BLINK_INTERVAL) % 2 == 1;
    }

    /** Draws the character, then its weapon, then the front hand over the weapon's grip. */
    private static void drawEntity(Batch batch, Entity entity) {
        TransformComponent transform = entity.getTransform();
        AnimationComponent animation = entity.getAnimation();
        Animation<TextureRegion> current = animation.animations.get(animation.currentType);
        TextureRegion frame = current.getKeyFrames()[AnimationFrames.keyFrameIndex(animation)];

        // Snap the feet to whole world units so the body and weapon stay aligned and don't shimmer
        float footX = MathUtils.round(transform.position.x);
        float footY = MathUtils.round(transform.position.y);
        float direction = transform.isFacingRight ? 1f : -1f;

        drawCharacter(batch, frame, animation, footX, footY, direction);

        WeaponComponent weapon = entity.getWeapon();
        if (weapon != null && weapon.equipped != null) {
            HandAnchor hand = AnimationFrames.handAnchor(weapon, animation);
            if (hand != null) {
                drawWeapon(batch, weapon, hand, frame, animation, footX, footY, direction);
                Animation<TextureRegion> overlay = animation.overlays.get(animation.currentType);
                if (overlay != null) {
                    drawCharacter(batch, overlay.getKeyFrames()[AnimationFrames.keyFrameIndex(animation)], animation, footX, footY, direction);
                }
            }
        }
    }

    private static void drawCharacter(Batch batch, TextureRegion frame, AnimationComponent animation,
                                      float footX, float footY, float direction) {
        float width = frame.getRegionWidth() * animation.drawScale;
        float height = frame.getRegionHeight() * animation.drawScale;
        float drawX = footX - width / 2f;
        float drawY = footY - animation.footOffset * animation.drawScale;

        // Sprites face right; a negative width mirrors them around the feet to face left
        if (direction > 0) {
            batch.draw(frame, drawX, drawY, width, height);
        } else {
            batch.draw(frame, drawX + width, drawY, -width, height);
        }
    }

    /**
     * Places the weapon's pivot on the hand anchor of the current frame.
     * Facing left mirrors the hand offset, flips the weapon (scaleX = -1) and negates its rotation.
     */
    private static void drawWeapon(Batch batch, WeaponComponent weaponComponent, HandAnchor hand,
                                   TextureRegion frame, AnimationComponent animation,
                                   float footX, float footY, float direction) {
        Weapon weapon = weaponComponent.equipped;

        // Hand anchor is in frame pixels (top-left origin); convert to an offset from the feet
        float offsetX = (hand.x - frame.getRegionWidth() / 2f) * animation.drawScale;
        float offsetY = (frame.getRegionHeight() - hand.y - animation.footOffset) * animation.drawScale;
        float handX = footX + offsetX * direction;
        float handY = footY + offsetY;
        float rotation = hand.rotation + weapon.getRotation() + weaponComponent.aimRotation;

        batch.draw(weapon.getRegion(),
            handX - weaponComponent.originX, handY - weaponComponent.originY,
            weaponComponent.originX, weaponComponent.originY,
            weaponComponent.width, weaponComponent.height,
            direction, 1f, rotation * direction);
    }
}
