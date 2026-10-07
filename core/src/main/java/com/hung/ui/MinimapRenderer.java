package com.hung.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.hung.world.Level;
import com.hung.world.Room;

/**
 * Exploration minimap, top-right of the screen (M opens the whole map in the middle).
 * Draws the room layout as {@link MinimapState} sees it (that state listens to room transitions):
 * visited rooms filled (current room highlighted, doorways marked), rooms one door away as dark
 * outlines, unexplored rooms hidden, and a blinking player icon at the player's live position.
 * The compact panel uses a fixed scale (COMPACT_PIXELS_PER_TILE) and scrolls with the player.
 */
public class MinimapRenderer implements Disposable {

    private static final float MARGIN = 16f;
    private static final float COMPACT_WIDTH = 260f;
    private static final float COMPACT_HEIGHT = 104f;
    private static final float PADDING = 4f;
    /** Compact panel scale: screen pixels per map tile. */
    private static final float COMPACT_PIXELS_PER_TILE = 2f;
    /** The expanded map leaves this much space around it and uses at most this share of the height. */
    private static final float EXPANDED_MARGIN = 48f;
    private static final float EXPANDED_MAX_HEIGHT = 0.7f;
    /** Gap between neighbouring rooms on the map, screen pixels (rooms touch in the world). */
    private static final float ROOM_GAP = 1f;
    private static final float DOORWAY_SIZE = 2f;
    private static final float PLAYER_SIZE = 4f;
    private static final float EXPANDED_PLAYER_SIZE = 6f;
    /** Player icon blinks this many times per second. */
    private static final float BLINK_RATE = 2.5f;
    private static final float BLINK_MIN_ALPHA = 0.35f;
    private static final float LABEL_GAP = 6f;

    private static final Color PANEL_COLOR = new Color(0.04f, 0.045f, 0.05f, 0.72f);
    private static final Color BORDER_COLOR = new Color(0.55f, 0.6f, 0.48f, 0.9f);
    private static final Color KNOWN_COLOR = new Color(0.32f, 0.34f, 0.33f, 0.9f);
    private static final Color VISITED_COLOR = new Color(0.4f, 0.43f, 0.4f, 0.95f);
    private static final Color LANDMARK_COLOR = new Color(0.33f, 0.45f, 0.53f, 0.95f);
    private static final Color BRANCH_COLOR = new Color(0.53f, 0.47f, 0.28f, 0.95f);
    private static final Color CURRENT_COLOR = new Color(0.62f, 0.72f, 0.42f, 1f);
    private static final Color DOORWAY_COLOR = new Color(0.92f, 0.86f, 0.62f, 1f);
    private static final Color PLAYER_COLOR = new Color(1f, 0.32f, 0.22f, 1f);
    private static final Color TEXT_COLOR = new Color(0.85f, 0.85f, 0.8f, 1f);
    private static final String STANDARD_KIND = "STANDARD";
    private static final String BRANCH_KIND = "BRANCH";
    private static final String START_KIND = "START";

    private final Level level;
    private final MinimapState state;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private boolean isExpanded;
    // Panel layout of the current frame, and buffers reused to avoid allocating in the game loop
    private final Rectangle panel = new Rectangle();
    private final Rectangle clip = new Rectangle();
    private final Color blink = new Color();
    private final StringBuilder label = new StringBuilder();
    private float scale;
    private float viewX;
    private float viewY;

    public MinimapRenderer(Level level, MinimapState state) {
        this.level = level;
        this.state = state;
    }

    public void toggleExpanded() {
        isExpanded = !isExpanded;
    }

    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    /** @param player the player's feet (world units), for the live icon; null hides the icon */
    public void render(Batch batch, Vector2 player, float time) {
        Room currentRoom = state.getCurrentRoom();
        if (currentRoom == null || level.getRooms().size == 0) return;
        layout(player != null ? player.x : currentRoom.bounds.x + currentRoom.bounds.width / 2f,
            player != null ? player.y : currentRoom.bounds.y + currentRoom.bounds.height / 2f);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL_COLOR);
        shapes.rect(panel.x, panel.y, panel.width, panel.height);
        drawVisitedRooms();
        if (player != null) drawPlayer(player, time);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawKnownRooms();
        shapes.setColor(BORDER_COLOR);
        shapes.rect(panel.x, panel.y, panel.width, panel.height);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        label.setLength(0);
        for (int i = 0; i < currentRoom.kind.length(); i++) {
            char c = currentRoom.kind.charAt(i);
            label.append(c == '_' ? ' ' : c);
        }
        label.append("  ·  explored ")
            .append(state.getVisitedCount()).append(" / ").append(state.getRoomCount()).append(isExpanded ? "  ·  M close" : "  ·  M map");
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(TEXT_COLOR);
        font.draw(batch, label, panel.x, panel.y - LABEL_GAP);
        batch.end();
    }

    /** Panel rectangle, world → screen scale, and the world point at the panel's bottom-left corner. */
    private void layout(float focusX, float focusY) {
        float levelWidth = level.getWidth();
        float levelHeight = level.getHeight();
        if (isExpanded) {
            float maxWidth = camera.viewportWidth - 2f * EXPANDED_MARGIN - 2f * PADDING;
            float maxHeight = camera.viewportHeight * EXPANDED_MAX_HEIGHT - 2f * PADDING;
            scale = Math.min(maxWidth / levelWidth, maxHeight / levelHeight);
            float width = levelWidth * scale + 2f * PADDING;
            float height = levelHeight * scale + 2f * PADDING;
            panel.set((camera.viewportWidth - width) / 2f, (camera.viewportHeight - height) / 2f, width, height);
            viewX = 0f;
            viewY = 0f;
            return;
        }
        panel.set(camera.viewportWidth - MARGIN - COMPACT_WIDTH, camera.viewportHeight - MARGIN - COMPACT_HEIGHT,
            COMPACT_WIDTH, COMPACT_HEIGHT);
        scale = COMPACT_PIXELS_PER_TILE / level.getGrid().getTileSize();
        viewX = scrollTo(focusX, (COMPACT_WIDTH - 2f * PADDING) / scale, levelWidth);
        viewY = scrollTo(focusY, (COMPACT_HEIGHT - 2f * PADDING) / scale, levelHeight);
    }

    /** Lower edge of a window of `view` world units centered on `focus`, kept inside [0, size]. */
    private static float scrollTo(float focus, float view, float size) {
        return view >= size ? (size - view) / 2f : MathUtils.clamp(focus - view / 2f, 0f, size - view);
    }

    private void drawVisitedRooms() {
        Array<Room> rooms = level.getRooms();
        for (int i = 0; i < rooms.size; i++) {
            Room room = rooms.get(i);
            if (!state.isVisited(i) || !toScreen(room.bounds, ROOM_GAP)) continue;
            shapes.setColor(room == state.getCurrentRoom() ? CURRENT_COLOR : roomColor(room));
            shapes.rect(clip.x, clip.y, clip.width, clip.height);
        }
        shapes.setColor(DOORWAY_COLOR);
        for (int i = 0; i < rooms.size; i++) {
            if (!state.isVisited(i)) continue;
            Array<Vector2> doorways = rooms.get(i).doorways;
            for (int d = 0; d < doorways.size; d++) {
                float x = screenX(doorways.get(d).x);
                float y = screenY(doorways.get(d).y);
                if (isInside(x, y)) shapes.rect(x - DOORWAY_SIZE / 2f, y - DOORWAY_SIZE / 2f, DOORWAY_SIZE, DOORWAY_SIZE);
            }
        }
    }

    /** Dark outlines for rooms seen through a door but not entered yet. */
    private void drawKnownRooms() {
        shapes.setColor(KNOWN_COLOR);
        Array<Room> rooms = level.getRooms();
        for (int i = 0; i < rooms.size; i++) {
            if (!state.isKnown(i) || state.isVisited(i) || !toScreen(rooms.get(i).bounds, ROOM_GAP)) continue;
            shapes.rect(clip.x, clip.y, clip.width, clip.height);
        }
    }

    private void drawPlayer(Vector2 player, float time) {
        float size = isExpanded ? EXPANDED_PLAYER_SIZE : PLAYER_SIZE;
        float x = MathUtils.clamp(screenX(player.x), panel.x + PADDING, panel.x + panel.width - PADDING);
        float y = MathUtils.clamp(screenY(player.y), panel.y + PADDING, panel.y + panel.height - PADDING);
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * BLINK_RATE * MathUtils.PI2);
        shapes.setColor(blink.set(PLAYER_COLOR).mul(1f, 1f, 1f, MathUtils.lerp(BLINK_MIN_ALPHA, 1f, pulse)));
        shapes.rect(x - size / 2f, y, size, size);
    }

    private Color roomColor(Room room) {
        if (BRANCH_KIND.equals(room.kind)) return BRANCH_COLOR;
        if (STANDARD_KIND.equals(room.kind) || START_KIND.equals(room.kind)) return VISITED_COLOR;
        return LANDMARK_COLOR;
    }

    /** World rectangle → screen, inset by `gap` pixels and clipped to the panel interior, into `clip`. */
    private boolean toScreen(Rectangle world, float gap) {
        float x0 = Math.max(screenX(world.x) + gap, panel.x + PADDING);
        float y0 = Math.max(screenY(world.y) + gap, panel.y + PADDING);
        float x1 = Math.min(screenX(world.x + world.width) - gap, panel.x + panel.width - PADDING);
        float y1 = Math.min(screenY(world.y + world.height) - gap, panel.y + panel.height - PADDING);
        if (x1 <= x0 || y1 <= y0) return false;
        clip.set(x0, y0, x1 - x0, y1 - y0);
        return true;
    }

    private float screenX(float worldX) {
        return panel.x + PADDING + (worldX - viewX) * scale;
    }

    private float screenY(float worldY) {
        return panel.y + PADDING + (worldY - viewY) * scale;
    }

    private boolean isInside(float x, float y) {
        return x >= panel.x + PADDING && x <= panel.x + panel.width - PADDING
            && y >= panel.y + PADDING && y <= panel.y + panel.height - PADDING;
    }

    @Override
    public void dispose() {
        shapes.dispose();
        font.dispose();
    }

    public boolean isExpanded() { return isExpanded; }
}
