package com.hung.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;

import java.util.EnumMap;

/**
 * The menu look, shared by every menu (main menu, pause menu, save toast). Colours mirror the
 * :root tokens of the approved design (build_asset/build_ui/menu-preview.template.html); fonts and
 * the hazard strip come from build_asset/build_ui/export-game.py (assets/ui/).
 *
 * Menus lay out in design pixels (a 1280 × 720 screen); {@link #setScale} follows the window, and the
 * fonts are rendered by FreeType at that scale so text stays sharp at any window size.
 */
public class UiTheme implements Disposable {

    /** Design screen the menus are laid out on. */
    public static final float DESIGN_WIDTH = 1280f;
    public static final float DESIGN_HEIGHT = 720f;

    private static final String FONT_DIR = "ui/fonts/";
    private static final String DISPLAY_REGULAR = FONT_DIR + "Silkscreen-Regular.ttf";
    private static final String DISPLAY_BOLD = FONT_DIR + "Silkscreen-Bold.ttf";
    private static final String TEXT_FONT = FONT_DIR + "PixelifySans.ttf";

    // Tokens (same names as the design's CSS)
    public static final Color PAGE = rgb(0x090a08);
    public static final Color PANEL = new Color(17 / 255f, 19 / 255f, 15 / 255f, 0.94f);
    public static final Color PANEL_INSET = rgb(0x0a0b09);
    public static final Color EDGE = rgb(0x3b4031);
    public static final Color EDGE_HI = rgb(0x5c6249);
    public static final Color OLIVE = rgb(0x3b4230);
    public static final Color OLIVE_HI = rgb(0x555c3f);
    public static final Color AMBER = rgb(0xe38b2c);
    public static final Color AMBER_HI = rgb(0xffb15c);
    public static final Color TEXT = rgb(0xe8e3d0);
    public static final Color TEXT_SELECTED = rgb(0xfff7e2);
    public static final Color MUTED = rgb(0x9b9c88);
    public static final Color DIM = rgb(0x5a5d50);
    public static final Color DANGER = rgb(0xcf4a35);
    public static final Color DANGER_DEEP = rgb(0x6a2a20);
    public static final Color DANGER_DARK = rgb(0x3a1a15);
    public static final Color OK = rgb(0x8fb35a);
    public static final Color KEY_CAP = rgb(0x23261e);
    public static final Color BAR_BACK = rgb(0x22251d);
    public static final Color DIVIDER = rgb(0x1c1f18);
    public static final Color SWITCH_ON_TEXT = rgb(0x1a1206);
    public static final Color SHADOW = new Color(0f, 0f, 0f, 0.55f);
    public static final Color SCRIM = new Color(0f, 0f, 0f, 0.5f);

    /** Text styles: face and size in design pixels (the design's font sizes). */
    public enum Font {
        GAME_TITLE(DISPLAY_BOLD, 96),
        SUBTITLE(DISPLAY_REGULAR, 18),
        CRUMB(DISPLAY_REGULAR, 13),
        PANEL_TITLE(DISPLAY_BOLD, 34),
        LINE(TEXT_FONT, 17),
        ITEM(TEXT_FONT, 27),
        DETAIL(TEXT_FONT, 15),
        VALUE(DISPLAY_REGULAR, 16),
        SMALL_CAPS(DISPLAY_REGULAR, 12),
        FOOT(TEXT_FONT, 14),
        SLOT_NUMBER(DISPLAY_REGULAR, 30),
        SLOT_TITLE(TEXT_FONT, 22),
        VERSION(TEXT_FONT, 15);

        final String file;
        public final int size;

        Font(String file, int size) {
            this.file = file;
            this.size = size;
        }
    }

    private final EnumMap<Font, BitmapFont> fonts = new EnumMap<>(Font.class);
    public final Texture hazardAmber;
    public final Texture hazardDanger;
    /** One white pixel: solid rectangles drawn with the sprite batch (tinted). */
    public final Texture white;
    private float scale = -1f;

    public UiTheme() {
        hazardAmber = repeating("ui/hazard_amber.png");
        hazardDanger = repeating("ui/hazard_danger.png");
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(Color.WHITE);
        pixel.fill();
        white = new Texture(pixel);
        pixel.dispose();
        setScale(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /** Screen pixels per design pixel for this window (the design screen fitted inside it). */
    public static float scaleFor(int width, int height) {
        return Math.max(0.25f, Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT));
    }

    /** Follows the window; re-renders the fonts when the scale changes. */
    public void setScale(int width, int height) {
        float next = scaleFor(width, height);
        if (Math.abs(next - scale) < 0.001f) return;
        scale = next;
        disposeFonts();
        for (Font font : Font.values()) fonts.put(font, generate(font));
    }

    public float getScale() {
        return scale;
    }

    public BitmapFont font(Font font) {
        return fonts.get(font);
    }

    private BitmapFont generate(Font font) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(font.file));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = Math.max(6, Math.round(font.size * scale));
        parameter.minFilter = Texture.TextureFilter.Nearest;
        parameter.magFilter = Texture.TextureFilter.Nearest;
        parameter.hinting = FreeTypeFontGenerator.Hinting.Full;
        BitmapFont bitmap = generator.generateFont(parameter);
        generator.dispose();
        // Drawn in design pixels: one font pixel is one screen pixel
        bitmap.getData().setScale(1f / scale);
        bitmap.setUseIntegerPositions(false);
        return bitmap;
    }

    private static Texture repeating(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return texture;
    }

    private void disposeFonts() {
        for (BitmapFont font : fonts.values()) font.dispose();
        fonts.clear();
    }

    private static Color rgb(int hex) {
        return new Color(((hex >> 16) & 0xff) / 255f, ((hex >> 8) & 0xff) / 255f, (hex & 0xff) / 255f, 1f);
    }

    @Override
    public void dispose() {
        disposeFonts();
        hazardAmber.dispose();
        hazardDanger.dispose();
        white.dispose();
    }
}
