# 05 - Systems Guide

[← Index](README.md)

For AI: This explains each game system, what it does, and how it integrates.

Systems marked **✅ Implemented** exist in code; the rest are the plan.

## 0. Coordinates & Data Files

### World Coordinates
- **Plain 2D side view**: x is left/right, **y is up**, gravity pulls towards −y. There is no floor depth.
- **1 world unit = 1 map pixel**; map tiles are 16 × 16 px. Origin is the bottom-left of the map.
- `TransformComponent.position` is the **feet point** (bottom-center) of an entity. Sprites are drawn
  centered on x with their feet on y; `ColliderComponent` boxes span `x ± width/2`, `y .. y + height`.
- `Constants.VIEW_MIN_WIDTH × VIEW_MIN_HEIGHT` (640 × 360 world units ≈ 40 × 22 tiles) is the minimum
  visible area, so a 1280 × 720 window shows the 16 px pixel art at 2×.

### Level Data: `assets/maps/<name>/map.tmx` (Tiled)

Exported by the map generator: `node tools/export-game.mjs --seed <n> --name <name>` in
`build_asset/build_map/platformer-map-generator` (see that folder's README and `build_asset/build_map/PROGRESS.md`).
The file opens in the [Tiled](https://www.mapeditor.org) editor, so maps can be adjusted by hand.

| Part | Content |
|---|---|
| tile layers | `walls` · `decor_back` · `terrain` · `props` · `decor_front` (drawn back to front; entities between `props` and `decor_front`) |
| `terrain` tileset | every tile has a **`type`** property: `SOLID`, `PLATFORM`, `LADDER`, `SPIKE`, `TOXIC`, `BREAKABLE`, `EMPTY` → collision |
| `spawns` objects | `PLAYER_SPAWN` and `EXIT` rectangles (1 × 2 tiles; bottom edge = feet) |
| `enemies` objects | one `ENEMY` rectangle per enemy; properties `definition` (path of the enemy JSON), `enemy`, `patrolMinX`, `patrolMaxX`, `facing`, `hint`, `room` |
| `pois`, `lights`, `zones` | checkpoints/chests/signposts, point lights, rooms/landmarks (data for later systems) |
| `rooms` objects | one `ROOM` rectangle per room = its **camera bounds** (interior + 1 wall tile, grown over its secret pocket); `kind`, `region`, `onPath` |
| `doors` objects | one `DOOR` rectangle per door = its **trigger**; `room`, `targetRoom`, `targetDoor` (null = one-way drop), `kind` (`SIDE` / `LADDER` / `DROP`), `direction`, arrival feet point `spawnX` / `spawnY` (Tiled pixels, y down) |
| map properties | `physics.moveSpeed`, `physics.jumpHeight`, `physics.jumpDistance` (tiles), `seed`, `theme`; optional `playerCharacter`, `playerWeapon` (JSON paths) |

- Loaded by `com.hung.world.WorldLoader` → `Level` (map + `TileGrid` + `PhysicsSettings` + spawn/exit) + `World`.
- The start map is `Constants.START_MAP_PATH`.
- `level.json` next to the map is the generator's full data export (rooms, decor, lights…), not read by the game yet.
- **Physics contract:** the generator only builds jumps the player can make with 80% of
  `physics.*`, so `PhysicsSettings` derives gravity and jump speed from those values
  (air time T = jumpDistance / moveSpeed, gravity = 8·jumpHeight / T², jumpSpeed = 4·jumpHeight / T).
  Don't hard-code player speeds; change them in the generator and re-export.

### Enemy Data: `assets/sprites/enemies/<id>/character.json`

Written by the enemy generator (`build_asset/build_enemy/enemy-character-generator`) and copied here by the map export.
Read into `EntityFactory`/`EnemyDefinition` (unknown fields ignored):

- `spriteSheet` (`image`, `frameWidth`, `frameHeight`) — one row per animation in `sprite-sheet.png`
- `animations.<name>` — `state` (`IDLE`, `MOVE`→`WALK`, `ATTACK`, `DEFEND`, `HIT`, `DEATH`), `loop`, `frames[]`
  with the frame rect and its own `duration` (ms; honored by repeating the frame every 10 ms tick)
- `animations.move.move.groundSpeed` — patrol speed (px/s) that keeps the planted foot from sliding
- `anchors.feet` — ground line inside the frame; IDLE frame 0 `bounds` → collision box

Enemy art is drawn at scale 1 (same pixel size as the map). Pixel coordinates in the file are top-left, y down.

### Player Character Data: `assets/sprites/players/<id>/<id>.json`

Written by the character generator (`build_asset/build_character/player-character-generator`):
`node tools/export-game.mjs --id player_soldier --scale 0.5` copies the JSON and its strips into
`assets/sprites/players/<id>/`. The file holds much more (anchors, parts, palette…); the game reads:

```json
{
  "id": "player_soldier",
  "frameWidth": 64, "frameHeight": 64,
  "drawScale": 0.5,
  "footOffset": 0,
  "colliderWidth": 14, "colliderHeight": 53,
  "animations": [
    { "type": "WALK", "sheet": "walk.png", "frameCount": 8, "frameDuration": 0.1, "playMode": "LOOP",
      "frames": [ { "duration": 100, "event": "FOOTSTEP" }, ... ],
      "walk": { "groundSpeed": 30 } },
    { "type": "ATTACK", "sheet": "attack_knife.png", "frameCount": 7, "playMode": "NORMAL",
      "frames": [ ..., { "duration": 55, "event": "HIT", "attackBox": { "x": 50, "y": 29, "width": 14, "height": 9 } }, ... ] },
    { "type": "CROUCH", "sheet": "crouch.png", "frameCount": 4, "playMode": "NORMAL",
      "hurtbox": { "x": 21, "y": 21, "width": 14, "height": 43 } }
  ]
}
```

- Each sheet is **one row of equal frames**, read left to right. A bare file name (`walk.png`) is
  next to the JSON; a path with folders is from the asset root.
- `drawScale` — sprite px → world units. The export uses 0.5: the 64 px frame is 32 units, the body
  ≈ 26.5 units (1.7 tiles), drawn crisp by the 2× camera.
- `footOffset` — transparent sprite pixels below the feet (before scaling); 0 for the generator.
- `colliderWidth` / `colliderHeight` — body box in **sprite pixels** (× `drawScale`), 0 = derived from
  the frame. The map generator assumes a 1 × 2 tile (16 × 32) player; keep the box within that.
- `frames[].duration` (ms) — per-frame timing (played by repeating frames on a 10 ms tick;
  `AnimationComponent.keyFrameSources` maps back to the authored frame). `frames[].attackBox` —
  where the HIT frame reaches (sprite px, top-left origin). `walk.groundSpeed` — stride speed.
  `hurtbox` on CROUCH — the crouched box height.
- Walk / jump / climb speeds are **not** in this file: they come from the map (physics contract above).
- `type` must be an `AnimationType`; `IDLE` is required; missing types fall back to `IDLE`.
  The generator's ATTACK_KNIFE row has `type: "ATTACK"`.
- The player is the map property `playerCharacter`, else `Constants.DEFAULT_PLAYER_CHARACTER`
  (`sprites/players/player_soldier/player_soldier.json`); when that file is missing the map loads
  with no player and a free camera. `playerWeapon` (optional) adds a separate weapon sprite — the
  generator's soldier already has the knife drawn into its frames, so it doesn't need one.
- Animations may also define weapon hand anchors (`hand` / `handFrames`); see [character-weapon-system.md](character-weapon-system.md).
- `weaponLayer: "SEPARATE"` + `handSheet` per animation: the strips are the body without a weapon and
  `<anim>_hand.png` holds only the front hand. The player then holds `Constants.DEFAULT_PLAYER_WEAPON`
  (`sprites/weapons/combat_knife/combat_knife.json`, from `build_asset/build_weapon`) unless the map names
  `playerWeapon`; RenderingSystem draws body → weapon → front hand.

- Animations with `"stance": "PISTOL"` (`IDLE_PISTOL`, `WALK_PISTOL`, `CROUCH_PISTOL`, `JUMP_PISTOL`, `SHOOT_PISTOL`)
  form a second animation set used while the pistol is in hand (types IDLE / WALK / CROUCH / JUMP / SHOOT;
  states the set lacks, like HIT and DEATH, come from the default set). `supportFrames` (back hand) is informational.
- `"stance": "RIFLE"` (`IDLE_RIFLE` … `SHOOT_RIFLE`) is the rifle set the same way.
- With a SEPARATE player the loader also gives the player every `Constants.DEFAULT_PLAYER_ARSENAL` weapon that
  exists (service pistol, assault rifle): knife = slot 1, pistol = slot 2, rifle = slot 3.

### Weapon Data: `assets/sprites/weapons/<id>/<id>.json`

Written by the weapon generator (`build_asset/build_weapon/pixel-weapon-generator`, `node tools/export-game.mjs`):
`id, name, texture` (asset root path), `drawScale, pivot` (grip, weapon px top-left), `rotation, tipAnchor`
(+ the generator's metadata, ignored). `assets/attachments/<character>/<id>.json` (`weaponScale 1`) gives the
weapon the character's pixel size. Firearms add:
- `muzzleAnchor`, `ejectionAnchor`, `supportAnchor` (weapon px), `stance` (`PISTOL` → the player's pistol set);
- `fire`: `mode` (SEMI = one shot per press, AUTO = while held), `roundsPerSecond, damage, magazine, reloadSeconds, projectileSpeedTiles, rangeTiles,
  spreadDegrees, recoilDegrees, shellEject {speedTiles, angleDegrees}` (tiles × the map's tile size);
- `effects`: `muzzleFlash`, `tracer`, `casing`, `impact` — each `{texture, frameWidth, frameHeight, frameCount,
  durations (ms), origin (frame px), loop}`, strips in `sprites/weapons/<id>/fx/`.

### Adding Content
- **New map**: generate it in the map tool and run `node tools/export-game.mjs --seed <n> --name <name>`;
  point `Constants.START_MAP_PATH` at `maps/<name>/map.tmx`.
- **New enemy look**: `node tools/generate.mjs --seed <n> --id <id> --palette <PALETTE>` in the enemy tool,
  then re-run the map export (it picks enemies from the enemy tool's export folder and copies the ones it uses).
- **Add / move an enemy by hand**: open the `.tmx` in Tiled, duplicate an object in the `enemies` layer,
  set `definition` to an enemy JSON under `assets/sprites/enemies/` and adjust `patrolMinX` / `patrolMaxX`.
- **New animation state** (e.g. `SHOOT`): add it to `AnimationType`, choose it in `AnimationSystem.resolveType`, add it to the JSON.

## 1. Input System ✅ Implemented (keyboard)

**Location**: `com.hung.input`

**Purpose**: Translate player/AI input into a unified format

### Input Sources
```
KeyboardController (Player)
   └─ Reads: Keyboard keys, mouse position
   └─ Outputs: Movement vector, aim vector, fire flag
   
AIController (Enemy)
   └─ Reads: Enemy state, player position
   └─ Outputs: Movement vector, aim vector, fire flag
   
GamepadController (Future)
   └─ Reads: Gamepad input
   └─ Outputs: Same as keyboard
```

### Controller Interface (All inputs return this)

Current interface (`com.hung.input.Controller`):

```java
public interface Controller {
    // Each axis -1..1. X = left/right, Y = up/down (reserved for ladders)
    Vector2 getMovementDirection();

    // true only on the frame a jump / attack is requested
    boolean isJumpPressed();
    boolean isAttackPressed();
}
```

Add methods when their feature is built (planned: `getAimDirection()`, `isFirePressed()`, `isAbilityPressed()`).

### Key Bindings
Defined as key arrays at the top of `KeyboardController` (several keys per action):
- Move: `A` / `D` or left / right arrows; `W` / `S` or up / down climb ladders, `S` / down crouches
- Jump: `Space` (`S` + `Space` drops through a one-way platform)
- Attack: `J` or the left mouse button

`CameraInput` (free camera: `F2` in game, or always when the map has no player): `WASD` / arrows pan
(`Shift` faster), `Q` / `E` or mouse wheel zoom, `Home` back to the spawn, `Tab` next enemy.

See [04-GAME-DESIGN.md → Controls](04-GAME-DESIGN.md#controls) for the full table.

### InputSystem
Reads each controlled entity's `Controller` (plus its collider, health and the tile grid) and writes
velocity, facing and movement / attack state:
- walk: `velocity.x = direction.x * moveSpeed`; stops while crouching or swinging on the ground
- jump: `velocity.y = jumpSpeed` when jump was pressed within the last 0.1 s (`jumpBufferTime`) and the
  entity stood on something within the last 0.08 s (`airTime`, coyote time)
- crouch: down on the ground sets `isCrouching` (collider uses `crouchHeight`); stands up only with headroom
- drop through: crouch + jump on a one-way platform moves the feet 2 units below its top
- ladders: up/down at a ladder sets `isClimbing`, snaps x to the ladder column; then up/down moves at
  `climbSpeed`, left/right steps off, jump jumps off, past the top rung → stand on it, floor → let go
- attack: sets `AttackComponent.isRequested` (AttackSystem starts the swing)
- hurt or dead entities get no control (knockback plays out); `setEnabled(false)` (free camera) stops them

## 2. Physics System ✅ Implemented

**Location**: `com.hung.physics`

**Purpose**: Apply gravity and move entities by their velocity using delta time

**Gravity**: every entity with a `MovementComponent` (except while `isClimbing`) gets `velocity.y -= gravity * delta` (capped at
`maxFallSpeed`), with `gravity` from the level's `PhysicsSettings`. Entities with a `ColliderComponent`
are moved by `CollisionSystem.move(entity, dx, dy)`; others just add `velocity * delta`.

### Input
```
For each entity:
├─ Current position (Vector2)
├─ Current velocity (Vector2)
└─ Delta time (float) in seconds
```

### Process
```
position.x += velocity.x * delta
position.y += velocity.y * delta
```

### Output
```
Updated position for each entity
```

**Important**: 
- Always multiply by delta time!
- Position is absolute (world coordinates)
- Velocity is pixels/second

## 3. Collision System ✅ Implemented (tiles)

**Location**: `com.hung.physics`

**Purpose**: Detect collisions, prevent entities from overlapping walls

**Implemented now** — box vs `TileGrid`, one axis at a time (same rules as the map generator's validator):
- horizontal: `SOLID` / `BREAKABLE` tiles stop the box → `MovementComponent.isBlocked`
- rising: `SOLID` tiles bonk the head; `PLATFORM` and ladders are passed through
- falling: lands on `SOLID`, `PLATFORM` or a ladder's top rung, only when the feet cross the tile top
  from above → `MovementComponent.isGrounded`; while climbing, ladder top rungs don't hold the box
- the box is `Hitboxes.body(entity)`: `width` × `height`, or `crouchHeight` while crouching
- outside the map: left/right are walls, above/below is open air

**`HazardSystem`** (after physics, player only): checkpoint objects set `World.respawnPoint`; touching the
lower 80% of a `SPIKE` / `TOXIC` tile or falling 64 units below the map → `PlayerRespawn.respawn`
(no damage yet); reaching the exit is logged once.

**Entity vs entity** (`ContactDamageSystem`): a living patrol enemy touching the player's box deals 10
damage through `Damage.apply`. Enemies don't block movement. Projectile collisions below are planned.

### Input
```
For each entity:
├─ Position (after physics update)
├─ Hitbox (size/shape)
└─ Collision type (Player, Enemy, Projectile, Wall)
```

### Collision Types

| Type | Collides With | Result |
|------|---------------|--------|
| Player | Walls | Stop movement (can't overlap) |
| Player | Enemies | Damage to player |
| Enemy | Walls | Stop movement |
| Enemy | Other enemies | Stop movement |
| Projectile | Walls | Projectile dies |
| Projectile | Enemies | Damage to enemy, projectile dies |
| Projectile | Player | (Friendly fire off, no collision) |

## 4. Combat System ✅ Melee + firearms implemented

**Location**: `com.hung.combat`

**Purpose**: Handle weapons, projectiles, damage

**Implemented now — melee driven by animation data:**
- `AttackComponent`: `damage`, `isRequested` (input), `isAttacking`, `hasHit` (one landing per swing).
- `AttackSystem`: a request starts the attack (if alive and not hurt); `AnimationSystem` then plays
  ATTACK. On every authored frame with an `attackBox` the box (`Hitboxes.fromSprite`, mirrored when
  facing left) damages each entity on the other side (player ↔ non-player) whose body it overlaps,
  and clears `BREAKABLE` tiles from the grid and the map's `terrain` layer. The attack ends when the
  ATTACK animation finishes.
- `Damage.apply(target, amount, sourceX)`: lowers health, starts the hurt reaction (HIT length) or
  death, gives the target's `invulnerability` seconds of invulnerability, knocks it back away from
  `sourceX`, cancels its attack / climb / crouch.
- Numbers are placeholders in `EntityFactory` / `ContactDamageSystem` until definitions carry stats:
  knife 10, player 100 HP + 1 s invulnerability, enemies 30 HP, enemy touch 10. Firearm numbers come
  from the weapon JSON (`fire`).

**Implemented now — weapons in hand and firearms:**
- `ArsenalComponent`: the carried weapons (one `WeaponComponent` each, so a magazine survives switching)
  and the slot in hand; input asks for a slot (1 / 2) or the next one (Q).
- `WeaponSwitchSystem`: switches `Entity.weapon` to the slot and the animation set to the weapon's stance
  (`AnimationFrames.useStance`); not while swinging / hurt / dead; cancels a reload.
- `WeaponComponent` firearm state: `ammo, cooldown, reloadTime, shootTime, isFireRequested, isReloadRequested`.
  InputSystem asks to fire on a press, and every frame the button is held for AUTO weapons (`Controller.isAttackHeld`);
  the cooldown (1 / roundsPerSecond) paces full-auto.
- `ShootingSystem`: trigger → shot when the cooldown is over and there are rounds (empty → reload);
  spawns the projectile at `Hitboxes.weaponPoint(muzzleAnchor)` along `Hitboxes.weaponAngle` ± spread,
  a muzzle flash (`Effects.spawn`) and a casing (sprite + movement + 1×1 collider: gravity and tiles);
  `shootTime` keeps the SHOOT pose for one SHOOT animation.
- `ProjectileSystem`: moves bullets in ≤ 2-unit steps; a SOLID / BREAKABLE tile or the first living body
  of the other side stops them (`Damage.apply`, impact effect); range runs out → removed.
  PhysicsSystem skips projectiles.
- `EffectSystem` (`com.hung.animation`): advances `SpriteComponent` animations, counts `EffectComponent`
  lifetimes down and removes finished effects; landed casings stop sliding / spinning.
- `SpriteComponent`: an animation placed by an origin pixel (rotation / flip around it) — effects,
  tracers, casings; `RenderingSystem` draws them after the characters.

### Weapon Component

**Stores**:
```
├─ Current weapon
├─ Fire rate cooldown (timer)
├─ Ammunition count
└─ Damage per hit
```

### Projectile Entity

**Stores**:
```
├─ Position
├─ Velocity (direction * speed)
├─ Damage
├─ Owner (player or enemy)
└─ Lifetime (despawn if too old)
```

## 5. Health & Damage System

**Location**: `com.hung.component` + `com.hung.combat`

**Purpose**: Track entity health, handle damage

### HealthComponent ✅

```
├─ health / maxHealth (int)
├─ hurtTime          seconds left of the HIT reaction (no control meanwhile)
├─ invulnerability   seconds granted per hit; invulnerableTime = seconds left (sprite blinks)
└─ isDead + deadTime
```

**`HealthSystem`** (after attacks): counts the timers down and slows knockback (400 u/s²). A dead enemy
plays DEATH, stays 1.5 s, then `World.removeEntity`. A dead player plays DEATH, waits 1 s, then
respawns at `World.respawnPoint` with full health (`PlayerRespawn`, 1.5 s invulnerable).
`PatrolSystem` leaves hurt / dead enemies alone.

## 6. AI System ✅ Patrol + hunting (trial) implemented

**Location**: `com.hung.ai`

**Purpose**: Make enemies behave intelligently

**Implemented now — `PatrolSystem`**: every entity with a `PatrolComponent` (enemies from the map) walks
at `moveSpeed` between `minX` and `maxX` and turns around at the range ends, when blocked by a wall, or
before stepping off a ledge. The range comes from the map's enemy object (`patrolMinX` / `patrolMaxX`).

**Implemented (trial, 2026-10-06) — `EnemyBrainSystem`**: enemies with an `EnemyBrainComponent` (the enemy
generator's phase 2 archetypes, built by `entity/EnemyFactory`) hunt the player. It runs after PatrolSystem and
overrides it while hunting: player within `aggroRange` (and near the enemy's floor) → face, chase at
`moveSpeed × chaseSpeedScale` (ground enemies stop at ledges and walls; they don't jump), inside `attackRange`
(80% of the attack reach measured by the generator + ~half the player's width) and off `attackCooldown` → stand
and attack. The attack animation's per-frame hitboxes (from `character.json`) do the damage through AttackSystem,
like the player's knife. The MINI_BOSS alternates ATTACK (smash) and ATTACK_2 (claw sweep).
Flyers (`MovementComponent.isFlying`, no gravity) patrol at a cruise height (40 units over their floor), hunt just
above the player's head and sink to the player's body during the dive; after DEATH the wreck drops to the floor.

| Archetype | HP (durabilityClass) | Attack damage (damageClass) | Aggro | Cooldown | Chase |
|---|---|---|---|---|---|
| SMALL_MELEE (phase 1) | 30 | contact only (10) | — | — | patrol only |
| CRAWLER | 20 (LOW) | 8 (LOW) | 160 | 0.9 s | ×1.6 |
| FLYING | 20 (LOW) | 12 (MEDIUM) | 180 | 1.4 s | ×1.4 |
| MINI_BOSS | 300 (HIGH), 85% knockback resistance | 25 (HIGH) | 220 | 1.6 s | ×1.2 |

The numbers live in `EnemyFactory` (the enemy files only carry the classes). Touching any enemy still deals
`ContactDamageSystem.ENEMY_CONTACT_DAMAGE`.

**Planned** — the full state machine below (flee, ranged, groups).

### Enemy State Machine

```
State Machine:

IDLE (default)
  └─ If player visible AND in range
     └─ Change to CHASE
     
CHASE
  └─ Move toward player
  └─ If player not visible
     └─ Change to IDLE
  └─ If player close (attack range)
     └─ Change to ATTACK
     
ATTACK
  └─ Shoot at player
  └─ If player far away
     └─ Change to CHASE
  └─ If low health
     └─ Change to FLEE
     
FLEE
  └─ Move away from player
  └─ If far enough
     └─ Change to IDLE
```

## 7. Animation System ✅ Implemented (IDLE, WALK, JUMP, ATTACK, HIT, DEATH, CROUCH)

**Location**: `com.hung.animation`

**Purpose**: Display animated sprites based on state

Uses LibGDX's built-in `com.badlogic.gdx.graphics.g2d.Animation<TextureRegion>` (no custom Animation class).

### Animation Component

**Stores**:
```
├─ EnumMap<AnimationType, Animation<TextureRegion>>
│   ├─ IDLE, WALK, JUMP, ATTACK, HIT, DEATH, CROUCH   ✅
│   └─ RUN, CLIMB, SHOOT (planned)
├─ keyFrameSources   key frame → authored frame (per-frame durations)
├─ attackBoxes       attack box per authored frame (ATTACK)
├─ handAnchors       weapon hand per frame (optional)
├─ currentType + stateTime (seconds since it started)
├─ drawScale + footOffset (from character JSON)
└─ strideSpeed       world u/s at which WALK feet don't slide
```

### Update Process

```
Each frame (AnimationSystem.update):
1. Pick the state (first match):
   dead                   → DEATH
   hurtTime > 0           → HIT
   attacking              → ATTACK
   climbing               → IDLE (until a CLIMB animation exists)
   crouching              → CROUCH
   not grounded           → JUMP
   |velocity.x| > 1 u/s   → WALK
   otherwise              → IDLE
   (missing in the data   → IDLE)
2. State changed? → currentType = new state, stateTime = 0
   Same state?    → stateTime += delta × rate   (WALK: |vx| / strideSpeed, clamped 0.5–2.5)
3. JUMP: stateTime follows velocity.y (launch at +jumpSpeed → fall at −jumpSpeed)
4. RenderingSystem draws animation.getKeyFrame(stateTime)
```

## 8. Rendering System ✅ Implemented

**Location**: `com.hung.rendering`

**Purpose**: Draw all entities to screen

- `RenderingSystem` — the map's tile layers through LibGDX's `OrthogonalTiledMapRenderer` (only what the
  camera sees), with the entities drawn between `props` and `decor_front`. Sprites face right; facing left
  draws them mirrored.
- `CameraController` — `ExtendViewport` (at least `640x360` world units visible). Follows the player's feet
  + 24 units with a critically damped spring per axis (`util/SmoothDamp`, 0.18 s), and is a
  `RoomTransitionEvent` listener: it stays inside the current room's camera bounds and cuts to the new room
  (behind the door fade). Gameplay zoom 0.5–1 (default 0.8, `-`/`=`, wheel, `0` resets) is eased and capped
  so the view is never bigger than the room. Without rooms it clamps to the map. The free camera
  (`CameraInput`, F2) ignores rooms; zoom 0.25–6.
- `FadeRenderer` — the black overlay of a room transition (opacity = `RoomTransition.fade`).
- `DebugRenderer` — `F3` toggles an overlay: collision tiles (red solid edges, cyan one-way tops, green
  ladders, magenta hazards, gold breakable walls), entity boxes (yellow, crouch box while crouching),
  attack boxes (red, during the HIT frame), patrol ranges (orange), checkpoints (sky blue), spawn and exit.
- `HudRenderer` — screen-pixel camera: player health bar + numbers top-left, weapon + ammo, controls hint bottom-left.
- `ui/MinimapRenderer` — exploration minimap top-right (2 px per tile, scrolls with the player; `M` shows the whole
  map in the middle). Draws `ui/MinimapState`: visited rooms filled (current room green, landmarks blue, side rooms
  gold, doorways marked), rooms one door away as dark outlines, the rest hidden, and a blinking player icon.
- `LightingRenderer` — after the scene: a ¼-resolution light map (FrameBuffer cleared to the ambient colour, every
  visible `Level.getLights()` light, the player light and `EffectComponent.lightRadius` effects drawn additively with a
  radial falloff texture) multiplied onto the screen (`GL_DST_COLOR, GL_ZERO`), then an additive colour glow. Lights
  come from the map's `lights` objects (`LightSource`: kind, radius px, color, intensity, flicker, phase) read by
  WorldLoader; the flicker formulas match the map generator. `L` toggles it.
- `StatsPanel` — the character sheet (`I` / `F1`, GameScreen pauses the systems): player health, movement in tiles
  (walk / jump height / jump distance / air time / climb, computed from the physics), body box, defence, position,
  respawn, stance; one card per arsenal slot with the weapon sprite ×4, damage, DPS, range / reach, magazine, fire
  mode, rate, reload, bullet speed, spread, and comparison bars; the weapon in hand is highlighted.
- An invulnerable entity blinks (hidden every other 0.08 s).

### Rendering Order (Back to Front)

```
1. Map: walls → decor_back → terrain → props            ✅
2. Entities (enemies, then the player)                    ✅
3. Map: decor_front (pipes in front of the action)        ✅
4. Projectiles / effects                                  (planned)
5. Lighting from the map's `lights` objects               (planned)
6. HUD/UI (always on top)                                 ✅ health bar + hints
```

## 9. Audio System

**Location**: `com.hung.audio`

**Purpose**: Play sounds and music

### Sound Manager

**Stores**:
```
├─ Loaded sounds (cached)
│   ├─ "gun_shot"
│   ├─ "impact"
│   ├─ "enemy_death"
│   ├─ "pickup"
│   └─ ... more
└─ Loaded music tracks
```

## 10. Item & Pickup System

**Location**: `com.hung.item`

**Purpose**: Dropped items that player can collect

### Item Types

```
Abstract Item class:
├─ HealthPack: +50 HP
├─ WeaponCrate: Swap weapon
├─ SpeedBoost: +50% speed (5 sec)
├─ Shield: Block 1 hit (3 sec)
└─ AmmoPack: Refill ammo
```

## 11. World System ✅ Implemented

**Location**: `com.hung.world`

**Purpose**: Container for level and entities (no logic)

### World Holds

```
├─ Level (Tiled map + collision grid + physics + spawn/exit + checkpoints)   ✅
├─ Entities (Array<Entity>; removeEntity for the dead)   ✅
├─ Respawn point (spawn, then the last checkpoint)   ✅
├─ RoomTransition (current room, door fade phase)   ✅
├─ Rooms (Level.getRooms(): camera bounds, neighbours, doorways)   ✅
├─ Door entities (Transform + DoorComponent)   ✅
├─ Player entity (also in entities)             ✅
├─ Enemy entities (patrolling, from the map)   ✅
├─ Projectile / Item entities                  (planned, same entities array)
└─ Game state                                   (planned)
```

`WorldLoader.load(mapPath, resources)` loads the `.tmx` (and its tilesets), builds the collision grid from
the `terrain` layer, reads spawn/exit, the `enemies` layer and the CHECKPOINT objects of `pois`, loads
every enemy and player definition and texture up front, then builds the `Level`, the enemy entities,
the player (map property `playerCharacter`, else `Constants.DEFAULT_PLAYER_CHARACTER`) and the `World`.
`RoomLoader` adds the rooms (`rooms` layer) and one door entity per `doors` object (target room's camera
bounds copied into the `DoorComponent`; spawnY flipped to y up).

### Rooms & doors (event-driven) ✅

```
build_map (DoorPlacer)            map.tmx                      game
every carved link ──► doors[] ──► "rooms" + "doors" layers ──► RoomLoader ──► Room[] + door entities
                                                                    │
DoorSystem: player touches a door of the current room               ▼
  → fade out (world paused) → player moved to door.spawn ──► EventBus.publish(RoomTransitionEvent)
  → fade in                                                       │                │
                                                CameraController ◄┘                └► MinimapState
                                                (clamp to new bounds, cut)            (reveal, move icon)
```

- `core/EventBus` + `EventListener<T>`: observer hub. Publishers fill a reused event object; listeners copy what
  they keep. Copy-on-write listener lists, so publishing never allocates and (un)subscribing inside a listener is safe.
- `world/RoomTransitionEvent`: `reason` (`LEVEL_START`, `DOOR`, `RELOCATED`), `previousRoom`, `room`,
  `cameraBounds`, `spawn` (player feet), `doorId`.
- `world/DoorSystem`: a door only works from its own room (`DoorComponent.roomId`), so the two doors of a doorway
  never fight. Velocity is kept through a door: a fall or a ladder climb simply continues. Every arrival point is
  outside the target room's door triggers (checked by the map tool), so there is no ping-pong. When the player
  leaves the current room's bounds without a door (respawn at a checkpoint), it publishes `RELOCATED`.
- Door kinds: `SIDE` doorway (both ways), `LADDER` shaft (both ways, arrival on the ladder), `DROP` hole (one-way down).
- Adding a listener (e.g. room music, enemy wake-up): `events.subscribe(RoomTransitionEvent.class, listener)` in
  `GameScreen`; nothing in the door code changes.

### Entities
`Entity` is a component container: a `TransformComponent` always, plus optional
`MovementComponent`, `ColliderComponent`, `AnimationComponent`, `WeaponComponent`, `PatrolComponent`,
`AttackComponent`, `HealthComponent` and `Controller` (null when absent — systems skip it).
Build entities with `EntityFactory`, never with subclasses.

## 12. Game Loop Integration ✅ Implemented

`MyGame` (`com.hung.core`) creates the `SpriteBatch` and `ResourceLoader`, loads the start level,
and shows `GameScreen`. `GameScreen` owns the systems and runs them in order:

```java
// GameScreen.render(delta)
update(Math.min(delta, Constants.MAX_FRAME_TIME));  // clamp spikes (window drag, breakpoints)

player != null && !isCameraFree ? cameraController.follow(player feet, level, delta)   // F2 / no player:
                                 : cameraInput.update(delta, cameraController, world);
ScreenUtils.clear(BACKGROUND_COLOR);
renderingSystem.render(batch, camera);   // tile layers + entities (manages batch begin/end)
lightingRenderer.render(...);            // light map over the scene (L)
if (isDebugVisible) debugRenderer.render(camera, world);
fadeRenderer.render(world.getRoomTransition());   // door fade
hudRenderer.render(batch, world, isCameraFree);
minimap.render(batch, player feet, time);

// GameScreen.render: update(delta) is skipped while a door fades out (RoomTransition.Phase.FADING_OUT);
// doorSystem.update(delta) runs every frame after it: doors → RoomTransitionEvent → camera + minimap

// GameScreen.update(delta)
inputSystem.update(delta);       // Controller → walk, jump, crouch, ladders, attack / trigger / reload / weapon requests
weaponSwitchSystem.update();     // arsenal slot → weapon in hand + animation stance
patrolSystem.update();           // enemies: velocity.x along their patrol
physicsSystem.update(delta);     // gravity (not on ladders), then CollisionSystem (tile collision)
hazardSystem.update();           // checkpoints, spikes / toxic / fall-out → respawn, exit
contactDamageSystem.update();    // enemy touches player → Damage
attackSystem.update();           // start swings, HIT-frame box → Damage + breakable tiles
shootingSystem.update(delta);    // cooldown / reload, shots → projectile + muzzle flash + casing
projectileSystem.update(delta);  // bullets: walls, bodies (Damage), range; impact effects
healthSystem.update(delta);      // hurt / death timers, remove dead enemies, respawn the player
animationSystem.update(delta);
effectSystem.update(delta);      // effect / bullet / casing sprites, remove finished effects
```

Planned systems slot into this order: more AI next to patrol, enemy weapons with combat, audio before render.
