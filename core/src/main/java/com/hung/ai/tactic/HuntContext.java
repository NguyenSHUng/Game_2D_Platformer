package com.hung.ai.tactic;

/**
 * What a tactic may know about one frame of the hunt, measured by the enemy.
 *
 * @param dx                  target x minus enemy x, world units
 * @param attackRange         starts an attack when the target is this close horizontally
 * @param holdDistance        closer than this the enemy stops closing in
 * @param isInReach           an attack started now would reach the target
 * @param canStrikeNow        cooldown over, has an attack and stands where it may strike
 * @param canMoveTowardTarget a step towards the target keeps it inside its home room
 * @param canBackOff          a step away from the target is safe: floor, no hazard, inside its home room
 * @param delta               seconds this frame
 */
public record HuntContext(float dx, float attackRange, float holdDistance, boolean isInReach, boolean canStrikeNow,
                          boolean canMoveTowardTarget, boolean canBackOff, float delta) {
}
