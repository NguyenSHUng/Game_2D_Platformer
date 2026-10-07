# 02 - Architecture

[← Index](README.md)

## Overall Design Philosophy

This project uses a **Component-Based Architecture** inspired by ECS (Entity Component System) but adapted for Java + LibGDX.

**Core principle**: Entities own components, systems act on components. No behavior in components.

```
┌─────────────────────────────────────────┐
│           Game Window                    │
│  (LibGDX - Handles rendering, input)    │
└────────┬────────────────────────────┬───┘
         │                            │
    LibGDX                       Our Code
     Loop                      (core module)
         │                            │
         └─────────────────┬──────────┘
                           │
                    ┌──────▼─────────┐
                    │   Game Loop    │
                    │ (MyGame class) │
                    └──────┬─────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
    ┌───▼───┐         ┌───▼───┐         ┌──▼──┐
    │ Input │         │Update │         │Render
    │ (60x) │         │ (60x) │         │ (60x)
    └───┬───┘         └───┬───┘         └──┬──┘
        │                 │                 │
    Input Layer      Systems Layer    Rendering Layer
```

## Game Loop (The Main Cycle)

```java
// Runs ~60 times per second
void update(float delta) {
    // 1. INPUT PHASE
    handleInput();  // Read keyboard, mouse, gamepad
    
    // 2. UPDATE PHASE
    updateAI(delta);              // Enemy decisions
    updateMovement(delta);         // Position changes
    updateAnimation(delta);        // Frame advancement
    updateCombat(delta);           // Weapon cooldowns
    
    // 3. PHYSICS PHASE
    collisionSystem.update(delta); // Detect collisions
    
    // 4. AUDIO PHASE
    audioSystem.update(delta);     // Play sounds
    
    // 5. RENDERING PHASE
    render();                      // Draw everything
}
```

**Important**: Each phase reads data, modifies data, but doesn't call other phases. Phases are independent.

## Layer 1: Input Controllers

**Location**: `com.hung.input`

Controllers translate user input (keyboard/mouse/AI) into a common format:

```java
public interface Controller {
    Vector2 getMovementDirection();  // -1 to 1
    Vector2 getAimDirection();       // -1 to 1
    boolean isFirePressed();
    boolean isAbilityPressed();
}
```

**Implementations**:
- `KeyboardController` - Player input
- `AIController` - Enemy behavior
- `GamepadController` - (future) gamepad support
- `ReplayController` - (future) playback

**Controllers do NOT know about game state.** They just output numbers.

## Layer 2: Entities & Components

**Locations**: 
- `com.hung.entity` - Entity base classes
- `com.hung.component` - Component data

### Entity Base Class

```java
public class Entity {
    protected TransformComponent transform;
    protected HealthComponent health;
    
    public Entity(float x, float y) {
        transform = new TransformComponent();
        transform.position.set(x, y);
    }
    
    public void update(float delta) { }  // Override in subclass
    public void render(Batch batch) { }  // Override in subclass
}
```

### Component Examples

Components are pure data:

```java
// Location: com.hung.component

public class TransformComponent {
    public Vector2 position;    // World position
    public Vector2 velocity;    // Direction and speed
    public float rotation;      // Rotation in degrees
}

public class HealthComponent {
    public float health;
    public float maxHealth;
    public boolean isAlive;
}

public class WeaponComponent {
    public Weapon weapon;       // Current weapon
    public float cooldown;      // Seconds until next shot
}

public class AnimationComponent {
    public Animation currentAnimation;
    public float elapsedTime;
}
```

## Layer 3: Systems (The Behavior)

**Location**: `com.hung.{system}`

Systems read from entities and modify their components:

```java
// Example: PhysicsSystem
public class PhysicsSystem {
    private List<Entity> entities;
    
    public PhysicsSystem(List<Entity> entities) {
        this.entities = entities;
    }
    
    public void update(float delta) {
        for (Entity entity : entities) {
            TransformComponent transform = entity.getTransform();
            
            // Move entity
            transform.position.x += transform.velocity.x * delta;
            transform.position.y += transform.velocity.y * delta;
        }
    }
}
```

### System Responsibilities

| System | Location | Reads | Writes | When |
|--------|----------|-------|--------|------|
| InputSystem ✅ | input/ | Controller, Movement | Transform (velocity.x, jump, facing) | Every frame |
| PatrolSystem ✅ | ai/ | Patrol, Movement, Collider, tile grid | Transform (velocity.x, facing), Patrol | Every frame |
| PhysicsSystem ✅ | physics/ | Movement, level physics | Transform (gravity, position via CollisionSystem) | Every frame |
| CollisionSystem ✅ | physics/ | Collider, tile grid | Transform, Movement (isGrounded, isBlocked) | Called by PhysicsSystem |
| AnimationSystem ✅ | animation/ | Transform, Movement | Animation (type, time) | Every frame |
| CombatSystem | combat/ | Weapon, Cooldown | Cooldown | Every frame |
| RenderingSystem ✅ | rendering/ | Tiled map, Transform, Animation | Screen | End of frame |

## Implemented So Far (2D Platformer Prototype)

```
core/src/main/java/com/hung/
├── core/       MyGame (entry point, extends LibGDX Game), GameScreen (runs systems; F2 / no player = free camera),
│               EventBus + EventListener (observer hub: systems publish events, others listen)
├── world/      World (+ respawn point, RoomTransition), Level (Tiled map + collision grid + checkpoints + rooms),
│               WorldLoader (.tmx), RoomLoader (rooms + doors layers), Room, DoorSystem (room transitions →
│               RoomTransitionEvent), TileGrid, TileType, PhysicsSettings (movement contract from the map),
│               EnemySpawn, PlayerRespawn
├── entity/     Entity (component container), EntityFactory, CharacterDefinition (player JSON),
│               EnemyDefinition (enemy generator's character.json)
├── component/  Transform, Movement, Collider, Animation, Weapon, Patrol, Attack, Health
├── input/      Controller, KeyboardController, InputSystem (walk/jump/crouch/ladders/attack), CameraInput
├── ai/         PatrolSystem (enemies walk their patrol range)
├── physics/    PhysicsSystem (gravity + movement), CollisionSystem (box vs tile grid), HazardSystem
│               (checkpoints, spikes/toxic, exit), Hitboxes (body / attack boxes in world units)
├── animation/  AnimationType, AnimationDefinition + FrameDefinition (JSON), HandAnchor, AnimationSystem,
│               AnimationFrames (current frame helpers)
├── combat/     AttackSystem (melee from attack boxes), Damage, ContactDamageSystem, HealthSystem,
│               WeaponDefinition (JSON), Weapon, AttachmentProfile (JSON) — see character-weapon-system.md
├── rendering/  RenderingSystem (tile layers + entities), CameraController (room-clamped, SmoothDamp follow),
│               DebugRenderer, HudRenderer, LightingRenderer, FadeRenderer, StatsPanel
├── ui/         MinimapState (exploration, listens to RoomTransitionEvent), MinimapRenderer
└── util/       Constants, ResourceLoader (textures, Tiled maps, JSON), SmoothDamp

assets/
├── maps/<name>/                    map.tmx + tilesets/*.png + level.json   ← map generator (tools/export-game.mjs)
├── sprites/enemies/<id>/           character.json + sprite-sheet.png         ← enemy generator (copied by the same export)
├── sprites/players/<id>/           <id>.json + one strip per animation      ← character generator (tools/export-game.mjs)
├── sprites/weapons/<id>/            <id>.png + <id>.json                     ← weapon generator (tools/export-game.mjs)
└── attachments/<character>/        one profile per weapon: how this character holds it

build_asset/build_map/   map generator (HTML tool) — PROGRESS.md
build_asset/build_enemy/ enemy generator (HTML tool) — PROGRESS.md
build_asset/build_character/ player character generator (HTML tool) — PROGRESS.md
build_asset/build_weapon/ weapon generator (HTML tool) — PROGRESS.md
Image_Processing/        Python art tools: pose transfer, weapon layers, export_profile.py, weapon_tuner.py — not shipped
```

The desktop launcher (`lwjgl3/.../Lwjgl3Launcher`) only starts `com.hung.core.MyGame` and sets the window (1280x720).
Maps, enemies and characters are data — see [05-SYSTEMS-GUIDE.md → Coordinates & Data Files](05-SYSTEMS-GUIDE.md#0-coordinates--data-files).

### Asset pipeline

```text
build_asset/build_map/platformer-map-generator   ──node tools/export-game.mjs──►  assets/maps/<name>/
        (picks enemies for each spawn)                                    └─────►  assets/sprites/enemies/<id>/
build_asset/build_enemy/enemy-character-generator ──node tools/generate.mjs──►   its export/enemies/<id>/ (roster)
```

## Package Organization

### Core Package (`core`)
Entry point and game lifecycle.

```java
com.hung.core
├── MyGame.java            // Main game class (extends LibGDX Game)  ✅
├── GameScreen.java        // LibGDX screen, runs systems in order    ✅
├── GameContext.java       // Shared resources                        (planned)
└── GameState.java         // Current game state                      (planned)
```

### World Package (`world`)
Everything about the game world.

```java
com.hung.world
├── World.java             // World container                                   ✅
├── Level.java             // Tiled map + collision grid + spawn/exit           ✅
├── TileGrid.java          // Collision tiles (row 0 = bottom)                  ✅
├── TileType.java          // SOLID, PLATFORM, LADDER, SPIKE, TOXIC, BREAKABLE  ✅
├── PhysicsSettings.java   // Gravity / walk / jump from the map's physics.*   ✅
├── EnemySpawn.java        // One enemy placement from the map                  ✅
└── WorldLoader.java       // .tmx → World (levels are generated offline by build_asset/build_map) ✅
```

### Entity Package (`entity`)
Game object base classes.

```java
com.hung.entity
├── Entity.java            // Base class
├── Player.java            // Player entity
├── Enemy.java             // Enemy base
├── Projectile.java        // Bullets, etc
└── Item.java              // Pickups
```

### Component Package (`component`)
Pure data holders.

```java
com.hung.component
├── TransformComponent.java
├── HealthComponent.java
├── WeaponComponent.java
├── AnimationComponent.java
├── MovementComponent.java
└── InventoryComponent.java
```

### System Packages

Each system lives in its domain:

```java
com.hung.ai             // AI logic
├── AIController.java
├── StateMachine.java
└── states/
    ├── IdleState.java
    ├── ChaseState.java
    └── AttackState.java

com.hung.physics        // Collision/movement
├── CollisionSystem.java
├── Hitbox.java
└── Raycast.java

com.hung.combat         // Weapons/damage
├── Weapon.java
├── Projectile.java
└── Damage.java

com.hung.input          // Controllers
├── Controller.java
├── KeyboardController.java
└── AIController.java

com.hung.animation      // Sprites/animation
├── Animation.java
├── AnimationFrame.java
└── Sprite.java

com.hung.rendering      // Drawing
├── Renderer.java
├── Camera.java
└── SpriteSheet.java

com.hung.audio          // Sound
├── SoundManager.java
└── MusicPlayer.java

com.hung.ui             // Menus/HUD
├── HUD.java
├── Button.java
└── Menu.java

com.hung.util           // Helpers
├── Constants.java
├── Vector2D.java  (or use LibGDX Vector2)
└── ResourceLoader.java
```

## Important Rules for Architecture

### Rule 1: Systems Don't Know Each Other
When one system's result must reach several others, publish an event on the `EventBus`
(`com.hung.core`) instead of calling them: `DoorSystem` publishes `RoomTransitionEvent`; the camera and
the minimap subscribe in `GameScreen` and never see the doors.

Instead of:
```java
❌ PhysicsSystem calls CombatSystem
combatSystem.applyDamage(enemy, damage);
```

Do this:
```java
✅ Store result, let systems read it
if (collision.occurred) {
    enemy.getHealth().health -= damage;
}
```

### Rule 2: Controllers Don't Have State
```java
❌ WRONG
class KeyboardController {
    void update() { /* reads input */ }
    void render() { /* draws debug */ }
}

✅ RIGHT
class KeyboardController {
    Vector2 getMovementDirection() { /* just return */ }
    boolean isFirePressed() { /* just return */ }
}
```

### Rule 3: Entities Are Containers, Not Managers
```java
❌ WRONG
public class Player {
    void update(delta) {
        moveByInput();
        updateAnimation();
        checkCollisions();
        updateWeapon();
    }
}

✅ RIGHT
public class Player {
    // Systems will call update() on each component
    // through their respective systems
}
```

### Rule 4: Data Flows One Direction
```
Input → Entity Components → Systems → Rendering
```

Systems modify components. Rendering reads components. Never backwards.

## Initialization Flow

```java
public class Game extends ApplicationAdapter {
    
    private World world;
    
    @Override
    public void create() {
        // 1. Create world
        world = new World();
        
        // 2. Create player
        Player player = new Player(100, 100);
        world.addEntity(player);
        
        // 3. Create systems (with reference to world)
        physicsSystem = new PhysicsSystem(world.getEntities());
        aiSystem = new AISystem(world.getEntities());
        renderSystem = new RenderSystem();
        
        // 4. Initialize assets
        assetManager.load("player.png");
        // ... etc
    }
    
    @Override
    public void render() {
        // Call update phase
        update(Gdx.graphics.getDeltaTime());
        
        // Call render phase
        renderSystem.render(world);
    }
    
    private void update(float delta) {
        // All systems update in order
        inputSystem.update(delta, world);
        physicsSystem.update(delta);
        aiSystem.update(delta);
        combatSystem.update(delta);
        collisionSystem.update(delta);
        animationSystem.update(delta);
    }
}
```
