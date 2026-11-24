package com.hung.ui.menu;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.hung.save.Settings;
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

    /** Opens the end-of-run page (the exit was reached; the save is already marked complete). */
    public void openComplete(String mode, String time) {
        view.reset(new Menu("Mission complete")
            .chip(mode, Menu.Tone.OK).chip(time, Menu.Tone.PLAIN)
            .item("Main menu", actions::toMainMenu)
            .item("Keep exploring", actions::resume));
        isOpen = true;
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
