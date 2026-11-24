package com.hung.entity.enemy;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.PhysicsSettings;
import com.hung.world.TileGrid;
import com.hung.world.World;

/**
 * An enemy that hunts: notices the player in range (or when hit, or when another hunter calls it),
 * turns, chases, jumps obstacles and attacks. The hunt itself is fixed here ({@link #think});
 * subclasses give the numbers and change single steps (how to chase, when it may strike, what to do
 * afterwards), and the mode's {@link EnemySkill} scales them.
 */
public abstract class HunterEnemy extends Enemy {

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

    private final float attackRange;
    private float cooldown;
    private float memory;
    private boolean isEngaged;
    private boolean isAttackFacingRight;
    private boolean isJumping;
    private float jumpVelocityX;
    // Reused by startJump() to avoid allocating in the game loop
    private final Vector2 jump = new Vector2();

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

    /** Hit from afar or called by another hunter: hunts without having seen the player. */
    public void provoke() {
        memory = getSkill().memorySeconds();
    }

    @Override
    protected void tick(float delta) {
        cooldown = Math.max(0f, cooldown - delta);
        memory = Math.max(0f, memory - delta);
    }

    @Override
    protected void whileHurt() {
        provoke();
    }

    @Override
    protected final void think(World world, Entity target) {
        TransformComponent transform = getTransform();
        AttackComponent attack = getAttack();
        float dx = target != null ? target.getTransform().position.x - transform.position.x : 0f;
        float dy = target != null ? target.getTransform().position.y - floorY() : 0f;
        updateEngaged(world, target, dx, dy);
        boolean isAttacking = attack != null && attack.isAttacking;

        if (isJumping && !continueJump(world)) {
            afterThink(target, isAttacking);
            return;
        }
        if (isEngaged && !isAttacking) {
            transform.isFacingRight = dx > 0f;
            if (isInReach(target, dx, dy) && cooldown <= 0f && attack != null && canStrike()) {
                attack.isRequested = true;
                isAttackFacingRight = transform.isFacingRight;
                cooldown = attackCooldown() * getSkill().cooldownScale();
                transform.velocity.x = 0f;
            } else if (Math.abs(dx) > attackRange * holdRatio()) {
                chase(world, Math.signum(dx));
            } else {
                transform.velocity.x = 0f;
            }
            if (!isJumping) jumpTowards(world, dx, dy);
        }
        // PatrolSystem turns it to its patrol direction every frame; a swing keeps the side it started on
        if (isAttacking) transform.isFacingRight = isAttackFacingRight;
        afterThink(target, isAttacking);
    }

    /** Sight, memory of the last sight, and the call to nearby hunters on first sight. */
    private void updateEngaged(World world, Entity target, float dx, float dy) {
        EnemySkill skill = getSkill();
        if (target == null) {
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
            if (!(entities.get(i) instanceof HunterEnemy other) || other == this) continue;
            if (other.getTransform().position.dst(getTransform().position) <= range) other.provoke();
        }
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
}
