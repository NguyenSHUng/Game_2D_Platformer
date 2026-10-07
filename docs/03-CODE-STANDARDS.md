# 03 - Code Standards

[← Index](README.md)

## Package Structure (Mandatory)

All code goes under:
```
com.hung.{system}
```

**System packages** (create only these):

```
com.hung.core          # Game loop, screens, main classes
com.hung.world         # World, levels, tilemap, maze
com.hung.entity        # Base entity classes, game objects
com.hung.component     # Data components (Transform, Health, etc)
com.hung.ai            # AI systems (StateMachine, controllers)
com.hung.combat        # Weapons, damage, projectiles
com.hung.physics       # Collision, hitbox, raycast
com.hung.input         # Input controllers, keyboard
com.hung.animation     # Sprites, animations, frames
com.hung.rendering     # Render system, camera
com.hung.item          # Pickups, upgrades
com.hung.audio         # Sounds, music
com.hung.ui            # HUD, menus, buttons
com.hung.util          # Helpers, constants, loaders
```

**Do NOT create**:
- `models/`, `services/`, `helpers/` - use existing packages
- Deep sub-packages - max 1 level deep

## Naming Conventions

### Classes
```java
// Concrete classes - PascalCase
public class Player { }
public class Enemy { }
public class CollisionSystem { }

// Interfaces - start with I or end with -able (both acceptable)
public interface IController { }
public interface Damageable { }  // Preferred if short

// Abstract classes - prefix with Abstract
public abstract class AbstractComponent { }
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

```java
package com.hung.entity;

import com.badlogic.gdx.math.Vector2;

/**
 * Brief description of what this class does.
 * 
 * Responsibilities:
 * - Handles X
 * - Manages Y
 */
public class Player {
    
    // ==================== CONSTANTS ====================
    private static final float MOVE_SPEED = 200f;
    private static final float ROTATION_SPEED = 10f;
    
    // ==================== FIELDS ====================
    private Vector2 position;
    private Vector2 velocity;
    private float health;
    private boolean isAlive;
    
    // ==================== CONSTRUCTOR ====================
    public Player(float x, float y) {
        this.position = new Vector2(x, y);
        this.velocity = new Vector2();
        this.health = 100f;
        this.isAlive = true;
    }
    
    // ==================== PUBLIC METHODS ====================
    public void update(float delta) {
        // Implementation
    }
    
    public void takeDamage(float amount) {
        // Implementation
    }
    
    // ==================== GETTERS ====================
    public Vector2 getPosition() { return position; }
    public float getHealth() { return health; }
    public boolean isAlive() { return isAlive; }
    
    // ==================== SETTERS ====================
    public void setPosition(float x, float y) { 
        this.position.set(x, y); 
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

## Entity Base Class Pattern

```java
public class Entity {
    protected TransformComponent transform;
    protected HealthComponent health;
    // ... other components
    
    public Entity(float x, float y) {
        this.transform = new TransformComponent();
        this.transform.position.set(x, y);
    }
    
    public void update(float delta) {
        // Override in subclasses
    }
    
    public void render(Batch batch) {
        // Override in subclasses
    }
}
```

**Never create large inheritance chains:**
- ❌ `Entity → Character → Enemy → ChaserEnemy → FastChaser`
- ✅ `Entity` with mix of reusable components

## System Classes (Behavior)

Systems handle logic, entities hold data.

```java
package com.hung.physics;

public class CollisionSystem {
    
    private List<Entity> entities;
    
    public CollisionSystem(List<Entity> entities) {
        this.entities = entities;
    }
    
    /**
     * Check all collisions this frame.
     * Call once per update().
     */
    public void update(float delta) {
        for (int i = 0; i < entities.size(); i++) {
            for (int j = i + 1; j < entities.size(); j++) {
                checkCollision(entities.get(i), entities.get(j));
            }
        }
    }
    
    private void checkCollision(Entity a, Entity b) {
        // Implementation
    }
}
```

## Interface Design

Make behavior contracts clear:

```java
package com.hung.input;

import com.badlogic.gdx.math.Vector2;

/**
 * Any object that can receive input should implement this.
 * Allows swapping: KeyboardController, AIController, GamepadController
 */
public interface Controller {
    Vector2 getMovementDirection();  // -1 to 1 on X/Y
    Vector2 getAimDirection();       // -1 to 1 normalized
    boolean isFirePressed();
    boolean isAbilityPressed();
}
```

## Design Patterns (Use When Needed)

### Factory Pattern
```java
// Create entities/weapons without exposing constructors
public class EnemyFactory {
    public static Enemy createChaser(float x, float y) { }
    public static Enemy createTurret(float x, float y) { }
}
```

### Strategy Pattern
```java
// Swap behaviors at runtime
public class Enemy {
    private AIStrategy strategy;  // Can be Chase, Patrol, Flee, etc
    
    public void setStrategy(AIStrategy newStrategy) {
        this.strategy = newStrategy;
    }
}
```

### State Machine Pattern
```java
// For complex behaviors
public class StateMachine {
    private State currentState;
    public void update(float delta) { currentState.update(delta); }
    public void changeState(State newState) { /* ... */ }
}
```

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
| Comment non-obvious | Not: `position += velocity;` YES: Complex AI logic |
| Package by feature | `com.hung.combat.*` not `com.hung.utils` |

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
