package com.hung.entity.enemy;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.ai.tactic.DirectChaseTactic;
import com.hung.ai.tactic.HuntContext;
import com.hung.ai.tactic.HuntTactic;
import com.hung.ai.tactic.TacticActions;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.PhysicsSettings;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.World;

/**
 * An enemy that hunts: notices the player in range (or when hit, or when another hunter calls it),
 * turns, chases, jumps obstacles and attacks. The frame is fixed here ({@link #think}): sight and
 * jumping stay in this class, the choice between striking, closing in and waiting is its
 * {@link HuntTactic}'s, carried out through the {@link TacticActions} below. Subclasses give the
 * numbers and change single steps (how to chase, when it may strike, what to do afterwards), and the
 * mode's {@link EnemySkill} scales them. It only hunts a player inside its home room, only calls
 * hunters of that room and never chases out of it.
 */
public abstract class HunterEnemy extends Enemy implements TacticActions {

    /** Ground hunters notice the player at most this far above / below their floor. */
    protected static final float GROUND_AGGRO_HEIGHT = 48f;
    private static final float DEFAULT_HOLD_RATIO = 0.6f;
    /** Gives up beyond this many notice ranges, however long it remembers. */
    private static final float LEASH_RATIO = 2.5f;
    /** Slower than the player's walk (96), so a chase can always be escaped. */
    private static final float MAX_CHASE_SPEED = 84f;
    /** Straight-up jumps only when the player is about overhead. */
    private static final float OVERHEAD_RANGE = 24f;
    /** After a jump it patrols at most this far to each side of the landing. */
    private static final float NEW_PATROL_RANGE = 80f;
    /** Holds this far outside body contact, world units. */
    private static final float CONTACT_GAP = 2f;
    /** Backs off at this share of its chase speed (so also under MAX_CHASE_SPEED). */
    private static final float RETREAT_SPEED_RATIO = 0.7f;

    private final float attackRange;
    private float cooldown;
    private float memory;
    private boolean isEngaged;
    private boolean isAttackFacingRight;
    private boolean isJumping;
    private float jumpVelocityX;
    // Reused by startJump() to avoid allocating in the game loop
    private final Vector2 jump = new Vector2();
    /** Decides each engaged frame; its own instance, since a tactic may keep state. */
    private HuntTactic tactic = new DirectChaseTactic();
    /** The world of the think() in progress, for approach() / retreat(); null outside it. */
    private World thinkingWorld;
    /** Seconds of the frame in progress (from tick()). */
    private float frameDelta;

    /** @param attackRange starts an attack when the player is this close horizontally (world units) */
    protected HunterEnemy(float attackRange) {
        this.attackRange = attackRange;
    }

    /** Notices the player this far away horizontally, world units. */
    protected abstract float aggroRange();

    /** Seconds between attacks. */
    protected abstract float attackCooldown();

    /** Chase speed as a multiple of the walk speed. */
    protected abstract float chaseSpeedScale();

    /** Inside this share of the attack range the enemy stops closing in. */
    protected float holdRatio() {
        return DEFAULT_HOLD_RATIO;
    }

    protected float aggroHeight() {
        return GROUND_AGGRO_HEIGHT;
    }

    /** True while it hunts the player. */
    public boolean isEngaged() {
        return isEngaged;
    }

    /** Seconds until it may attack again. */
    float cooldownLeft() {
        return cooldown;
    }

    HuntTactic tactic() {
        return tactic;
    }

    /** Replaces its DirectChaseTactic; give each enemy its own instance. */
    public void setTactic(HuntTactic tactic) {
        this.tactic = tactic;
    }

    /** Hit from afar or called by another hunter: hunts without having seen the player. */
    public void provoke() {
        memory = getSkill().memorySeconds();
    }

    @Override
    protected void tick(float delta) {
        cooldown = Math.max(0f, cooldown - delta);
        memory = Math.max(0f, memory - delta);
        frameDelta = delta;
    }

    @Override
    protected void whileHurt() {
        provoke();
        tactic.onHurt();
    }

    @Override
    protected final void think(World world, Entity target) {
        TransformComponent transform = getTransform();
        AttackComponent attack = getAttack();
        float dx = target != null ? target.getTransform().position.x - transform.position.x : 0f;
        float dy = target != null ? target.getTransform().position.y - floorY() : 0f;
        boolean wasEngaged = isEngaged;
        updateEngaged(world, target, dx, dy);
        if (wasEngaged && !isEngaged) tactic.onTargetLost();
        boolean isAttacking = attack != null && attack.isAttacking;

        if (isJumping && !continueJump(world)) {
            afterThink(target, isAttacking);
            return;
        }
        if (isEngaged && !isAttacking) {
            HuntContext context = new HuntContext(dx, attackRange, holdDistance(target), isInReach(target, dx, dy),
                cooldown <= 0f && attack != null && canStrike(), canMoveWithinHome(Math.signum(dx)),
                canRetreat(world, -Math.signum(dx)), frameDelta);
            thinkingWorld = world;
            tactic.decide(context, this);
            thinkingWorld = null;
            if (!isJumping) jumpTowards(world, dx, dy);
        }
        // PatrolSystem turns it to its patrol direction every frame; a swing keeps the side it started on
        if (isAttacking) transform.isFacingRight = isAttackFacingRight;
        afterThink(target, isAttacking);
    }

    // ------------------------------------------------------------------ actions for the tactic

    @Override
    public void face(boolean isRight) {
        getTransform().isFacingRight = isRight;
    }

    @Override
    public void strike() {
        TransformComponent transform = getTransform();
        getAttack().isRequested = true;
        isAttackFacingRight = transform.isFacingRight;
        cooldown = attackCooldown() * getSkill().cooldownScale();
        transform.velocity.x = 0f;
    }

    @Override
    public void approach(float direction) {
        chase(thinkingWorld, direction);
    }

    @Override
    public void retreat(float direction) {
        getTransform().velocity.x = canRetreat(thinkingWorld, direction) ? direction * chaseSpeed() * RETREAT_SPEED_RATIO : 0f;
    }

    @Override
    public void hold() {
        getTransform().velocity.x = 0f;
    }

    /** A step back this way stays in its home room and is safe for its body ({@link #canBackOff}). */
    private boolean canRetreat(World world, float direction) {
        return direction != 0f && canMoveWithinHome(direction) && canBackOff(world, direction);
    }

    /** Ground hunters back off only on the floor, never off a ledge or onto spikes / toxic pools. */
    protected boolean canBackOff(World world, float direction) {
        MovementComponent movement = getMovement();
        return movement != null && movement.isGrounded && isSafeAhead(world, direction);
    }

    // ------------------------------------------------------------------ sight

    /** Sight, memory of the last sight, and the call to nearby hunters on first sight. */
    private void updateEngaged(World world, Entity target, float dx, float dy) {
        EnemySkill skill = getSkill();
        if (target == null || !isInHomeRoom(target)) {
            memory = 0f;
            isEngaged = false;
            return;
        }
        float range = aggroRange() * skill.aggroScale();
        boolean isSeen = Math.abs(dx) <= range && Math.abs(dy) <= aggroHeight() * skill.aggroScale();
        if (isSeen && !isEngaged && skill.alertRange() > 0f) alertOthers(world, skill.alertRange());
        if (isSeen) memory = skill.memorySeconds();
        if (Math.abs(dx) > range * LEASH_RATIO) memory = 0f;
        isEngaged = isSeen || memory > 0f;
    }

    private void alertOthers(World world, float range) {
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            if (!(entities.get(i) instanceof HunterEnemy other) || other == this || other.getHomeRoom() != getHomeRoom()) continue;
            if (other.getTransform().position.dst(getTransform().position) <= range) other.provoke();
        }
    }

    /** The target stands in this hunter's room (always true without rooms). */
    private boolean isInHomeRoom(Entity target) {
        Room room = getHomeRoom();
        return room == null || room.bounds.contains(target.getTransform().position.x, target.getTransform().position.y + 1f);
    }

    /** A step this way keeps its body inside its room, or brings it back in. */
    private boolean canMoveWithinHome(float direction) {
        Room room = getHomeRoom();
        if (room == null || direction == 0f) return true;
        float front = getTransform().position.x + direction * (bodyWidth(this) / 2f + 1f);
        return direction > 0f ? front <= room.bounds.x + room.bounds.width : front >= room.bounds.x;
    }

    /**
     * Stops closing in at this distance: its share of the attack range, but outside body contact when
     * that still leaves the target in reach (the crawler's wide body would otherwise stand on the player).
     */
    private float holdDistance(Entity target) {
        float hold = attackRange * holdRatio();
        float apart = bodyWidth(this) / 2f + bodyWidth(target) / 2f + CONTACT_GAP;
        return apart < attackRange ? Math.max(hold, apart) : hold;
    }

    /** Horizontally in reach now, or about to be ({@link EnemySkill#aimLeadSeconds}), and at a height it can hit. */
    private boolean isInReach(Entity target, float dx, float dy) {
        float ahead = dx + target.getTransform().velocity.x * getSkill().aimLeadSeconds();
        boolean isComing = Math.signum(ahead) == Math.signum(dx) && Math.abs(ahead) <= attackRange;
        return (Math.abs(dx) <= attackRange || isComing) && canReachHeight(target, dy);
    }

    /** Ground hunters only swing when the bodies overlap vertically. */
    protected boolean canReachHeight(Entity target, float dy) {
        return dy <= bodyHeight(this) && dy >= -bodyHeight(target);
    }

    /** Height its notice range is measured from: its own feet for ground hunters. */
    protected float floorY() {
        return getTransform().position.y;
    }

    /** Ground hunters attack only with their feet on the floor. */
    protected boolean canStrike() {
        MovementComponent movement = getMovement();
        return movement == null || movement.isGrounded;
    }

    /** Ground hunters run towards the player; at ledges, hazards and walls they jump if they can, else stop. */
    protected void chase(World world, float direction) {
        MovementComponent movement = getMovement();
        boolean isStopped = movement == null || !movement.isGrounded;
        if (!isStopped && (movement.isBlocked || !isSafeAhead(world, direction))) {
            if (startJump(world, direction)) return;
            isStopped = !isSafeAhead(world, direction);
        }
        getTransform().velocity.x = isStopped ? 0f : direction * chaseSpeed();
    }

    /** Ground hunters stand still while the attack plays (the animation itself lunges / swings). */
    protected void afterThink(Entity target, boolean isAttacking) {
        if (isAttacking) getTransform().velocity.x = 0f;
    }

    protected final float chaseSpeed() {
        MovementComponent movement = getMovement();
        if (movement == null) return 0f;
        return Math.min(MAX_CHASE_SPEED, movement.moveSpeed * chaseSpeedScale() * getSkill().chaseScale());
    }

    // ------------------------------------------------------------------ jumping (ground hunters)

    /** Player on a low platform overhead: jump up to it. */
    private void jumpTowards(World world, float dx, float dy) {
        AttackComponent attack = getAttack();
        if (attack != null && attack.isRequested) return;
        if (dy > bodyHeight(this) / 2f && Math.abs(dx) <= OVERHEAD_RANGE) startJump(world, 0f);
    }

    /** direction 0 = straight up onto a platform. */
    private boolean startJump(World world, float direction) {
        MovementComponent movement = getMovement();
        PhysicsSettings physics = world.getLevel().getPhysics();
        if (!getSkill().canJump() || physics == null || movement == null || movement.isFlying || !movement.isGrounded) return false;
        TransformComponent transform = getTransform();
        TileGrid grid = world.getLevel().getGrid();
        boolean canJump = direction == 0f
            ? JumpPlanner.ontoPlatform(grid, transform.position.x, transform.position.y, getCollider(), physics.gravity, jump)
            : JumpPlanner.across(grid, transform.position.x, transform.position.y, getCollider(), direction, physics.gravity, jump);
        if (!canJump) return false;
        transform.velocity.set(jump);
        jumpVelocityX = jump.x;
        movement.isGrounded = false;
        isJumping = true;
        return true;
    }

    /** Keeps the jump's speed in the air (PatrolSystem clears it); true once it has landed. */
    private boolean continueJump(World world) {
        TransformComponent transform = getTransform();
        if (!getMovement().isGrounded) {
            transform.velocity.x = jumpVelocityX;
            if (jumpVelocityX != 0f) transform.isFacingRight = jumpVelocityX > 0f;
            return false;
        }
        isJumping = false;
        patrolHere(world);
        return true;
    }

    /** Its old stretch may be out of reach after a jump: patrols the floor it stands on now. */
    private void patrolHere(World world) {
        PatrolComponent patrol = getPatrol();
        if (patrol == null) return;
        float x = getTransform().position.x;
        patrol.minX = x - walkable(world, -1f);
        patrol.maxX = x + walkable(world, 1f);
        Room room = getHomeRoom();
        if (room == null) return;
        float half = bodyWidth(this) / 2f;
        float minX = Math.max(patrol.minX, room.bounds.x + half);
        float maxX = Math.min(patrol.maxX, room.bounds.x + room.bounds.width - half);
        if (minX <= maxX) {
            patrol.minX = minX;
            patrol.maxX = maxX;
        }
    }

    private float walkable(World world, float direction) {
        TileGrid grid = world.getLevel().getGrid();
        float x = getTransform().position.x;
        float distance = 0f;
        while (distance < NEW_PATROL_RANGE && isSafeAhead(world, x + direction * distance, direction)) distance += grid.getTileSize() / 2f;
        return distance;
    }

    /** Floor one step ahead, and no spikes or toxic pool standing on it. */
    private boolean isSafeAhead(World world, float direction) {
        return isSafeAhead(world, getTransform().position.x, direction);
    }

    private boolean isSafeAhead(World world, float x, float direction) {
        TileGrid grid = world.getLevel().getGrid();
        float y = getTransform().position.y;
        float half = getCollider().width / 2f;
        if (!grid.hasFloorAhead(x, y, half, direction)) return false;
        int column = grid.toTile(x + direction * (half + 1f));
        return !grid.get(column, grid.toTile(y + 1f)).isHazard();
    }

    protected static float bodyHeight(Entity entity) {
        ColliderComponent collider = entity.getCollider();
        return collider != null ? collider.height : 0f;
    }

    private static float bodyWidth(Entity entity) {
        ColliderComponent collider = entity.getCollider();
        return collider != null ? collider.width : 0f;
    }
}
