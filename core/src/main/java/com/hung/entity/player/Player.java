package com.hung.entity.player;

import com.hung.entity.GameCharacter;

/** The character the person plays: driven by a Controller, carries an arsenal, opens secret walls. */
public class Player extends GameCharacter {

    @Override
    public Faction getFaction() {
        return Faction.PLAYER;
    }

    @Override
    public boolean canBreakWalls() {
        return true;
    }
}
