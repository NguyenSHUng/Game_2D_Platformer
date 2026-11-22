package com.hung.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.hung.save.Settings;

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

    /** Opens the pause page: crumb = "HARD / SLOT 1", chips = room, play time, rooms explored. */
    public void open(String crumb, String room, String time, String rooms) {
        view.reset(new Menu("Paused").crumb(crumb)
            .chip(room, Menu.Tone.HOT).chip(time, Menu.Tone.PLAIN).chip(rooms, Menu.Tone.PLAIN)
            .line("Leaving saves where you stand.")
            .item("Resume", actions::resume)
            .item("Settings", () -> view.push(SettingsMenu.build(view, settings)))
            .item("Save and return to main menu", actions::toMainMenu)
            .item("Save and quit game", actions::quitGame));
        isOpen = true;
    }

    /** Opens the end-of-run page (the exit was reached; the save is already marked complete). */
    public void openComplete(String crumb, String time, String rooms) {
        view.reset(new Menu("Mission complete").crumb(crumb)
            .chip("EXIT REACHED", Menu.Tone.OK).chip(time, Menu.Tone.PLAIN).chip(rooms, Menu.Tone.PLAIN)
            .line("The run is saved as complete.")
            .item("Return to main menu", actions::toMainMenu)
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
