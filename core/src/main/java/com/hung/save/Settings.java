package com.hung.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

/**
 * Player settings, kept in ~/.mygame/settings.json (written on every change). A missing or broken
 * file falls back to the defaults. {@link #apply()} pushes the display settings to the window.
 */
public class Settings {

    private static final String TAG = "Settings";
    private static final String FILE = ".mygame/settings.json";
    /** The window size when not fullscreen (the launcher's). */
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;

    public boolean isFullscreen;
    public boolean isVsync = true;
    public boolean isFpsVisible;

    public static Settings load() {
        FileHandle file = Gdx.files.external(FILE);
        if (!file.exists()) return new Settings();
        try {
            Settings settings = json().fromJson(Settings.class, file);
            return settings != null ? settings : new Settings();
        } catch (RuntimeException e) {
            Gdx.app.error(TAG, "Unreadable settings, using defaults: " + e.getMessage());
            return new Settings();
        }
    }

    public void save() {
        try {
            Json json = json();
            Gdx.files.external(FILE).writeString(json.prettyPrint(json.toJson(this, Settings.class)), false, "UTF-8");
        } catch (RuntimeException e) {
            Gdx.app.error(TAG, "Could not write settings: " + e.getMessage());
        }
    }

    /** Fullscreen / window and vsync; only changes the display mode when it differs. */
    public void apply() {
        if (isFullscreen && !Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        } else if (!isFullscreen && Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        }
        Gdx.graphics.setVSync(isVsync);
    }

    private static Json json() {
        Json json = new Json(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
        json.setUsePrototypes(false);
        return json;
    }
}
