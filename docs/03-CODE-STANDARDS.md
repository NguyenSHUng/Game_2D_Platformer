# 03 - Code Standards

[← Index](README.md)

## Package Structure (Mandatory)

All code goes under:
```
com.hung.{system}
```

**System packages** (the packages that exist; create a new one only for a new domain):

```
com.hung.core          # MyGame, GameScreen (frame loop), GameMode, Navigator, EventBus
com.hung.world         # World, Level, tile grid, loaders, rooms, doors, world events
com.hung.entity        # Entity (component container) and the factories that build entities
com.hung.component     # Data components (Transform, Movement, Health, Animation, Weapon, ...)
com.hung.ai            # Enemy AI systems (PatrolSystem, EnemyBrainSystem)
com.hung.combat        # Weapons, damage, attacks, projectiles, health
com.hung.physics       # Physics, tile collision, hazards, hitboxes
com.hung.input         # Controller, KeyboardController, InputSystem, free camera input
com.hung.animation     # Animation data and systems, effect systems
com.hung.rendering     # Map / entity drawing, camera, lights, HUD, debug overlay
com.hung.ui            # Title screen, menus, pause menu, toast, minimap
com.hung.save          # Save data, save snapshot, save slots, settings, game session
com.hung.util          # Constants, resource loader, helpers
```

**Do NOT create**:
- `models/`, `services/`, `helpers/` - use existing packages
- Deep sub-packages - max 1 level deep

## Naming Conventions

### Classes
```java
// Concrete classes - PascalCase; components end in Component, systems in System
public class HealthComponent { }
public class CollisionSystem { }
public class EnemyFactory { }

// Interfaces - a plain noun for the role, no I- prefix
public interface Controller { }
public interface EventListener<T> { }
```

### Methods & Variables
```java
// Methods - camelCase, start with verb
public void update(float delta) { }
public Vector2 getPosition() { }
public boolean hasCollided() { }
public void setHealth(int value) { }

// Variables - camelCase
private Vector2 position;
private float health;
private boolean isAlive;

// Constants - SCREAMING_SNAKE_CASE
public static final float PLAYER_SPEED = 200f;
public static final int TILE_SIZE = 32;
```

### Booleans
```java
// Always prefix with is/has/can/should
private boolean isAlive;
private boolean hasWeapon;
private boolean canShoot;
private boolean shouldDespawn;

// NOT: alive, weapon, shoot, despawn
```

## Class Structure Template

Order members as constants, fields, constructor, public methods, private helpers. A system, for example:

```java
package com.hung.ai;

/**
 * Simple enemy AI: walk back and forth inside the patrol range, turning at its ends,
 * at walls and before stepping off a ledge.
 * Reads: PatrolComponent, MovementComponent, ColliderComponent. Writes: TransformComponent, PatrolComponent.
 */
public class PatrolSystem {

    private final World world;

    public PatrolSystem(World world) {
        this.world = world;
    }

    public void update() {
        TileGrid grid = world.getLevel().getGrid();
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            PatrolComponent patrol = entity.getPatrol();
            MovementComponent movement = entity.getMovement();
            ColliderComponent collider = entity.getCollider();
            if (patrol == null || movement == null || collider == null) continue;
            // ... turn at the range ends, walls and ledges, then set the walking velocity
        }
    }

    private static boolean shouldTurn(TileGrid grid, TransformComponent transform, ColliderComponent collider,
                                      MovementComponent movement, PatrolComponent patrol) {
        // ...
    }
}
```

## Component Classes (Data-Oriented)

Components should be **thin data holders**, not behavior containers.

```java
package com.hung.component;

import com.badlogic.gdx.math.Vector2;

/**
 * Stores position and rotation data.
 * Systems (not components) update these values.
 */
public class TransformComponent {
    public Vector2 position;
    public Vector2 velocity;
    public float rotation;
    
    public TransformComponent() {
        this.position = new Vector2();
        this.velocity = new Vector2();
        this.rotation = 0f;
    }
}
```

### Component Naming
- ✅ `TransformComponent`, `HealthComponent`, `WeaponComponent`
- ❌ `updateTransform()` - put in a system instead

## Entities Are Containers

`Entity` (`com.hung.entity`) holds a transform plus optional components and has no behaviour; factories
assemble entities, systems act on them (see [02-ARCHITECTURE.md](02-ARCHITECTURE.md)).

```java
Entity enemy = new Entity();
enemy.setMovement(movement);
enemy.setHealth(new HealthComponent(40));
```

**Never create entity subclasses or inheritance chains:**
- ❌ `Entity → Character → Enemy → ChaserEnemy → FastChaser`
- ✅ `Entity` with mix of reusable components

## System Classes (Behavior)

Systems handle logic, entities hold data. A system walks the world's entities each frame and skips the
ones without the components it needs; `GameScreen` calls the systems in a fixed order
([02-ARCHITECTURE.md → Frame order](02-ARCHITECTURE.md#frame-order)).

```java
package com.hung.physics;

public class PhysicsSystem {

    private final World world;
    private final CollisionSystem collisionSystem;

    public PhysicsSystem(World world, CollisionSystem collisionSystem) {
        this.world = world;
        this.collisionSystem = collisionSystem;
    }

    public void update(float delta) {
        PhysicsSettings physics = world.getLevel().getPhysics();
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            if (entity.getProjectile() != null) continue; // ProjectileSystem moves bullets in collision-checked steps
            TransformComponent transform = entity.getTransform();
            MovementComponent movement = entity.getMovement();
            if (movement != null && !movement.isClimbing && !movement.isFlying) {
                transform.velocity.y = Math.max(transform.velocity.y - physics.gravity * delta, -physics.maxFallSpeed);
            }

            if (entity.getCollider() != null) {
                collisionSystem.move(entity, transform.velocity.x * delta, transform.velocity.y * delta);
            } else {
                transform.position.mulAdd(transform.velocity, delta);
            }
        }
    }
}
```

`CollisionSystem` is not called by `GameScreen`: `PhysicsSystem` owns it and calls
`move(entity, dx, dy)`, which moves one collider box through the **tile grid**, one axis at a time (walls
stop it, it lands on solid tiles, platforms and ladder tops). Bodies against bodies (attacks, bullets,
touching enemies) are checked by the combat systems through `Hitboxes`, not by `CollisionSystem`.

## Interface Design

Make behavior contracts clear. The player's intent is the `Controller` interface; `InputSystem` only
talks to it, and `KeyboardController` is the implementation:

```java
package com.hung.input;

import com.badlogic.gdx.math.Vector2;

/**
 * Source of the player's intent (KeyboardController). Controllers only report what is wanted;
 * InputSystem decides what actually happens. Enemies have no controller: PatrolSystem and
 * EnemyBrainSystem drive them through their components.
 */
public interface Controller {
    Vector2 getMovementDirection();   // each axis -1..1: walk (x), climb / crouch (y)
    boolean isJumpPressed();
    boolean isAttackPressed();
    boolean isAttackHeld();           // full-auto firearms keep firing
    boolean isReloadPressed();
    int getWeaponSlotPressed();       // 0-based slot, or -1
    boolean isNextWeaponPressed();
}
```

`KeyboardController` polls the keyboard and mouse when asked and keeps no state between frames. There is
no AI controller: enemy movement and behaviour live in their components (`PatrolComponent`,
`EnemyBrainComponent`) and systems (`PatrolSystem`, `EnemyBrainSystem`).

## Design Patterns (Use When Needed)

### Factory Pattern
```java
// Build entities by composition from data files; callers never assemble components themselves
Entity player = EntityFactory.createCharacter(definition, definitionPath, resources, x, y,
    new KeyboardController(), physics);
Entity enemy = EnemyFactory.createEnemy(enemyDefinition, enemyPath, resources, spawn);
```

### Strategy Pattern
Swap behaviour behind a small interface instead of subclassing: `InputSystem` works with any `Controller`,
and `PauseMenu` reports choices through `PauseMenu.Actions`, which `GameScreen` implements. Add an interface
only when there is a real second implementation or a boundary to protect.

### State Machines
There is no generic state-machine class. State is data in components and each system decides transitions:
`AnimationSystem` picks the animation by priority (DEATH > HIT > ATTACK > SHOOT > climbing > CROUCH > JUMP >
WALK > IDLE), `EnemyBrainSystem` leaves an enemy to `PatrolSystem` until it notices the player, then
chases and attacks (`EnemyBrainComponent.isEngaged`, `cooldown`), and
`DoorSystem` steps `RoomTransition` through NONE → FADING_OUT → FADING_IN. Don't add a state-machine API
without a behaviour that needs it.

## Code Quality Rules for Agents

| Rule | Example |
|------|---------|
| Always use delta time | `position.x += velocity * delta;` NOT `position.x += 5;` |
| Resource caching | Load textures in constructor, reuse them |
| No null in loops | Separate allocation from game loops |
| Composition first | Add components, don't extend classes |
| Clear responsibilities | One system = one job |
| Testable code | No hardcoded values in logic |
| Constants at top | All magic numbers → `static final` |
| Comment only where needed | See [Comments](#comments) |
| Package by feature | `com.hung.combat.*` not `com.hung.utils` |

## Comments

Comments are for what the code cannot say. Write few, and keep each one short.

**Write a comment for:**
- **Why**, when the reason isn't visible: a workaround, an ordering constraint, a deliberate trade-off.
  `// Backwards, so removing an entity doesn't skip the next one`
- **A rule or contract** a reader must respect: units and coordinate spaces, invariants, "call after X".
  `/** Read-only view: change it with setRespawnPoint. */`
- **A non-obvious formula or magic value** whose meaning isn't in its name (the source or the derivation).
- **The class's job**, in a short Javadoc on public classes and on public methods whose name doesn't say
  everything (what it returns in edge cases, what it changes).

**Don't write a comment that:**
- restates the code (`// set the health`, `// loop over entities`, `/** Returns the width. */` on `getWidth()`);
- labels obvious blocks or narrates steps the method names already tell;
- records history or who changed what (that is git's job), or keeps commented-out code;
- explains how the code was before a refactor, or what a reviewer asked for.

**Keep them true.** A comment that no longer matches the code is worse than none: update or delete it in
the same change. Prefer a clearer name or a small extracted method over a comment that explains a tangle.

## Forbidden Patterns

❌ **Do NOT do this:**

```java
// 1. Game logic in lwjgl3
class Lwjgl3Launcher {
    void updatePlayer() { }  // NO! Goes in core
}

// 2. Deep inheritance
Enemy → ChaserEnemy → FastChaser → VeryFastChaser

// 3. Static mess
class Utils { static void update() { } }

// 4. Texture loading every frame
render() {
    Texture tex = new Texture("player.png");  // NO!
}

// 5. Hardcoded values
position.x += 5.0f;  // NO! Use PLAYER_SPEED constant

// 6. Complex entity constructors
new Enemy(x, y, type, ai, weapon, animation, color);  // NO!
// Use factory or builder instead
```
