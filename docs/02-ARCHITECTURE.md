# 02 - Architecture

[← Index](README.md)

How the game is put together. Every class, file by file, with its size and role: [01-SOURCE-MAP.md](01-SOURCE-MAP.md).
This page describes the design and the rules; it only names classes that exist.

## Design

A **component-based architecture inspired by ECS**, adapted for Java + LibGDX:

- `Entity` is a container: a transform plus optional components (null when absent). It has no behaviour.
- Components are data. Most expose public fields that systems read and write every frame; a component
  encapsulates its state only where a rule must hold (see [Encapsulated components](#encapsulated-components)).
- Systems hold the behaviour. Each walks `World.getEntities()` and skips entities without the components it needs.
- Capabilities come from composition. There is no `Player` or `Enemy` subclass: the factories assemble
  the right components (`EntityFactory`, `EnemyFactory`, `WeaponFactory`).

Inheritance is used only where LibGDX asks for it: `MyGame extends Game`, `GameScreen` / `MenuScreen extends
ScreenAdapter`, `CameraInput extends InputAdapter`. Polymorphism comes from small interfaces with real
contracts: `Controller`, `EventListener<T>`, `Navigator`, `PauseMenu.Actions`.

## Application lifecycle

```text
Lwjgl3Launcher ─► MyGame.create(): batch, ResourceLoader, SaveManager, Settings, UiTheme
                  └─► MenuScreen (title screen)
                        │ New Game / Continue  (Navigator)
                        ▼
                  MyGame.startRun(GameSession): WorldLoader.load(map) → GameScreen (applies the save if continuing)
                        │ Esc → "Save and return" (Navigator)
                        ▼
                  MyGame.endRun(): dispose the GameScreen, unload its map → MenuScreen
```

Screen switches are posted with `Gdx.app.postRunnable`, so the screen that asked finishes its frame first.
`MyGame` owns the long-lived services (batch, resources, saves, settings, UI theme); a run owns its `World`,
its systems and its `EventBus`, and all of them are dropped together when the run ends.

## Frame order

`GameScreen.render()` runs, in this order (steps 1–14 are `GameScreen.update()`; they are skipped while the
character sheet or the pause menu is open, or while a door fades out):

1. `InputSystem`: controller intent → velocity, jump, crouch, ladders, attack / fire / reload / weapon requests
2. `WeaponSwitchSystem`: applies a weapon request (and switches the animation stance)
3. `PatrolSystem`: enemies walk their patrol range
4. `EnemyBrainSystem`: enemies that see the player chase and attack (overrides the patrol velocity)
5. `PhysicsSystem`, which moves colliders through `CollisionSystem`
6. `HazardSystem`: checkpoints, traps, falling out, exit (publishes `CheckpointEvent`, `ExitReachedEvent`)
7. `ContactDamageSystem`: touching an enemy hurts
8. `AttackSystem`: melee hits from the current frame's attack box; breaks BREAKABLE tiles
9. `ShootingSystem`: firearms, reloads, muzzle effects
10. `ProjectileSystem`: bullets against tiles and bodies
11. `HealthSystem`: hurt / invulnerability timers, death, player respawn
12. `AnimationSystem`: picks each entity's animation and advances it
13. `EnemyEffectSystem`: spawns enemy effects on their frames
14. `EffectSystem`: plays and removes effect entities
15. `DoorSystem.update`: room transitions (runs even while the world is paused by the fade)
16. Drawing: map + entities → lights → debug overlay → door fade → HUD → minimap → character sheet → toast → pause menu → FPS

The order matters and is relied on: requests set in steps 1 and 4 are consumed in steps 2, 8 and 9; the
brain runs after the patrol so it can override it; animation runs after health and attacks so HIT, DEATH and
ATTACK win; enemy effects run after animation to see the new frame. Change it only with tests that show why.

## Input

`com.hung.input.Controller` is the player's intent: movement direction, jump, attack (pressed / held),
reload, weapon slot, next weapon. `KeyboardController` implements it by polling the keyboard and mouse
when asked, so it keeps no state between frames. `InputSystem` only talks to the interface.

Every read of the player's intent goes through the controller.

**Weapon slot keys and the character sheet.** While the sheet is open the world update (and so
`InputSystem`) doesn't run, so `GameScreen.handleToggles()` reads `controller.getWeaponSlotPressed()` itself,
sets `ArsenalComponent.requestedSlot` and runs `WeaponSwitchSystem`. Normally only one of the two paths reads
the key in a frame, with one exception:

- Esc is handled after `handleToggles()`. If Esc and a number key are pressed in the same frame while the
  sheet is open, the sheet reads the key and switches the weapon, then Esc closes the sheet, the normal
  update runs and `InputSystem` reads the same key again.
- The second request asks for the slot that is already in hand, and `WeaponSwitchSystem` ignores a request
  for the current slot (`requestedSlot != current`), so nothing changes.
- The code before the Phase 2 refactor read the keys at the same point and behaved the same way.

**Several number keys in one frame (intentional change in Phase 2).** `KeyboardController` returns the
lowest pressed slot. With the sheet open, the lower-numbered slot therefore wins; the sheet's own key loop
used before Phase 2 picked the higher-numbered one. Normal gameplay always picked the lower slot, so both
paths now behave the same.

Enemies have no controller: `PatrolSystem` and `EnemyBrainSystem` write their components directly.
Moving them behind an AI controller would change their update order and is not planned.

## Entities and components

```java
Entity enemy = new Entity();                       // has a TransformComponent
enemy.setMovement(movement);
enemy.setHealth(new HealthComponent(40));
enemy.setBrain(brain);                             // EnemyFactory assembles this from character.json
```

`Entity` has one typed field per component kind (getter + setter, null = absent). Adding a component kind
means adding a field; the fixed fields are kept on purpose: typed, fast, and readable at this size.

### Encapsulated components

A component hides its state when a rule spans several fields:

| Component | Rule | API |
|-----------|------|-----|
| `HealthComponent` | 0 ≤ health ≤ maxHealth, isDead ⇔ health == 0, maxHealth ≥ 1 | `new HealthComponent(max)`, `applyDamage(n)`, `restoreFull()`, `setHealthClamped(n)`, getters |
| `AnimationComponent` | the per-stance maps (animations, hand anchors, key frame sources, overlays, attack boxes) always belong to the same stance | `useStance(name)` switches all of them; getters to read |
| `World` | the respawn point changes in one place | `setRespawnPoint(x, y)`; `getRespawnPoint()` is for reading |

The remaining fields (timers, velocities, flags) stay public: systems update them every frame and no rule
spans them.

## Systems

| System | Package | Reads | Writes |
|--------|---------|-------|--------|
| InputSystem | input | Controller, Movement, Collider, Health, tile grid | Transform, Movement, Attack / Weapon / Arsenal requests |
| WeaponSwitchSystem | combat | Arsenal, Attack, Health | Weapon, Arsenal, Animation stance |
| PatrolSystem | ai | Patrol, Movement, Collider, Health, tile grid | Transform, Patrol |
| EnemyBrainSystem | ai | Brain, Animation, Health, player position, tile grid | Transform, Attack request, Brain |
| PhysicsSystem | physics | Movement, level physics | Transform (through CollisionSystem) |
| CollisionSystem | physics | Collider, tile grid | Transform, Movement (grounded, blocked) |
| HazardSystem | physics | Collider, tile grid, checkpoints, exit | World respawn point, player position; publishes events |
| ContactDamageSystem | combat | Collider, Health | Health (through Damage) |
| AttackSystem | combat | Attack, Animation, Health (attack box via Hitboxes) | Health (through Damage), tile grid + map |
| ShootingSystem | combat | Weapon, Movement, Health | Weapon; adds projectile and effect entities |
| ProjectileSystem | combat | Projectile, Collider, tile grid | Health (through Damage); removes projectiles |
| HealthSystem | combat | Health, Animation | Health timers, Transform; removes dead enemies, respawns the player |
| AnimationSystem | animation | Transform, Movement, Health, Attack, Weapon | Animation (type, time) |
| EnemyEffectSystem | animation | EnemyEffects, Animation | adds effect entities |
| EffectSystem | animation | Sprite, Effect | removes finished effects |
| DoorSystem | world | Door, player body | RoomTransition; publishes `RoomTransitionEvent` |
| RenderingSystem | rendering | map, Transform, Animation, Weapon, Sprite | screen |

Systems never call each other. They share small static helpers instead: `Damage.apply` (one hit),
`PlayerRespawn.respawn`, `Effects.spawn`, `Hitboxes` (world-space boxes) and `AnimationFrames` (current frame).
`PhysicsSystem` owns its `CollisionSystem`.

## Communication

1. **Component data** (the default). A system leaves a request or a result in a component and a later system
   acts on it: `attack.isRequested`, `weapon.isFireRequested`, `arsenal.requestedSlot`, `movement.isGrounded`.
2. **EventBus** (`com.hung.core`), only when one producer must reach listeners it should not know:

   | Event | Publisher | Listeners |
   |-------|-----------|-----------|
   | `RoomTransitionEvent` | `DoorSystem` | `CameraController` (clamps to the room), `MinimapState` (reveals it) |
   | `CheckpointEvent` | `HazardSystem` | `GameScreen` (saves, shows a toast) |
   | `ExitReachedEvent` | `HazardSystem` | `GameScreen` (marks the run complete, opens the end screen) |

   Publishers reuse one event object, so listeners copy what they keep. Each `GameScreen` creates its own
   bus and subscribes everything in its constructor; the bus and its listeners go away with the screen, so
   nothing needs unsubscribing. `DoorSystem.start()` announces the first room and must run after the
   subscriptions (`GameScreen.show()`).
3. **Screen navigation** goes through `Navigator` (`MyGame`); the pause menu reports choices through
   `PauseMenu.Actions` (`GameScreen`).

## Saving

| Class | Responsibility |
|-------|----------------|
| `SaveData` | The JSON written to a slot (schema version 1). Plain fields only. |
| `SaveSnapshot` | `capture(...)` turns the world into SaveData; `apply(...)` puts a SaveData back into a freshly loaded world. No file access, no LibGDX runtime: unit tested. |
| `SaveManager` | The three slot files in `~/.mygame/saves/`: read (EMPTY / OK / CORRUPT / INCOMPATIBLE, never throws), write through a temporary file, delete. |
| `GameSession` | The run being played: slot, mode, play time, the save it continues. |

`GameScreen` saves on `CheckpointEvent`, on reaching the exit and when the player leaves (menu, quit,
window closed or minimised).

## Packages

```text
core/       MyGame, GameScreen, GameMode, Navigator, EventBus, EventListener
world/      World, Level, WorldLoader, RoomLoader, Room, DoorSystem, RoomTransition(+Event), CheckpointEvent,
            ExitReachedEvent, TileGrid, TileType, PhysicsSettings, EnemySpawn, TrialEnemies, PlayerRespawn, LightSource
entity/     Entity, EntityFactory, EnemyFactory, WeaponFactory, CharacterDefinition, EnemyDefinition
component/  Transform, Movement, Collider, Animation, Health, Attack, Weapon, Arsenal, Patrol, EnemyBrain,
            EnemyEffects, Sprite, Effect, Projectile, Door
input/      Controller, KeyboardController, InputSystem, CameraInput
ai/         PatrolSystem, EnemyBrainSystem
physics/    PhysicsSystem, CollisionSystem, HazardSystem, Hitboxes
combat/     AttackSystem, ContactDamageSystem, ShootingSystem, ProjectileSystem, HealthSystem, WeaponSwitchSystem,
            Damage, Effects, Weapon, WeaponDefinition, FireDefinition, EffectDefinition, WeaponEffect, AttachmentProfile
animation/  AnimationSystem, AnimationFrames, AnimationSet, AnimationType, AnimationDefinition, FrameDefinition,
            HandAnchor, EffectSystem, EnemyEffectSystem
rendering/  RenderingSystem, CameraController, LightingRenderer, HudRenderer, StatsPanel, DebugRenderer, FadeRenderer
ui/         MenuScreen, MenuView, Menu, UiTheme, PauseMenu, SettingsMenu, Toast, FpsOverlay, MinimapState, MinimapRenderer
save/       SaveData, SaveSnapshot, SaveManager, Settings, GameSession
util/       Constants, ResourceLoader, SmoothDamp
```

There is no audio, item or state-machine package yet.

## Rules

1. **Entities are containers.** No `update()` or `render()` on an entity, no entity subclasses; add a component
   and a system instead.
2. **Systems don't call systems.** Share data through components, use a static helper for shared rules
   (`Damage.apply(target, amount, sourceX)` rather than touching hit points), and publish an event only
   when the receivers must stay unknown to the sender.
3. **Change encapsulated state through its API** (`HealthComponent`, `AnimationComponent.useStance`,
   `World.setRespawnPoint`).
4. **Player intent comes from the `Controller`.** Don't read keys for gameplay actions anywhere else.
5. **Keep the frame order** (see above) unless a test shows the change is needed.
6. **Load up front, never in `update()` / `render()`;** don't allocate in per-frame code.
7. **Data comes from files.** Maps, characters, enemies and weapons are JSON / TMX written by the tools in
   `build_asset/`; keep their schemas and the save schema stable.

## Known limitations

- Package dependencies have cycles (`world` ↔ `physics` through `DoorSystem` / `Hitboxes` and `PlayerRespawn`;
  `core` ↔ `world` / `physics` through the EventBus and events). Accepted for now.
- `AttackSystem` breaks walls by editing the cached TiledMap; `MyGame` unloads the map after every run so the
  next run starts clean. Broken walls and killed enemies are not saved.
- Enemy numbers (health, damage, aggro range, cooldowns) are constants in `EnemyFactory`.

## Asset pipeline

```text
build_asset/build_map/platformer-map-generator   ──node tools/export-game.mjs──►  assets/maps/<name>/
        (picks enemies for each spawn)                                    └─────►  assets/sprites/enemies/<id>/ (+ effects/)
build_asset/build_enemy/enemy-character-generator ──node tools/generate.mjs──►   its export/enemies/<id>/ (roster)
build_asset/build_character/…                     ──node tools/export-game.mjs──►  assets/sprites/players/<id>/
build_asset/build_weapon/…                        ──node tools/export-game.mjs──►  assets/sprites/weapons/<id>/, assets/attachments/
build_asset/build_ui                              ──py export-game.py──────────►  assets/ui/ (fonts, hazard tiles, title backdrop)
```

The desktop launcher (`lwjgl3/.../Lwjgl3Launcher`) only configures the window (1280×720) and starts `MyGame`.
Coordinates and data files: [05-SYSTEMS-GUIDE.md → Coordinates & Data Files](05-SYSTEMS-GUIDE.md#0-coordinates--data-files).
