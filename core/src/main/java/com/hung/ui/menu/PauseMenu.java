package com.hung.ui.menu;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.hung.save.Settings;
import com.hung.score.RunStats;
import com.hung.ui.UiTheme;

/**
 * The in-game menu over the paused, dimmed world: Esc opens it (Resume, Settings, Main Menu, Quit —
 * leaving saves first), and it doubles as the "mission complete" screen when the player reaches the
 * exit. GameScreen owns it and stops the world while it is open.
 */
public class PauseMenu {

    /** What the menu's choices do (GameScreen). */
    public interface Actions {
        void resume();
        void toMainMenu();
        void quitGame();
    }

    private final MenuView view;
    private final Settings settings;
    private final Actions actions;
    private boolean isOpen;

    public PauseMenu(UiTheme theme, Settings settings, Actions actions) {
        this.view = new MenuView(theme);
        this.settings = settings;
        this.actions = actions;
        view.setScene(MenuView.Scene.GAME);
        view.setRootBack(actions::resume);
    }

    /** Opens the pause page: chips = mode, play time. */
    public void open(String mode, String time) {
        view.reset(new Menu("Paused")
            .chip(mode, Menu.Tone.HOT).chip(time, Menu.Tone.PLAIN)
            .item("Resume", actions::resume)
            .item("Settings", () -> view.push(SettingsMenu.build(view, settings)))
            .item("Save and exit to menu", actions::toMainMenu)
            .item("Save and quit", actions::quitGame));
        isOpen = true;
    }

    /** Opens the end-of-run page (the exit was reached; the save is already marked complete): the run's score and statistics. */
    public void openComplete(String mode, String time, RunStats stats, int chestCount) {
        Menu menu = new Menu("Mission complete").chip(mode, Menu.Tone.OK).chip(time, Menu.Tone.PLAIN);
        for (String line : summary(stats, chestCount)) menu.line(line);
        view.reset(menu
            .item("Main menu", actions::toMainMenu)
            .item("Keep exploring", actions::resume));
        isOpen = true;
    }

    /** The end-of-run summary lines. */
    static String[] summary(RunStats stats, int chestCount) {
        return new String[] {
            "Score  " + stats.getScore(),
            "Enemies defeated  " + stats.getKills(),
            "Chests opened  " + stats.getChestsOpened() + " / " + chestCount,
            "Rooms cleared  " + stats.getRoomsCleared(),
        };
    }

    public void close() {
        isOpen = false;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void update() {
        if (isOpen) view.update();
    }

    public void render(Batch batch) {
        if (isOpen) view.render(batch);
    }

    public void resize(int width, int height) {
        view.resize(width, height);
    }
}
