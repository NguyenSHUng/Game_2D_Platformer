package com.hung.combat.definition;

/**
 * Firing data of a firearm, from the weapon generator's JSON ("fire"). Distances are in map
 * tiles and converted to world units with the level's tile size; times are seconds.
 */
public class FireDefinition {
    /** SEMI = one shot per trigger press; AUTO = keeps firing while the trigger is held. */
    public String mode = "SEMI";
    public float roundsPerSecond = 4f;
    public int damage = 10;
    public int magazine = 10;
    public float reloadSeconds = 1f;
    public float projectileSpeedTiles = 30f;
    public float rangeTiles = 20f;
    /** Random cone (± degrees) per shot. */
    public float spreadDegrees;
    public ShellEject shellEject;

    public boolean isAutomatic() {
        return "AUTO".equals(mode);
    }

    public static class ShellEject {
        public float speedTiles = 4f;
        /** Direction facing right, degrees counter-clockwise from +x (110 = up and back). */
        public float angleDegrees = 110f;
    }
}
