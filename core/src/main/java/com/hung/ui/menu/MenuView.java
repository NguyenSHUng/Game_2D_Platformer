package com.hung.ui.menu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import com.hung.ui.UiTheme;

/**
 * Draws a stack of {@link Menu} pages (the top one is shown) as the approved terminal panel
 * (build_asset/build_ui/menu-preview.template.html) and drives it with the keyboard and mouse:
 * Up/Down or W/S move, Enter/Space choose, Esc/Backspace go back a page (or run the root's back
 * action: close the pause menu), the mouse highlights and clicks items.
 *
 * Layout is in design pixels (UiTheme: a 1280 × 720 screen fitted into the window). On the title
 * screen the panel sits on the right; in game it is centred over the dimmed, paused world.
 * Everything is drawn with the sprite batch: rectangles from a white pixel, the hazard strip from
 * its repeating tile, text from the theme's FreeType fonts.
 */
public class MenuView {

    /** Where the panel goes. */
    public enum Scene { TITLE, GAME }

    // Panel geometry (design px, as in the design's CSS)
    private static final float PANEL_WIDTH = 560f;
    private static final float PANEL_WIDTH_WIDE = 660f;
    private static final float PANEL_WIDTH_GAME = 600f;
    private static final float TITLE_RIGHT = 72f;
    private static final float TITLE_TOP = 64f;
    private static final float GAME_TOP = 92f;
    private static final float BORDER = 3f;
    private static final float INSET = 2f;
    private static final float SHADOW_OFFSET = 8f;
    private static final float HAZARD_HEIGHT = 12f;
    private static final float RIVET = 6f;
    private static final float SIDE = 30f;
    private static final float HEAD_TOP = 22f;
    private static final float TITLE_LINE = 40f;
    private static final float HEAD_BOTTOM = 10f;
    private static final float CHIP_ROW = 22f;
    private static final float TEXT_LINE = 22f;
    private static final float KEY_ROW = 28f;
    private static final float ITEMS_TOP = 14f;
    private static final float ITEMS_SIDE = 16f;
    private static final float ITEM_GAP = 4f;
    private static final float ITEM_PAD_X = 14f;
    private static final float ITEM_PAD_Y = 8f;
    private static final float ITEM_LABEL = 32f;
    private static final float ITEM_DETAIL = 21f;
    private static final float ARROW_COLUMN = 32f;
    private static final float ARROW_WIDTH = 10f;
    private static final float ARROW_HEIGHT = 16f;
    private static final float NUDGE = 4f;
    private static final long NUDGE_MILLIS = 450L;
    private static final float OUTLINE = 2f;
    private static final float ITEMS_BOTTOM = 22f;
    private static final float KEY_CAP_PAD = 6f;

    private final UiTheme theme;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final GlyphLayout glyphs = new GlyphLayout();
    private final Array<Menu> stack = new Array<>();
    /** Item rectangles of the shown page (design px, y up), laid out each frame. */
    private final Array<Rectangle> itemBoxes = new Array<>();
    private final Rectangle panel = new Rectangle();
    private final float[] vertices = new float[20];
    private Scene scene = Scene.TITLE;
    private Runnable rootBack;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    // Layout results used by drawing
    private float chipsTop;
    private float linesTop;
    private float keysTop;

    public MenuView(UiTheme theme) {
        this.theme = theme;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /** What Esc does on the first page (null = nothing). */
    public void setRootBack(Runnable rootBack) {
        this.rootBack = rootBack;
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    public void resize(int width, int height) {
        theme.setScale(width, height);
        float scale = theme.getScale();
        camera.setToOrtho(false, width / scale, height / scale);
    }

    /** The design-pixel camera (screen = viewportWidth × viewportHeight design px). */
    public OrthographicCamera getCamera() {
        return camera;
    }

    // ------------------------------------------------------------------ page stack

    public void push(Menu menu) {
        if (menu.selected < 0 || menu.selected >= menu.items.size || !menu.items.get(menu.selected).isEnabled) {
            menu.selected = menu.firstEnabled();
        }
        stack.add(menu);
    }

    public void pop() {
        if (stack.size > 1) stack.pop();
    }

    /** Swaps the shown page for a rebuilt one (after its data changed), keeping the highlight. */
    public void replace(Menu menu) {
        if (!stack.isEmpty()) {
            Menu old = stack.pop();
            if (menu.selected < 0) menu.selected = old.selected;
        }
        push(menu);
    }

    /** Empties the stack and shows this page. */
    public void reset(Menu menu) {
        stack.clear();
        push(menu);
    }

    public Menu current() {
        return stack.isEmpty() ? null : stack.peek();
    }

    // ------------------------------------------------------------------ input

    public void update() {
        Menu menu = current();
        if (menu == null) return;
        layout(menu);
        if (isPressed(Input.Keys.UP, Input.Keys.W)) move(menu, -1);
        if (isPressed(Input.Keys.DOWN, Input.Keys.S)) move(menu, 1);
        if (isPressed(Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER, Input.Keys.SPACE)) {
            activate(menu, menu.selected);
            return;
        }
        if (isPressed(Input.Keys.ESCAPE, Input.Keys.BACKSPACE)) {
            back();
            return;
        }
        updateMouse(menu);
    }

    private void updateMouse(Menu menu) {
        int mouseX = Gdx.input.getX();
        int mouseY = Gdx.input.getY();
        float x = mouseX * camera.viewportWidth / Gdx.graphics.getWidth();
        float y = camera.viewportHeight - mouseY * camera.viewportHeight / Gdx.graphics.getHeight();
        int hovered = -1;
        for (int i = 0; i < itemBoxes.size; i++) {
            if (itemBoxes.get(i).contains(x, y)) hovered = i;
        }
        // Only a moving mouse takes the highlight, so it doesn't fight the keyboard
        boolean hasMoved = mouseX != lastMouseX || mouseY != lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (hovered >= 0 && menu.items.get(hovered).isEnabled && hasMoved) menu.selected = hovered;
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && hovered >= 0) activate(menu, hovered);
    }

    private void back() {
        if (stack.size > 1) stack.pop();
        else if (rootBack != null) rootBack.run();
    }

    private static void move(Menu menu, int step) {
        int count = menu.items.size;
        if (count == 0) return;
        int index = menu.selected;
        for (int tries = 0; tries < count; tries++) {
            index = ((index + step) % count + count) % count;
            if (menu.items.get(index).isEnabled) {
                menu.selected = index;
                return;
            }
        }
    }

    private static void activate(Menu menu, int index) {
        if (index < 0 || index >= menu.items.size) return;
        Menu.Item item = menu.items.get(index);
        if (!item.isEnabled || item.action == null) return;
        menu.selected = index;
        item.action.run();
    }

    private static boolean isPressed(int... keys) {
        for (int key : keys) if (Gdx.input.isKeyJustPressed(key)) return true;
        return false;
    }

    // ------------------------------------------------------------------ layout

    /** Places the panel and every block of the page (used by both input and drawing). */
    private void layout(Menu menu) {
        float width = scene == Scene.GAME ? PANEL_WIDTH_GAME : menu.isWide ? PANEL_WIDTH_WIDE : PANEL_WIDTH;
        float top = camera.viewportHeight - (scene == Scene.GAME ? GAME_TOP : TITLE_TOP);
        float x = scene == Scene.GAME ? (camera.viewportWidth - width) / 2f : camera.viewportWidth - TITLE_RIGHT - width;

        float y = top - BORDER - HAZARD_HEIGHT - HEAD_TOP - TITLE_LINE - HEAD_BOTTOM;
        chipsTop = y;
        if (!menu.chips.isEmpty()) y -= CHIP_ROW + ITEM_GAP;
        linesTop = y;
        y -= menu.lines.size * TEXT_LINE;
        keysTop = y - ITEM_GAP;
        if (!menu.keys.isEmpty()) y -= ITEM_GAP + menu.keys.size * KEY_ROW;
        y -= ITEMS_TOP;

        // Boxes are reused frame to frame (only a longer page adds some)
        while (itemBoxes.size < menu.items.size) itemBoxes.add(new Rectangle());
        itemBoxes.truncate(menu.items.size);
        for (int i = 0; i < menu.items.size; i++) {
            float h = itemHeight(menu.items.get(i));
            itemBoxes.get(i).set(x + ITEMS_SIDE, y - h, width - 2f * ITEMS_SIDE, h);
            y -= h + ITEM_GAP;
        }
        float bottom = y - ITEMS_BOTTOM - BORDER;
        panel.set(x, bottom, width, top - bottom);
    }

    private static float itemHeight(Menu.Item item) {
        return 2f * ITEM_PAD_Y + ITEM_LABEL + (item.detail != null ? ITEM_DETAIL : 0f);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the shown page; in game, the world behind it is darkened first. */
    public void render(Batch batch) {
        Menu menu = current();
        if (menu == null) return;
        layout(menu);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (scene == Scene.GAME) rect(batch, 0f, 0f, camera.viewportWidth, camera.viewportHeight, UiTheme.SCRIM);
        drawPanel(batch, menu);
        drawHead(batch, menu);
        for (int i = 0; i < menu.items.size; i++) {
            drawItem(batch, menu.items.get(i), itemBoxes.get(i), i == menu.selected);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawPanel(Batch batch, Menu menu) {
        Rectangle p = panel;
        rect(batch, p.x + SHADOW_OFFSET, p.y - SHADOW_OFFSET, p.width, p.height, UiTheme.SHADOW);
        rect(batch, p.x, p.y, p.width, p.height, UiTheme.EDGE);
        rect(batch, p.x + BORDER, p.y + BORDER, p.width - 2f * BORDER, p.height - 2f * BORDER, UiTheme.PANEL_INSET);
        rect(batch, p.x + BORDER + INSET, p.y + BORDER + INSET, p.width - 2f * (BORDER + INSET), p.height - 2f * (BORDER + INSET), UiTheme.PANEL);
        // Hazard strip across the top (red when the page confirms something destructive)
        Texture tile = menu.isDangerous ? theme.hazardDanger : theme.hazardAmber;
        float stripWidth = p.width - 2f * BORDER;
        batch.setColor(Color.WHITE);
        batch.draw(tile, p.x + BORDER, p.y + p.height - BORDER - HAZARD_HEIGHT, stripWidth, HAZARD_HEIGHT,
            0, 0, Math.round(stripWidth), Math.round(HAZARD_HEIGHT), false, false);
        // Rivets in the corners
        float top = p.y + p.height - BORDER - HAZARD_HEIGHT - 10f - RIVET;
        float bottom = p.y + 8f;
        for (float rx : new float[] {p.x + 8f, p.x + p.width - 8f - RIVET}) {
            for (float ry : new float[] {top, bottom}) {
                rect(batch, rx, ry, RIVET, RIVET, UiTheme.EDGE_HI);
                rect(batch, rx + RIVET - 2f, ry, 2f, RIVET, UiTheme.EDGE);
                rect(batch, rx, ry, RIVET, 2f, UiTheme.EDGE);
            }
        }
    }

    private void drawHead(Batch batch, Menu menu) {
        float x = panel.x + SIDE;
        float y = panel.y + panel.height - BORDER - HAZARD_HEIGHT - HEAD_TOP;
        text(batch, UiTheme.Font.PANEL_TITLE, menu.title.toUpperCase(), x, y, UiTheme.TEXT);

        float cx = x;
        for (Menu.Chip chip : menu.chips) cx += chip(batch, chip.text, chip.tone, cx, chipsTop) + 8f;
        float ly = linesTop;
        for (String line : menu.lines) {
            text(batch, UiTheme.Font.LINE, line, x, ly - 3f, UiTheme.MUTED);
            ly -= TEXT_LINE;
        }
        float ky = keysTop;
        for (String[] row : menu.keys) {
            float kx = x;
            for (String key : row[0].split(" ")) kx += keyCap(batch, key, kx, ky) + 4f;
            text(batch, UiTheme.Font.LINE, row[1], x + 150f, ky - 4f, UiTheme.MUTED);
            ky -= KEY_ROW;
        }
    }

    private void drawItem(Batch batch, Menu.Item item, Rectangle box, boolean isSelected) {
        if (isSelected) highlight(batch, box, item.isDestructive);
        float labelTop = box.y + box.height - ITEM_PAD_Y - 4f;
        float labelX = box.x + ITEM_PAD_X + ARROW_COLUMN;
        if (isSelected) arrow(batch, box.x + ITEM_PAD_X, labelTop - ITEM_LABEL / 2f + 4f);
        Color color = !item.isEnabled ? UiTheme.DIM : isSelected ? UiTheme.TEXT_SELECTED : UiTheme.TEXT;
        text(batch, UiTheme.Font.ITEM, item.label, labelX, labelTop, color);
        if (item.detail != null) {
            text(batch, UiTheme.Font.DETAIL, item.detail, labelX, labelTop - ITEM_LABEL + 2f, item.isEnabled ? UiTheme.MUTED : UiTheme.DIM);
        }
        float right = box.x + box.width - ITEM_PAD_X;
        float middle = labelTop - ITEM_LABEL / 2f + 4f;
        if (item.toggle != null) {
            toggleSwitch(batch, item.toggle.getAsBoolean(), right, middle);
        } else if (item.value != null) {
            String value = item.value.get();
            float w = width(UiTheme.Font.VALUE, value);
            text(batch, UiTheme.Font.VALUE, value, right - w, middle + capHeight(UiTheme.Font.VALUE) / 2f,
                isSelected ? UiTheme.AMBER_HI : UiTheme.MUTED);
        } else if (item.lock != null) {
            float w = width(UiTheme.Font.SMALL_CAPS, item.lock) + 12f;
            float h = capHeight(UiTheme.Font.SMALL_CAPS) + 10f;
            outline(batch, right - w, middle - h / 2f, w, h, UiTheme.DIM);
            text(batch, UiTheme.Font.SMALL_CAPS, item.lock, right - w + 6f, middle + capHeight(UiTheme.Font.SMALL_CAPS) / 2f, UiTheme.DIM);
        }
    }

    // ------------------------------------------------------------------ pieces

    /** Selected row: an olive gradient (red for destructive) with a light edge. */
    private void highlight(Batch batch, Rectangle box, boolean isDestructive) {
        Color from = isDestructive ? UiTheme.DANGER_DEEP : UiTheme.OLIVE_HI;
        Color to = isDestructive ? UiTheme.DANGER_DARK : UiTheme.OLIVE;
        gradient(batch, box.x, box.y, box.width * 0.7f, box.height, from, to);
        rect(batch, box.x + box.width * 0.7f, box.y, box.width * 0.3f, box.height, to);
        outline(batch, box.x, box.y, box.width, box.height, isDestructive ? UiTheme.DANGER : UiTheme.EDGE_HI);
    }

    /** The amber pointer: a pixel triangle, nudging right every other beat. */
    private void arrow(Batch batch, float x, float middle) {
        float nudge = (TimeUtils.millis() / NUDGE_MILLIS) % 2 == 0 ? 0f : NUDGE;
        int rows = (int) (ARROW_HEIGHT / 2f);
        for (int r = 0; r < rows; r++) {
            float w = ARROW_WIDTH * (rows - r) / rows;
            rect(batch, x + nudge, middle + r, w, 1f, UiTheme.AMBER);
            rect(batch, x + nudge, middle - r - 1f, w, 1f, UiTheme.AMBER);
        }
    }

    /** A bordered chip with small caps text; returns its width. */
    private float chip(Batch batch, String text, Menu.Tone tone, float x, float top) {
        Color color = tone == Menu.Tone.HOT ? UiTheme.AMBER : tone == Menu.Tone.OK ? UiTheme.OK
            : tone == Menu.Tone.BAD ? UiTheme.DANGER : UiTheme.EDGE;
        Color textColor = tone == Menu.Tone.PLAIN ? UiTheme.MUTED : color;
        float w = width(UiTheme.Font.SMALL_CAPS, text) + 16f;
        float h = capHeight(UiTheme.Font.SMALL_CAPS) + 10f;
        outline(batch, x, top - h, w, h, color);
        text(batch, UiTheme.Font.SMALL_CAPS, text, x + 8f, top - 5f, textColor);
        return w;
    }

    /** ON / OFF switch, right-aligned on `right`, centred on `middle`. */
    private void toggleSwitch(Batch batch, boolean isOn, float right, float middle) {
        float cap = capHeight(UiTheme.Font.SMALL_CAPS);
        float h = cap + 10f;
        float onW = width(UiTheme.Font.SMALL_CAPS, "ON") + 18f;
        float offW = width(UiTheme.Font.SMALL_CAPS, "OFF") + 18f;
        float x = right - onW - offW - 2f * OUTLINE;
        float y = middle - h / 2f;
        rect(batch, x, y, onW + offW + 2f * OUTLINE, h, UiTheme.EDGE_HI);
        rect(batch, x + OUTLINE, y + OUTLINE, onW + offW - 2f * OUTLINE + 2f * OUTLINE, h - 2f * OUTLINE, UiTheme.PANEL_INSET);
        float onX = x + OUTLINE;
        float offX = onX + onW;
        rect(batch, isOn ? onX : offX, y + OUTLINE, isOn ? onW : offW, h - 2f * OUTLINE, UiTheme.AMBER);
        text(batch, UiTheme.Font.SMALL_CAPS, "ON", onX + 9f, middle + cap / 2f, isOn ? UiTheme.SWITCH_ON_TEXT : UiTheme.DIM);
        text(batch, UiTheme.Font.SMALL_CAPS, "OFF", offX + 9f, middle + cap / 2f, isOn ? UiTheme.DIM : UiTheme.SWITCH_ON_TEXT);
    }

    /** A key cap ("^" / "v" draw arrows); returns its width. */
    private float keyCap(Batch batch, String key, float x, float top) {
        float cap = capHeight(UiTheme.Font.SMALL_CAPS);
        boolean isArrow = key.equals("^") || key.equals("v");
        float w = (isArrow ? 8f : width(UiTheme.Font.SMALL_CAPS, key)) + 2f * KEY_CAP_PAD + 2f * OUTLINE;
        float h = cap + 8f + OUTLINE * 3f;
        float y = top - h;
        rect(batch, x, y, w, h, UiTheme.EDGE_HI);
        rect(batch, x + OUTLINE, y + 2f * OUTLINE, w - 2f * OUTLINE, h - 3f * OUTLINE, UiTheme.KEY_CAP);
        if (isArrow) {
            float cx = x + w / 2f;
            float cy = y + 2f * OUTLINE + (h - 3f * OUTLINE) / 2f;
            for (int r = 0; r < 4; r++) {
                // Rows go up from the bottom: "^" is widest at the bottom
                float half = key.equals("^") ? 3 - r : r;
                rect(batch, cx - half - 1f, cy - 2f + r, 2f * half + 2f, 1f, UiTheme.TEXT);
            }
        } else {
            text(batch, UiTheme.Font.SMALL_CAPS, key, x + OUTLINE + KEY_CAP_PAD, y + 2f * OUTLINE + (h - 3f * OUTLINE + cap) / 2f, UiTheme.TEXT);
        }
        return w;
    }

    private void outline(Batch batch, float x, float y, float w, float h, Color color) {
        rect(batch, x, y, w, OUTLINE, color);
        rect(batch, x, y + h - OUTLINE, w, OUTLINE, color);
        rect(batch, x, y, OUTLINE, h, color);
        rect(batch, x + w - OUTLINE, y, OUTLINE, h, color);
    }

    private void rect(Batch batch, float x, float y, float w, float h, Color color) {
        if (w <= 0f || h <= 0f) return;
        batch.setColor(color);
        batch.draw(theme.white, x, y, w, h);
    }

    /** Horizontal gradient from `left` to `right` (per-vertex colours). */
    private void gradient(Batch batch, float x, float y, float w, float h, Color left, Color right) {
        float l = left.toFloatBits();
        float r = right.toFloatBits();
        float[] v = vertices;
        v[0] = x; v[1] = y; v[2] = l; v[3] = 0f; v[4] = 0f;
        v[5] = x; v[6] = y + h; v[7] = l; v[8] = 0f; v[9] = 1f;
        v[10] = x + w; v[11] = y + h; v[12] = r; v[13] = 1f; v[14] = 1f;
        v[15] = x + w; v[16] = y; v[17] = r; v[18] = 1f; v[19] = 0f;
        batch.draw(theme.white, v, 0, v.length);
    }

    /** Draws text with its top at `top`; returns its width. */
    private float text(Batch batch, UiTheme.Font style, String text, float x, float top, Color color) {
        BitmapFont font = theme.font(style);
        font.setColor(color);
        glyphs.setText(font, text);
        font.draw(batch, glyphs, x, top);
        return glyphs.width;
    }

    private float width(UiTheme.Font style, String text) {
        glyphs.setText(theme.font(style), text);
        return glyphs.width;
    }

    private float capHeight(UiTheme.Font style) {
        return theme.font(style).getCapHeight();
    }
}
