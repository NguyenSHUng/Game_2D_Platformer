package com.hung.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.hung.combat.ItemArt;
import com.hung.save.GameSession;
import com.hung.save.SaveData;
import com.hung.save.SaveManager;
import com.hung.save.Settings;
import com.hung.ui.UiTheme;
import com.hung.ui.menu.MenuScreen;
import com.hung.util.GameLog;
import com.hung.util.ResourceLoader;
import com.hung.world.World;
import com.hung.world.loader.WorldLoader;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. Starts on the
 * main menu (MenuScreen) and moves between it and a run (GameScreen) as the {@link Navigator}:
 * a new game or a continued save loads the mode's level; leaving a run drops its screen and map so
 * the next run starts from the original level (play breaks walls in place).
 */
public class MyGame extends Game implements Navigator {

    private static final String TAG = "MyGame";

    private SpriteBatch batch;
    private ResourceLoader resources;
    private SaveManager saves;
    private Settings settings;
    /** Menu fonts and textures, shared by the title screen and the runs' pause menus. */
    private UiTheme theme;
    private MenuScreen menu;
    private GameScreen run;
    private String runMapPath;

    @Override
    public void create() {
        GameLog.install();
        batch = new SpriteBatch();
        resources = new ResourceLoader();
        saves = new SaveManager();
        settings = Settings.load();
        settings.apply();
        theme = new UiTheme();
        menu = new MenuScreen(batch, this, saves, settings, theme);
        setScreen(menu);
    }

    // Switches run after the current frame, never in the middle of a screen's render

    @Override
    public void startNewGame(GameMode mode) {
        Gdx.app.postRunnable(() -> startRun(new GameSession(mode, null)));
    }

    @Override
    public void continueGame(SaveData save) {
        GameMode mode = GameMode.fromName(save.mode);
        Gdx.app.postRunnable(() -> startRun(new GameSession(mode, save)));
    }

    @Override
    public void returnToMainMenu() {
        Gdx.app.postRunnable(() -> {
            endRun();
            setScreen(menu);
        });
    }

    @Override
    public void quit() {
        Gdx.app.exit();
    }

    private void startRun(GameSession session) {
        endRun();
        Gdx.app.log(TAG, (session.loaded != null ? "Continuing" : "New") + " " + session.mode.label + " run");
        ItemArt itemArt = ItemArt.load(resources);
        World world = WorldLoader.load(session.mode.mapPath, session.mode.enemySkill, session.mode.tacticPolicy, itemArt, resources);
        runMapPath = session.mode.mapPath;
        run = new GameScreen(batch, world, session, saves, settings, theme, itemArt, this);
        setScreen(run);
    }

    /** Drops the current run (its screen, then its map). */
    private void endRun() {
        if (run == null) return;
        if (getScreen() == run) setScreen(menu);
        run.dispose();
        run = null;
        resources.unloadMap(runMapPath);
        runMapPath = null;
    }

    @Override
    public void dispose() {
        Screen screen = getScreen();
        if (screen != null) screen.hide();
        if (run != null) run.dispose();
        menu.dispose();
        theme.dispose();
        batch.dispose();
        resources.dispose();
        Gdx.app.log(TAG, "Session ended");
    }
}
