package com.hung.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/**
 * A short notice at the bottom of the screen in the menu style ("SAVE  Checkpoint 3 of 7 - saved"),
 * fading out after a moment. GameScreen shows one on every save.
 */
public class Toast {

    private static final float SECONDS = 2f;
    private static final float FADE_SECONDS = 0.3f;
    private static final float BOTTOM = 28f;
    private static final float PAD_X = 16f;
    private static final float PAD_Y = 9f;
    private static final float GAP = 10f;
    private static final float BORDER = 2f;

    private final UiTheme theme;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final GlyphLayout tagLayout = new GlyphLayout();
    private final GlyphLayout textLayout = new GlyphLayout();
    private final Color color = new Color();
    private String tag;
    private String text;
    private float timeLeft;

    public Toast(UiTheme theme) {
        this.theme = theme;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void resize(int width, int height) {
        theme.setScale(width, height);
        camera.setToOrtho(false, width / theme.getScale(), height / theme.getScale());
    }

    public void show(String tag, String text) {
        this.tag = tag;
        this.text = text;
        timeLeft = SECONDS;
    }

    public void render(Batch batch, float delta) {
        if (timeLeft <= 0f) return;
        timeLeft -= delta;
        float alpha = Math.min(1f, timeLeft / FADE_SECONDS);
        BitmapFont tagFont = theme.font(UiTheme.Font.SMALL_CAPS);
        BitmapFont textFont = theme.font(UiTheme.Font.DETAIL);
        tagLayout.setText(tagFont, tag);
        textLayout.setText(textFont, text);
        float h = textFont.getCapHeight() + 2f * PAD_Y;
        float w = tagLayout.width + GAP + textLayout.width + 2f * PAD_X;
        float x = (camera.viewportWidth - w) / 2f;
        float y = BOTTOM;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(color.set(UiTheme.EDGE).mul(1f, 1f, 1f, alpha));
        batch.draw(theme.white, x, y, w, h);
        batch.setColor(color.set(UiTheme.PANEL).mul(1f, 1f, 1f, alpha));
        batch.draw(theme.white, x + BORDER, y + BORDER, w - 2f * BORDER, h - 2f * BORDER);
        batch.setColor(Color.WHITE);
        float middle = y + h / 2f;
        tagFont.setColor(color.set(UiTheme.AMBER).mul(1f, 1f, 1f, alpha));
        tagFont.draw(batch, tag, x + PAD_X, middle + tagFont.getCapHeight() / 2f);
        textFont.setColor(color.set(UiTheme.MUTED).mul(1f, 1f, 1f, alpha));
        textFont.draw(batch, text, x + PAD_X + tagLayout.width + GAP, middle + textFont.getCapHeight() / 2f);
        batch.end();
    }
}
