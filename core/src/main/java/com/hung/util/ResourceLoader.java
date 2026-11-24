package com.hung.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;

/**
 * Loads and caches game resources: textures and Tiled maps through the AssetManager,
 * and JSON definitions. Load everything up front, never inside update() or render().
 */
public class ResourceLoader implements Disposable {

    private final AssetManager assetManager;
    private final Json json;
    private final Json externalJson;
    private final TextureLoader.TextureParameter pixelArtTextures;
    private final TmxMapLoader.Parameters pixelArtMaps;

    public ResourceLoader() {
        this.assetManager = new AssetManager();
        this.assetManager.setLoader(TiledMap.class, new TmxMapLoader(new InternalFileHandleResolver()));
        this.json = new Json();
        // Files written by the asset tools carry extra metadata the game doesn't read yet
        this.externalJson = new Json();
        this.externalJson.setIgnoreUnknownFields(true);

        // Nearest-neighbor keeps pixel art sharp when scaled
        this.pixelArtTextures = new TextureLoader.TextureParameter();
        this.pixelArtTextures.minFilter = Texture.TextureFilter.Nearest;
        this.pixelArtTextures.magFilter = Texture.TextureFilter.Nearest;
        this.pixelArtMaps = new TmxMapLoader.Parameters();
        this.pixelArtMaps.textureMinFilter = Texture.TextureFilter.Nearest;
        this.pixelArtMaps.textureMagFilter = Texture.TextureFilter.Nearest;
        this.pixelArtMaps.generateMipMaps = false;
    }

    /** Parses one of the game's own JSON definitions (strict: unknown fields are an error); null when the file doesn't exist. */
    public <T> T loadOptionalDefinition(Class<T> type, String path) {
        FileHandle file = Gdx.files.internal(path);
        return file.exists() ? json.fromJson(type, file) : null;
    }

    /** Parses a JSON file exported by an asset tool (build_asset/), ignoring fields the class doesn't declare. */
    public <T> T loadExternalDefinition(Class<T> type, String path) {
        return externalJson.fromJson(type, Gdx.files.internal(path));
    }

    /** Queues a texture; call {@link #finishLoading()} before reading it. */
    public void queueTexture(String path) {
        assetManager.load(path, Texture.class, pixelArtTextures);
    }

    /** Queues a Tiled map (.tmx) and its tileset images; call {@link #finishLoading()} before reading it. */
    public void queueMap(String path) {
        assetManager.load(path, TiledMap.class, pixelArtMaps);
    }

    public void finishLoading() {
        assetManager.finishLoading();
    }

    public Texture getTexture(String path) {
        return assetManager.get(path, Texture.class);
    }

    public TiledMap getMap(String path) {
        return assetManager.get(path, TiledMap.class);
    }

    /**
     * Drops a loaded map (and its tilesets) so the next load reads it fresh from disk: play changes
     * the map in place (broken walls), and a new run must start from the original.
     */
    public void unloadMap(String path) {
        if (assetManager.isLoaded(path, TiledMap.class)) assetManager.unload(path);
    }

    @Override
    public void dispose() {
        assetManager.dispose();
    }
}
