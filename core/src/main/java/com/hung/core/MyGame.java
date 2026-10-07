package com.hung.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.hung.util.Constants;
import com.hung.util.ResourceLoader;
import com.hung.world.World;
import com.hung.world.WorldLoader;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class MyGame extends Game {

    private SpriteBatch batch;
    private ResourceLoader resources;

    @Override
    public void create() {
        batch = new SpriteBatch();
        resources = new ResourceLoader();

        World world = WorldLoader.load(Constants.START_MAP_PATH, resources);
        setScreen(new GameScreen(batch, world));
    }

    @Override
    public void dispose() {
        Screen screen = getScreen();
        if (screen != null) screen.dispose();
        batch.dispose();
        resources.dispose();
    }
}
