package com.hung.rendering;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.hung.core.event.EventListener;
import com.hung.util.Constants;
import com.hung.util.SmoothDamp;
import com.hung.world.Level;
import com.hung.world.event.RoomTransitionEvent;

/**
 * The game camera.
 *  - Follows the player with a critically damped spring (SmoothDamp) on each axis.
 *  - Listens to {@link RoomTransitionEvent}: from then on it stays inside the current room's
 *    camera bounds (min / max x and y), so the black rock outside the room never shows; it
 *    snaps to the new room on the next frame (the door fade hides the cut). Without rooms it
 *    stays inside the level.
 *  - Gameplay zoom (adjustFollowZoom, -/= or the mouse wheel) is eased, and capped so the view
 *    never gets bigger than the room.
 *  - The free camera (CameraInput: pan / zoom / clampTo) ignores rooms and only keeps to the level.
 */
public class CameraController implements EventListener<RoomTransitionEvent> {

    /** Free camera zoom limits. */
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 6f;
    /** Gameplay zoom: below 1 is closer to the character. Rooms may force it lower (never bigger than the room). */
    public static final float DEFAULT_FOLLOW_ZOOM = 0.8f;
    private static final float MIN_FOLLOW_ZOOM = 0.5f;
    private static final float MAX_FOLLOW_ZOOM = 1f;
    /** Seconds the follow spring takes to catch up with the player (smaller = snappier). */
    private static final float FOLLOW_SMOOTH_TIME = 0.18f;
    /** Follow speed cap, world units per second. */
    private static final float FOLLOW_MAX_SPEED = 2400f;
    /** How quickly the zoom eases toward its target, per second. */
    private static final float ZOOM_SHARPNESS = 10f;
    /** The camera looks this far above the target's feet, so more of the room above is visible. */
    private static final float FOLLOW_OFFSET_Y = 24f;
    /** Snap instead of easing when the target jumps this far (respawn on maps without rooms), world units. */
    private static final float SNAP_DISTANCE = 400f;

    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final SmoothDamp followX = new SmoothDamp();
    private final SmoothDamp followY = new SmoothDamp();
    /** Current room's camera bounds (valid when hasRoomBounds). */
    private final Rectangle roomBounds = new Rectangle();
    // Reused every frame to avoid allocating in the game loop
    private final Rectangle levelBounds = new Rectangle();
    private boolean hasRoomBounds;
    /** A room change happened: jump (no easing) on the next follow(). */
    private boolean isSnapPending;
    /** The free camera moved the view: the follow springs restart from where it is. */
    private boolean isFollowStale = true;
    private float followZoom = DEFAULT_FOLLOW_ZOOM;

    public CameraController() {
        this.camera = new OrthographicCamera();
        this.viewport = new ExtendViewport(Constants.VIEW_MIN_WIDTH, Constants.VIEW_MIN_HEIGHT, camera);
    }

    /** New room: clamp to its bounds from now on, and cut to it on the next frame. */
    @Override
    public void onEvent(RoomTransitionEvent event) {
        roomBounds.set(event.cameraBounds);
        hasRoomBounds = true;
        isSnapPending = true;
    }

    /** Eases toward the target's feet (plus a small upward offset), inside the room (or level) bounds. */
    public void follow(Vector2 target, Level level, float delta) {
        Rectangle bounds = currentBounds(level);
        float zoomTarget = Math.min(followZoom, fitZoom(bounds));
        float targetX = target.x;
        float targetY = target.y + FOLLOW_OFFSET_Y;
        boolean isFarAway = Math.abs(targetX - followX.value) > SNAP_DISTANCE || Math.abs(targetY - followY.value) > SNAP_DISTANCE;

        if (isSnapPending || (isFarAway && !isFollowStale)) {
            camera.zoom = zoomTarget;
            followX.reset(clampX(targetX, bounds));
            followY.reset(clampY(targetY, bounds));
            isSnapPending = false;
        } else {
            if (isFollowStale) {
                followX.reset(camera.position.x);
                followY.reset(camera.position.y);
            }
            camera.zoom += (zoomTarget - camera.zoom) * (1f - (float) Math.exp(-ZOOM_SHARPNESS * delta));
            followX.update(clampX(targetX, bounds), FOLLOW_SMOOTH_TIME, FOLLOW_MAX_SPEED, delta);
            followY.update(clampY(targetY, bounds), FOLLOW_SMOOTH_TIME, FOLLOW_MAX_SPEED, delta);
        }
        isFollowStale = false;
        camera.position.set(clampX(followX.value, bounds), clampY(followY.value, bounds), 0f);
        camera.update();
    }

    /** Gameplay zoom: factor below 1 zooms in (closer to the character), above 1 zooms out. */
    public void adjustFollowZoom(float factor) {
        followZoom = MathUtils.clamp(followZoom * factor, MIN_FOLLOW_ZOOM, MAX_FOLLOW_ZOOM);
    }

    public void resetFollowZoom() {
        followZoom = DEFAULT_FOLLOW_ZOOM;
    }

    public void centerOn(float x, float y) {
        camera.position.set(x, y, 0f);
        camera.update();
        isFollowStale = true;
    }

    public void pan(float dx, float dy) {
        camera.position.add(dx, dy, 0f);
        camera.update();
        isFollowStale = true;
    }

    /** Free camera zoom. Factor below 1 zooms in, above 1 zooms out. */
    public void zoomBy(float factor) {
        camera.zoom = MathUtils.clamp(camera.zoom * factor, MIN_ZOOM, MAX_ZOOM);
        camera.update();
        isFollowStale = true;
    }

    /** Keeps the view inside the level; centers on an axis where the level is smaller than the view. */
    public void clampTo(Level level) {
        levelBounds.set(0f, 0f, level.getWidth(), level.getHeight());
        camera.position.set(clampX(camera.position.x, levelBounds), clampY(camera.position.y, levelBounds), 0f);
        camera.update();
    }

    public void resize(int width, int height) {
        viewport.update(width, height);
    }

    private Rectangle currentBounds(Level level) {
        if (hasRoomBounds) return roomBounds;
        levelBounds.set(0f, 0f, level.getWidth(), level.getHeight());
        return levelBounds;
    }

    /** Largest zoom at which the whole view fits inside the bounds (no empty space at the edges). */
    private float fitZoom(Rectangle bounds) {
        if (camera.viewportWidth <= 0f || camera.viewportHeight <= 0f) return followZoom;
        return Math.min(bounds.width / camera.viewportWidth, bounds.height / camera.viewportHeight);
    }

    private float clampX(float x, Rectangle bounds) {
        return clampAxis(x, camera.viewportWidth * camera.zoom / 2f, bounds.x, bounds.width);
    }

    private float clampY(float y, Rectangle bounds) {
        return clampAxis(y, camera.viewportHeight * camera.zoom / 2f, bounds.y, bounds.height);
    }

    private static float clampAxis(float target, float halfView, float min, float size) {
        if (size <= halfView * 2f) return min + size / 2f;
        return MathUtils.clamp(target, min + halfView, min + size - halfView);
    }

    public OrthographicCamera getCamera() { return camera; }
    public float getZoom() { return camera.zoom; }
}
