package com.hung.ui.menu;

import com.hung.save.Settings;

/**
 * The settings pages, shared by the main menu and the pause menu: display options (ON / OFF
 * switches, saved and applied at once) and the controls table. Sound has no system yet, so it
 * isn't offered.
 */
public final class SettingsMenu {

    private SettingsMenu() {
    }

    public static Menu build(MenuView view, Settings settings) {
        return new Menu("Settings")
            .item("Fullscreen", () -> {
                settings.isFullscreen = !settings.isFullscreen;
                changed(settings);
            }).toggle(() -> settings.isFullscreen)
            .item("VSync", () -> {
                settings.isVsync = !settings.isVsync;
                changed(settings);
            }).toggle(() -> settings.isVsync)
            .item("Show FPS", () -> {
                settings.isFpsVisible = !settings.isFpsVisible;
                changed(settings);
            }).toggle(() -> settings.isFpsVisible)
            .item("Controls", () -> view.push(controls(view)))
            .item("Back", view::pop);
    }

    private static Menu controls(MenuView view) {
        return new Menu("Controls").wide()
            .key("A D", "Walk")
            .key("SPACE", "Jump / S + Space: drop down")
            .key("W S", "Climb ladders / crouch")
            .key("J MOUSE", "Attack / shoot")
            .key("R", "Reload")
            .key("1 2 3 Q", "Change weapon")
            .key("E", "Open chest")
            .key("I", "Character sheet")
            .key("M", "Whole map")
            .key("ESC", "Pause menu")
            .item("Back", view::pop);
    }

    private static void changed(Settings settings) {
        settings.apply();
        settings.save();
    }
}
