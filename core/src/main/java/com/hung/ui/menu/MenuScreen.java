package com.hung.ui.menu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.hung.core.GameMode;
import com.hung.core.Navigator;
import com.hung.save.SaveManager;
import com.hung.save.Settings;
import com.hung.ui.FpsOverlay;
import com.hung.ui.UiTheme;

/**
 * The title screen, as designed in build_asset/build_ui/menu-preview.template.html: the bunker
 * backdrop, the game's name, the soldier idling on the floor, and the terminal panel
 * on the right with Continue (the most recent unfinished run), New Game (mode → continue or
 * start over when it has a save), Settings and Quit. Pages are rebuilt from the save files each time the screen is shown.
 */
public class MenuScreen extends ScreenAdapter {

    private static final String BACKDROP = "ui/title_backdrop.jpg";
    private static final String PLAYER_IDLE = "sprites/players/player_soldier/idle.png";
    private static final int PLAYER_FRAME = 64;
    private static final float IDLE_FRAME_SECONDS = 1f / 6f;
    // Title scene, design px (the design's CSS)
    private static final float FLOOR_HEIGHT = 96f;
    private static final float FLOOR_EDGE = 4f;
    private static final Color FLOOR_TOP = new Color(0x2a2d26ff);
    private static final Color FLOOR = new Color(0x141612ff);
    private static final float TITLE_LEFT = 72f;
    private static final float TITLE_TOP = 70f;
    private static final float TITLE_LINE = 86f;
    private static final float TITLE_SHADOW = 5f;
    private static final float PLAYER_SCALE = 3f;
    private static final float PLAYER_X = 105f;
    private static final String VERSION = "build 2026.11";

    private final SpriteBatch batch;
    private final Navigator navigator;
    private final SaveManager saves;
    private final Settings settings;
    private final UiTheme theme;
    private final MenuView view;
    private final FpsOverlay fps = new FpsOverlay();
    private final Texture backdrop;
    private final Texture playerSheet;
    private final Animation<TextureRegion> playerIdle;
    private float time;

    public MenuScreen(SpriteBatch batch, Navigator navigator, SaveManager saves, Settings settings, UiTheme theme) {
        this.batch = batch;
        this.navigator = navigator;
        this.saves = saves;
        this.settings = settings;
        this.theme = theme;
        this.view = new MenuView(theme);
        view.setScene(MenuView.Scene.TITLE);
        backdrop = new Texture(Gdx.files.internal(BACKDROP));
        backdrop.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        playerSheet = pixelTexture(PLAYER_IDLE);
        Array<TextureRegion> player = new Array<>();
        for (int x = 0; x + PLAYER_FRAME <= playerSheet.getWidth(); x += PLAYER_FRAME) {
            player.add(new TextureRegion(playerSheet, x, 0, PLAYER_FRAME, PLAYER_FRAME));
        }
        playerIdle = new Animation<>(IDLE_FRAME_SECONDS, player, Animation.PlayMode.LOOP);
    }

    private static Texture pixelTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(null);
        view.reset(mainPage());
    }

    @Override
    public void render(float delta) {
        time += delta;
        view.update();
        ScreenUtils.clear(UiTheme.PAGE);
        drawScene();
        view.render(batch);
        if (settings.isFpsVisible) fps.render(batch);
    }

    /** Backdrop, floor, the idling soldier, the game's name. */
    private void drawScene() {
        OrthographicCamera camera = view.getCamera();
        float w = camera.viewportWidth;
        float h = camera.viewportHeight;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        // Backdrop covers the screen (cropped, never stretched)
        float cover = Math.max(w / backdrop.getWidth(), h / backdrop.getHeight());
        float bw = backdrop.getWidth() * cover;
        float bh = backdrop.getHeight() * cover;
        batch.draw(backdrop, (w - bw) / 2f, (h - bh) / 2f, bw, bh);
        batch.setColor(FLOOR);
        batch.draw(theme.white, 0f, 0f, w, FLOOR_HEIGHT);
        batch.setColor(FLOOR_TOP);
        batch.draw(theme.white, 0f, FLOOR_HEIGHT - FLOOR_EDGE, w, FLOOR_EDGE);
        batch.setColor(Color.WHITE);
        TextureRegion player = playerIdle.getKeyFrame(time);
        batch.draw(player, PLAYER_X, FLOOR_HEIGHT - FLOOR_EDGE, PLAYER_FRAME * PLAYER_SCALE, PLAYER_FRAME * PLAYER_SCALE);

        BitmapFont title = theme.font(UiTheme.Font.GAME_TITLE);
        float top = h - TITLE_TOP;
        for (int i = 0; i < 2; i++) {
            String line = i == 0 ? "MY" : "GAME";
            float y = top - i * TITLE_LINE;
            title.setColor(Color.BLACK);
            title.draw(batch, line, TITLE_LEFT + TITLE_SHADOW, y - TITLE_SHADOW);
            title.setColor(UiTheme.TEXT);
            title.draw(batch, line, TITLE_LEFT, y);
        }
        BitmapFont sub = theme.font(UiTheme.Font.SUBTITLE);
        float subTop = top - 2f * TITLE_LINE - 14f;
        sub.setColor(UiTheme.AMBER);
        float bunker = sub.draw(batch, "BUNKER", TITLE_LEFT, subTop).width;
        sub.setColor(UiTheme.MUTED);
        sub.draw(batch, "2D PLATFORMER", TITLE_LEFT + bunker + 14f, subTop);
        BitmapFont version = theme.font(UiTheme.Font.VERSION);
        version.setColor(UiTheme.DIM);
        version.draw(batch, VERSION, 24f, 18f + version.getCapHeight());
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        view.resize(width, height);
        fps.resize(width, height);
    }

    @Override
    public void dispose() {
        fps.dispose();
        backdrop.dispose();
        playerSheet.dispose();
    }

    // ------------------------------------------------------------------ pages

    private Menu mainPage() {
        SaveManager.Save latest = saves.latestContinuable();
        Menu menu = new Menu("Main menu");
        menu.item("Continue", latest != null ? () -> navigator.continueGame(latest.data) : null)
            .detail(latest != null ? latest.mode.label + " - " + SaveManager.formatTime(latest.data.playTime) : null)
            .enabled(latest != null);
        menu.item("New Game", () -> view.push(modePage()));
        menu.item("Settings", () -> view.push(SettingsMenu.build(view, settings)));
        menu.item("Quit", navigator::quit);
        return menu;
    }

    /** The mode to play. A mode with a save asks first (savedPage); a locked one can't be chosen. */
    private Menu modePage() {
        Menu menu = new Menu("Choose a mode");
        for (GameMode mode : GameMode.values()) {
            SaveManager.Save save = saves.read(mode);
            menu.item(mode.label, () -> {
                if (save.isUsed()) view.push(savedPage(save));
                else navigator.startNewGame(mode);
            }).detail(mode.description).enabled(mode.isAvailable());
            if (!mode.isAvailable()) menu.lock("LOCKED");
        }
        return menu.item("Back", view::pop);
    }

    /** A mode that already has a save: continue it (unless finished or unreadable) or start over. */
    private Menu savedPage(SaveManager.Save save) {
        Menu menu = new Menu(save.mode.label).dangerous();
        if (save.isPlayable()) {
            menu.chip(SaveManager.formatTime(save.data.playTime), Menu.Tone.PLAIN);
            if (save.data.isComplete) menu.chip("COMPLETE", Menu.Tone.OK);
        } else {
            menu.line(save.problem);
        }
        if (save.isContinuable()) menu.item("Continue", () -> navigator.continueGame(save.data));
        else menu.item("Back", view::pop);
        menu.item("Start over", () -> navigator.startNewGame(save.mode)).destructive();
        if (save.isContinuable()) menu.item("Back", view::pop);
        return menu;
    }
}
