package com.hung.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.hung.world.RoomTransition;

/**
 * Full-screen black overlay for room transitions; its opacity is RoomTransition.fade,
 * which DoorSystem animates. Drawn over the world, under the HUD.
 */
public class FadeRenderer implements Disposable {

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ShapeRenderer shapes = new ShapeRenderer();

    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    public void render(RoomTransition transition) {
        if (transition.fade <= 0f) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.BLACK.r, Color.BLACK.g, Color.BLACK.b, transition.fade);
        shapes.rect(0f, 0f, camera.viewportWidth, camera.viewportHeight);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
