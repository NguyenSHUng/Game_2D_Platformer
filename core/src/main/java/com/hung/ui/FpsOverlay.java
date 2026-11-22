package com.hung.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.Disposable;

/** Frames per second in the top-right corner, when Settings.isFpsVisible. */
public class FpsOverlay implements Disposable {

    private static final float MARGIN = 8f;
    private static final float TEXT_WIDTH = 64f;
    private static final Color COLOR = new Color(0.6f, 0.85f, 0.5f, 1f);

    private final OrthographicCamera camera = new OrthographicCamera();
    private final BitmapFont font = new BitmapFont();
    // Reused every frame to avoid allocating in the game loop
    private final StringBuilder text = new StringBuilder();

    public FpsOverlay() {
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    public void render(Batch batch) {
        text.setLength(0);
        text.append(Gdx.graphics.getFramesPerSecond()).append(" FPS");
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(COLOR);
        font.draw(batch, text, camera.viewportWidth - MARGIN - TEXT_WIDTH, camera.viewportHeight - MARGIN);
        batch.end();
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
