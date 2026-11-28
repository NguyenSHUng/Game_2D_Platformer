package com.hung.world;

/**
 * One enemy placement read from the map's "enemies" object layer.
 * Positions are world units (feet point); the patrol is the floor stretch it walks.
 */
public class EnemySpawn {
    public String name;
    /** TMX object id, unique in its map. */
    public int objectId;
    /** Path of the enemy generator's character.json, e.g. sprites/enemies/small_melee_ghoul/character.json. */
    public String definitionPath;
    public float x;
    public float y;
    public float patrolMinX;
    public float patrolMaxX;
    public boolean isFacingRight = true;
    /** Id of the room it was placed in (the object's "room" property); null when the map has none. */
    public String roomId;
}
