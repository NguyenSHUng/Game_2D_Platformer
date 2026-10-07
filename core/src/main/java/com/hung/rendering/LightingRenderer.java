package com.hung.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.hung.component.EffectComponent;
import com.hung.entity.Entity;
import com.hung.world.LightSource;
import com.hung.world.World;

/**
 * Lighting pass drawn over the finished scene (like the map generator's preview):
 *  1. a light map — ambient darkness with every visible light added (warm lamps, cold monitors,
 *     toxic pulses, flickering electrics, the player's own small light, muzzle flashes) —
 *     multiplied onto the screen;
 *  2. a soft additive glow of each light's colour on top.
 * The light map is rendered at a fraction of the screen size with linear filtering, so lights are
 * smooth while the sprites stay pixel-sharp. Render-only: nothing here changes gameplay.
 */
public class LightingRenderer implements Disposable {

    /** Brightness left where no light reaches (0 = black, 1 = unlit scene), slightly blue. */
    private static final Color AMBIENT = new Color(0.30f, 0.31f, 0.36f, 1f);
    /** Light map resolution as a fraction of the window. */
    private static final int LIGHT_MAP_DIVISOR = 4;
    private static final int FALLOFF_SIZE = 128;
    /** Strength of the additive colour glow (the generator uses 0.22) and its size relative to the light. */
    private static final float GLOW_ALPHA = 0.22f;
    private static final float GLOW_SCALE = 0.8f;
    /** The player carries a dim light so they never vanish in an unlit corridor. */
    private static final float PLAYER_LIGHT_RADIUS = 56f;
    private static final float PLAYER_LIGHT_INTENSITY = 0.55f;
    private static final Color PLAYER_LIGHT_COLOR = new Color(1f, 0.92f, 0.8f, 1f);
    /** Feet → chest, where the player light sits, world units. */
    private static final float PLAYER_LIGHT_HEIGHT = 16f;
    private static final float MIN_VISIBLE_INTENSITY = 0.02f;

    private final Texture falloff;
    private final Matrix4 screenProjection = new Matrix4();
    private final Color tint = new Color();
    private FrameBuffer lightMap;
    private TextureRegion lightMapRegion;
    private boolean isEnabled = true;

    public LightingRenderer() {
        this.falloff = createFalloff();
    }

    public boolean isEnabled() { return isEnabled; }

    public void setEnabled(boolean enabled) { this.isEnabled = enabled; }

    public void resize(int width, int height) {
        if (lightMap != null) lightMap.dispose();
        int w = Math.max(1, width / LIGHT_MAP_DIVISOR);
        int h = Math.max(1, height / LIGHT_MAP_DIVISOR);
        lightMap = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        lightMap.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        lightMapRegion = new TextureRegion(lightMap.getColorBufferTexture());
        lightMapRegion.flip(false, true); // frame buffers are stored upside down
        screenProjection.setToOrtho2D(0, 0, width, height);
    }

    /** Draws the lighting over what is already on screen; manages batch begin/end itself. */
    public void render(Batch batch, OrthographicCamera camera, World world, float time, int screenWidth, int screenHeight) {
        if (!isEnabled || lightMap == null) return;
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;

        // 1. Light map: ambient, then every light added on top
        lightMap.begin();
        Gdx.gl.glClearColor(AMBIENT.r, AMBIENT.g, AMBIENT.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
        batch.begin();
        drawLights(batch, camera, world, time, halfWidth, halfHeight, 1f, 1f);
        batch.end();
        lightMap.end();

        // 2. Multiply the light map onto the scene
        batch.setProjectionMatrix(screenProjection);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.setColor(Color.WHITE);
        batch.begin();
        batch.draw(lightMapRegion, 0, 0, screenWidth, screenHeight);
        batch.end();

        // 3. Coloured glow
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        drawLights(batch, camera, world, time, halfWidth, halfHeight, GLOW_SCALE, GLOW_ALPHA);
        batch.end();

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(Color.WHITE);
    }

    /** Level lights inside the view, the player's light and effect lights (muzzle flashes, impacts). */
    private void drawLights(Batch batch, OrthographicCamera camera, World world, float time,
                            float halfWidth, float halfHeight, float sizeScale, float alpha) {
        Array<LightSource> lights = world.getLevel().getLights();
        for (int i = 0; i < lights.size; i++) {
            LightSource light = lights.get(i);
            if (light.x + light.radius < camera.position.x - halfWidth || light.x - light.radius > camera.position.x + halfWidth) continue;
            if (light.y + light.radius < camera.position.y - halfHeight || light.y - light.radius > camera.position.y + halfHeight) continue;
            float k = light.intensity * light.flickerAt(time);
            if (k < MIN_VISIBLE_INTENSITY) continue;
            drawLight(batch, light.x, light.y, light.radius * sizeScale, light.color, k, alpha);
        }

        Entity player = world.getPlayer();
        if (player != null && alpha >= 1f) {
            Vector2 feet = player.getTransform().position;
            drawLight(batch, feet.x, feet.y + PLAYER_LIGHT_HEIGHT, PLAYER_LIGHT_RADIUS, PLAYER_LIGHT_COLOR, PLAYER_LIGHT_INTENSITY, alpha);
        }

        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            EffectComponent effect = entities.get(i).getEffect();
            if (effect == null || effect.lightRadius <= 0f) continue;
            Vector2 at = entities.get(i).getTransform().position;
            float k = effect.lightDuration > 0f ? Math.min(1f, effect.timeLeft / effect.lightDuration) : 1f;
            drawLight(batch, at.x, at.y, effect.lightRadius * sizeScale, effect.lightColor, k, alpha);
        }
    }

    /**
     * One light: the falloff texture centred on (x, y). In the light map (alpha 1) the colour × k is
     * added; for the glow the colour goes in at alpha × k.
     */
    private void drawLight(Batch batch, float x, float y, float radius, Color color, float k, float alpha) {
        if (alpha >= 1f) tint.set(color.r * k, color.g * k, color.b * k, 1f);
        else tint.set(color.r, color.g, color.b, alpha * k);
        batch.setColor(tint);
        batch.draw(falloff, x - radius, y - radius, radius * 2f, radius * 2f);
    }

    /** White radial falloff like the generator's: 1 at the centre, 0.6 at 45 % of the radius, 0 at the edge. */
    private static Texture createFalloff() {
        Pixmap pixmap = new Pixmap(FALLOFF_SIZE, FALLOFF_SIZE, Pixmap.Format.RGBA8888);
        float c = (FALLOFF_SIZE - 1) / 2f;
        for (int y = 0; y < FALLOFF_SIZE; y++) {
            for (int x = 0; x < FALLOFF_SIZE; x++) {
                float d = (float) Math.hypot(x - c, y - c) / c;
                float v = d >= 1f ? 0f : d < 0.45f ? 1f - (d / 0.45f) * 0.4f : 0.6f * (1f - (d - 0.45f) / 0.55f);
                pixmap.setColor(v, v, v, v);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void dispose() {
        falloff.dispose();
        if (lightMap != null) lightMap.dispose();
    }
}
