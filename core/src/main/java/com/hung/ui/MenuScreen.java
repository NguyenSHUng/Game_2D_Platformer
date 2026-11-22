package com.hung.ui;

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
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.hung.core.GameMode;
import com.hung.core.Navigator;
import com.hung.save.SaveData;
import com.hung.save.SaveManager;
import com.hung.save.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The title screen, as designed in build_asset/build_ui/menu-preview.template.html: the bunker
 * backdrop, the game's name, the soldier and the Ravager idling on the floor, and the terminal panel
 * on the right with Continue (the most recent unfinished run), New Game (mode → slot → overwrite
 * check), Load Game (a finished run can be played again), Manage Saves (inspect / delete), Settings
 * and Quit. Pages are rebuilt from the save files each time the screen is shown.
 */
public class MenuScreen extends ScreenAdapter {

    private static final String BACKDROP = "ui/title_backdrop.jpg";
    private static final String PLAYER_IDLE = "sprites/players/player_soldier/idle.png";
    private static final int PLAYER_FRAME = 64;
    private static final String BOSS_JSON = "sprites/enemies/mini_boss_01/character.json";
    private static final String BOSS_SHEET = "sprites/enemies/mini_boss_01/sprite-sheet.png";
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
    private static final float BOSS_SCALE = 2f;
    private static final float BOSS_X = 270f;
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
    private final Texture bossSheet;
    private final Animation<TextureRegion> playerIdle;
    private final Animation<TextureRegion> bossIdle;
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
        bossSheet = pixelTexture(BOSS_SHEET);
        bossIdle = new Animation<>(IDLE_FRAME_SECONDS, bossFrames(), Animation.PlayMode.LOOP);
    }

    /** The Ravager's IDLE frames, read from its character.json. */
    private Array<TextureRegion> bossFrames() {
        Array<TextureRegion> frames = new Array<>();
        JsonValue idle = new JsonReader().parse(Gdx.files.internal(BOSS_JSON)).get("animations").get("idle").get("frames");
        for (JsonValue f = idle.child; f != null; f = f.next) {
            frames.add(new TextureRegion(bossSheet, f.getInt("x"), f.getInt("y"), f.getInt("width"), f.getInt("height")));
        }
        return frames;
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

    /** Backdrop, floor, the two idling characters, the game's name. */
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
        // The Ravager faces left, squaring up to the soldier (negative width mirrors it)
        TextureRegion boss = bossIdle.getKeyFrame(time);
        float bossWidth = boss.getRegionWidth() * BOSS_SCALE;
        batch.draw(boss, BOSS_X + bossWidth, FLOOR_HEIGHT - FLOOR_EDGE, -bossWidth, boss.getRegionHeight() * BOSS_SCALE);
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
        bossSheet.dispose();
    }

    // ------------------------------------------------------------------ pages

    private Menu mainPage() {
        Array<SaveManager.Slot> slots = saves.readAll();
        SaveManager.Slot latest = saves.latestContinuable();
        boolean hasAnySave = false;
        for (SaveManager.Slot slot : slots) hasAnySave |= slot.isUsed();
        Menu menu = new Menu("Main menu").crumb("TERMINAL / MAIN");
        menu.item("Continue", latest != null ? () -> navigator.continueGame(latest.index, latest.data) : null)
            .detail(latest != null ? "Slot " + (latest.index + 1) + " - " + summary(latest.data) : "No run to continue")
            .enabled(latest != null);
        menu.item("New Game", () -> view.push(modePage()));
        menu.item("Load Game", () -> view.push(loadPage())).enabled(hasAnySave);
        menu.item("Manage Saves", () -> view.push(managePage())).enabled(hasAnySave);
        menu.item("Settings", () -> view.push(SettingsMenu.build(view, settings)));
        menu.item("Quit", navigator::quit);
        return menu;
    }

    /** New game, step 1: the mode. Modes without a map yet are shown locked. */
    private Menu modePage() {
        Menu menu = new Menu("Choose a mode").crumb("NEW GAME / 1 OF 2");
        for (GameMode mode : GameMode.values()) {
            menu.item(mode.label, mode.isAvailable() ? () -> view.push(slotPage(mode)) : null)
                .detail(mode.description)
                .enabled(mode.isAvailable());
            if (!mode.isAvailable()) menu.lock("LOCKED");
        }
        return menu.select(GameMode.HARD.label).item("Back", view::pop);
    }

    /** New game, step 2: the slot it saves into (a used slot asks before it is overwritten). */
    private Menu slotPage(GameMode mode) {
        Menu menu = new Menu("Choose a slot").crumb("NEW GAME / 2 OF 2").wide()
            .line("New " + mode.label + " run. The game saves at checkpoints and when you leave.");
        for (SaveManager.Slot slot : saves.readAll()) {
            menu.slot(card(slot), () -> {
                if (slot.isUsed()) view.push(overwritePage(mode, slot));
                else navigator.startNewGame(slot.index, mode);
            });
        }
        return menu.item("Back", view::pop);
    }

    private Menu overwritePage(GameMode mode, SaveManager.Slot slot) {
        return new Menu("Overwrite slot " + (slot.index + 1) + "?").crumb("NEW GAME / CONFIRM").dangerous()
            .line(SaveManager.describe(slot))
            .line("The run saved here will be lost.")
            .item("Cancel", view::pop)
            .item("Overwrite and start", () -> navigator.startNewGame(slot.index, mode)).destructive();
    }

    private Menu loadPage() {
        Menu menu = new Menu("Load game").crumb("TERMINAL / LOAD").wide();
        for (SaveManager.Slot slot : saves.readAll()) {
            Runnable action = null;
            if (slot.isPlayable() && slot.data.isComplete) action = () -> view.push(replayPage(slot));
            else if (slot.isPlayable()) action = () -> navigator.continueGame(slot.index, slot.data);
            menu.slot(card(slot), action).enabled(action != null);
        }
        return menu.item("Back", view::pop);
    }

    /** A finished run can't be continued; it can be played again from the start in the same slot. */
    private Menu replayPage(SaveManager.Slot slot) {
        GameMode mode = GameMode.fromName(slot.data.mode);
        return new Menu("Run complete").crumb("LOAD / RUN COMPLETE")
            .line("Slot " + (slot.index + 1) + " reached the exit in " + SaveManager.formatTime(slot.data.playTime) + ".")
            .line("Play it again from the start?")
            .item("Cancel", view::pop)
            .item("Play again", () -> navigator.startNewGame(slot.index, mode));
    }

    private Menu managePage() {
        Menu menu = new Menu("Manage saves").crumb("TERMINAL / SAVES").wide()
            .line("Saves live in your home folder: .mygame/saves/");
        for (SaveManager.Slot slot : saves.readAll()) {
            menu.slot(card(slot), slot.isUsed() ? () -> view.push(slotDetailPage(slot)) : null).enabled(slot.isUsed());
        }
        return menu.item("Back", view::pop);
    }

    private Menu slotDetailPage(SaveManager.Slot slot) {
        Menu menu = new Menu("Slot " + (slot.index + 1)).crumb("SAVES / SLOT " + (slot.index + 1));
        if (slot.isPlayable()) {
            SaveData data = slot.data;
            GameMode mode = GameMode.fromName(data.mode);
            menu.chip(mode.label.toUpperCase(), Menu.Tone.HOT);
            if (data.isComplete) menu.chip("COMPLETE", Menu.Tone.OK);
            else if (data.roomId != null) menu.chip(data.roomId.toUpperCase(), Menu.Tone.PLAIN);
            menu.line("Played " + SaveManager.formatTime(data.playTime) + " - saved " + date(data.savedAt));
            menu.line((data.checkpointIndex >= 0 ? "Last checkpoint " + (data.checkpointIndex + 1) + " of " + data.checkpointCount
                : "No checkpoint yet") + " - health " + data.health + "/" + data.maxHealth);
            if (data.visitedRooms != null) menu.line(data.visitedRooms.length + " of " + data.roomCount + " rooms explored");
        } else {
            menu.chip(slot.status == SaveManager.Status.CORRUPT ? "UNREADABLE" : "INCOMPATIBLE", Menu.Tone.BAD);
            menu.line(SaveManager.describe(slot));
        }
        return menu.item("Delete", () -> view.push(deletePage(slot))).destructive().item("Back", view::pop);
    }

    private Menu deletePage(SaveManager.Slot slot) {
        return new Menu("Delete slot " + (slot.index + 1) + "?").crumb("SAVES / SLOT " + (slot.index + 1)).dangerous()
            .line(SaveManager.describe(slot))
            .line("This cannot be undone.")
            .item("Cancel", view::pop)
            .item("Delete", () -> {
                saves.delete(slot.index);
                // Rebuild from disk: Continue / Load may have changed
                view.reset(mainPage());
                if (anyUsed()) view.push(managePage());
            }).destructive();
    }

    private boolean anyUsed() {
        for (SaveManager.Slot slot : saves.readAll()) if (slot.isUsed()) return true;
        return false;
    }

    /** A save slot as a card: mode, room (or COMPLETE), time, rooms explored, date. */
    private static Menu.SlotCard card(SaveManager.Slot slot) {
        Menu.SlotCard card = new Menu.SlotCard(slot.index + 1, !slot.isUsed());
        if (!slot.isUsed()) return card;
        if (slot.data == null) {
            card.problem = slot.problem + " - delete or overwrite it";
            return card;
        }
        SaveData data = slot.data;
        GameMode mode = GameMode.fromName(data.mode);
        card.mode = mode != null ? mode.label : data.mode;
        if (slot.status != SaveManager.Status.OK) {
            card.problem = slot.problem;
            return card;
        }
        card.tag = data.isComplete ? "COMPLETE" : data.roomId != null ? data.roomId.toUpperCase() : null;
        card.tagTone = data.isComplete ? Menu.Tone.OK : Menu.Tone.HOT;
        card.time = SaveManager.formatTime(data.playTime);
        card.rooms = data.visitedRooms != null ? data.visitedRooms.length : 0;
        card.roomCount = data.roomCount;
        card.date = date(data.savedAt);
        return card;
    }

    /** "Hard - room_6 - 0:24:10" for the Continue row. */
    private static String summary(SaveData data) {
        GameMode mode = GameMode.fromName(data.mode);
        return (mode != null ? mode.label : data.mode) + " - " + (data.roomId != null ? data.roomId : "start")
            + " - " + SaveManager.formatTime(data.playTime);
    }

    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }
}
