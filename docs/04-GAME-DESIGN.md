# 04 - Game Design

[← Index](README.md)

## Game Overview

**Title**: Maze Shooter
**Genre**: 2D Side-View Platformer Shooter (underground bunker / dungeon)
**Core Loop**: Explore → Survive → Defeat → Upgrade → Next Level

Player explores procedurally generated underground levels (map generator in `build_asset/build_map`),
runs, jumps and climbs through multi-level rooms, fights enemies (enemy generator in `build_asset/build_enemy`),
collects items, upgrades weapons and faces increasingly difficult bosses.

## Core Gameplay Loop

```
1. SPAWN
   └─ Player starts in maze
   
2. EXPLORE
   └─ Run, jump and climb through the level
   └─ Tile collision: walls, floors, one-way platforms, ladders
   
3. COMBAT
   └─ Encounter enemies
   └─ Aim with mouse
   └─ Shoot with left-click
   └─ Dodge enemy fire
   
4. SURVIVE
   └─ Manage health
   └─ Pick up items
   └─ Escape to exit
   
5. PROGRESS
   └─ Defeat enough enemies
   └─ Reach exit = win level
   └─ Go to next level (harder)
```

**One play session**: 30+ minutes playing multiple levels

## Camera & Perspective

The game is a **plain 2D side view** (platformer): x is left/right, y is up/down, gravity pulls down.
There is no floor depth.
- Levels are tile maps (16 × 16 px tiles) exported from the map generator as Tiled `.tmx` files.
- Characters stand on tiles; ceilings, walls, one-way platforms and ladders shape the route.
- The level is a chain of **rooms**. The camera eases after the player (looking slightly above the feet)
  and never shows past the walls of the current room; it cuts to the next room when the player goes
  through a door. `-` / `=` (or the mouse wheel) zoom in / out, `0` resets; the view is never allowed to
  get bigger than the room.
- `F2` switches to the free camera (pan / zoom around the map) and back; the player stands still
  meanwhile. Without a player character the camera is always free.
- The player sprite is drawn at half its pixel size (`drawScale` 0.5): the 64 px frame becomes
  32 world units and the body ~26.5 units (≈ 1.7 tiles), crisp at the 2× camera.

## Lighting

The bunker is dark: the scene is multiplied by a light map (ambient ≈ 30 % brightness, slightly blue) in which
every light of the map's `lights` layer is added — 145 warm lamps, cold monitors / electrics, fires, toxic tanks —
with the generator's flicker (soft / pulse / strong stutter), plus a soft colour glow. The player carries a dim light
(3.5 tiles) so they never vanish; muzzle flashes and impact sparks light up briefly. `L` turns it off (map preview look).

## Rooms, doors & minimap

- Every doorway, ladder shaft and drop hole between two rooms is a **door**. Walking into a doorway, climbing
  through a shaft or falling down a hole fades the screen to black for a moment (the world pauses), puts the
  player just inside the next room and fades back in. Ladder climbs and falls carry on through the door.
- Drop holes are one-way; doorways and ladders work both ways.
- Respawning at a checkpoint in another room moves the camera there at once.
- **Minimap** (top-right): rooms you have entered are drawn (current room green, set pieces blue, side rooms
  gold, doorways as small marks); rooms behind a door of an explored room show as dark outlines; the rest
  stays hidden. The blinking red mark is you. `M` opens the whole map in the middle of the screen.

## Controls

| Action | Keys | Status |
|--------|------|--------|
| Move left / right | `A` / `D` or `←` / `→` | ✅ Implemented |
| Jump | `Space` | ✅ Implemented |
| Climb ladders (grab, climb, top rung) | `W` / `S` or `↑` / `↓` | ✅ Implemented |
| Crouch | `S` / `↓` on the ground | ✅ Implemented |
| Drop through a one-way platform | `S` + `Space` | ✅ Implemented |
| Knife attack / shoot (with the pistol in hand) | `J` or left click | ✅ Implemented |
| Pick weapon: knife / pistol / assault rifle, next weapon | `1` / `2` / `3`, `Q` | ✅ Implemented |
| Full-auto (rifle) | hold `J` / left button | ✅ Implemented |
| Reload | `R` (automatic when the magazine is empty) | ✅ Implemented |
| Free camera on / off | `F2` | ✅ Implemented |
| Character sheet (player + weapon stats; pauses, `1`/`2`/`3` pick a weapon) | `I` or `F1` | ✅ Implemented |
| Lighting on / off | `L` | ✅ Implemented |
| Whole map on / off | `M` | ✅ Implemented |
| Camera zoom in / out, reset | `=` / `-` or mouse wheel, `0` | ✅ Implemented |
| Toggle debug overlay (collision tiles, boxes, attack boxes, patrols, checkpoints, room bounds, doors) | `F3` | ✅ Dev tool |
| Aim | Mouse | Planned |
| Free aim (shoot at the mouse) | Mouse | Planned — shots follow the weapon (level, ±1.5° spread) |

**Free camera (`F2`, or no player in the map):**

| Action | Keys |
|--------|------|
| Pan | `W` `A` `S` `D` or arrows (`Shift` = faster) |
| Zoom in / out | `Q` / `E` or mouse wheel |
| Back to the player spawn | `Home` |
| Jump to the next enemy | `Tab` |

Key bindings live in one place per controller: the key arrays at the top of
`com.hung.input.KeyboardController` (player) and `com.hung.input.CameraInput` (free camera).

> **Windows + Vietnamese keyboard:** when the Windows Vietnamese (Telex) input method is active,
> the letter keys (WASD) are consumed by the IME and never reach the game. Switch to ENG
> (`Win + Space`) or use the arrow keys.

## Core Mechanics

### 1. Movement
- **Input**: A/D or left/right arrows
- **Speed**: from the map's physics contract — default 6 tiles/s (96 px/s with 16 px tiles)
- **Collision**: tile box collision: SOLID walls/floors/ceilings, one-way PLATFORMs and ladder tops
  (land on them from above, jump through from below); enemies don't block, they hurt on touch
- **Facing**: Sprite faces the last horizontal direction (mirrored when moving left)
- **Box**: the character JSON's `colliderWidth` × `colliderHeight` (sprite px) × `drawScale` —
  7 × 26.5 world units for the soldier; 21.5 high while crouching (the CROUCH pose's `hurtbox`)
- **Crouch**: `S` on the ground — stops walking, lowers the box; stands up only when there is room
- **Animation**: WALK is authored for 15 world units/s but the player walks at 96, so WALK plays
  faster (capped at 2.5×) until the character generator adds a RUN animation

### 2. Jumping
- **Input**: Space while standing on something; also within 0.08 s after walking off a ledge
  (coyote time), and a press up to 0.1 s before landing still jumps (jump buffer)
- **Height / distance**: from the map — default 3.5 tiles high, 6 tiles far, 1 second in the air.
  The map generator only builds gaps and ledges reachable with 80% of these numbers, so the game
  must use the same ones (they are read from the map file; see 05-SYSTEMS-GUIDE §2)
- **Air control**: Left/right still works in the air
- **Animation**: JUMP frames follow the vertical speed (launch → tuck → apex → fall), so short
  hops and long falls both look right

### 2b. Ladders, hazards, checkpoints
- **Ladders**: up/down grabs a ladder in front of the body (or the top rung under the feet) and
  snaps to its column; up/down climbs at 4 tiles/s without gravity; left/right steps off; Space
  jumps off; climbing past the top rung stands on it; reaching the floor lets go.
  No CLIMB animation yet — IDLE plays while climbing
- **Spikes / toxic sludge**: touching one sends the player back to the last checkpoint (no damage
  yet — trap damage tiers are planned, see build_asset/build_map/PROGRESS.md §6). Falling out of
  the map does the same
- **Checkpoints**: the map's `pois` objects of type CHECKPOINT; touching one makes it the respawn point
- **Breakable walls** (BREAKABLE tiles): the knife's attack box destroys them (grid + drawn tile)
- **Exit**: reaching it is logged; level transitions are planned

### 3. Aiming
- **Input**: Mouse position
- **Output**: Aim direction
- **Feedback**: Side view — the sprite only faces left/right (mirrored), so aiming turns the weapon/projectile direction, not the sprite. Exact rules to be decided with combat.
- **Range**: 360 degrees

### 4. Shooting (implemented: service pistol, assault rifle)
- **Weapons carried**: knife (`1`), service pistol (`2`) and assault rifle (`3`); `Q` cycles. Switching changes
  the player's animation set: with the pistol the soldier holds it with **both hands** (`*_PISTOL` animations
  from the character generator — idle, walk, crouch and jump all keep the gun aimed); with the rifle the stock is
  at the shoulder, the front hand on the handguard and the back hand on the pistol grip (`*_RIFLE`) — not during
  a swing or a hurt reaction; a reload in progress is cancelled
- **Rifle data**: full-auto (hold the trigger), 9 rounds/s, 11 damage (3 rounds per enemy), 30-round magazine,
  1.8 s reload, bullets 40 tiles/s, range 32 tiles, ±2.5° spread; the SHOOT_RIFLE pose rocks the body back, the
  rifle stays level; a longer flash / tracer / casing than the pistol
- **Input**: `J` / left click pulls the trigger (semi-automatic: one shot per press); `R` reloads
- **Pistol data** (weapon JSON `fire`, from the weapon generator): 12 damage (enemies 30 HP → 3 shots),
  12-round magazine, 1.1 s reload (automatic when empty), max 5 shots/s, bullets 30 tiles/s, range
  22 tiles, ±1.5° spread
- **Shot**: the bullet leaves the muzzle (the pistol's muzzle anchor where it is drawn, so it follows the
  pose), with a muzzle flash, a brass casing flying back from the ejection port, and the SHOOT pose
  (muzzle kicks up 15°) when standing still; walking / jumping / crouching keep their stance animation
- **Projectile**: tested every 2 world units — stops at the first wall / breakable tile (one-way platforms
  and ladders let it through) with an impact spark, or at the first enemy body (damage + knockback); it
  never hits the shooter; it vanishes after its range
- **HUD**: weapon name and rounds (`Service Pistol 9 / 12`, `RELOADING`) under the health bar

### 4b. Knife (implemented placeholder)
- **Asset**: a separate sprite from the weapon generator (`build_asset/build_weapon`, combat knife 24×9 px),
  held at each frame's hand anchor; the player strips carry no weapon (see character-weapon-system.md)
- **Input**: `J` or left click; can't attack while climbing; on the ground the player stops for the swing
- **Hit**: the ATTACK animation's HIT frame carries an `attackBox` (sprite px); for that frame the
  box (mirrored when facing left, stretched to the knife's tip) damages every enemy it touches, once per
  swing — 10 damage
- **Enemies**: 30 HP (3 hits); a hit plays HIT, knocks back a little; at 0 HP DEATH plays, the
  body stays 1.5 s, then it is removed
- **Contact damage**: touching a living enemy costs the player 10 HP, knocks them back, plays HIT
  and gives 1 s of invulnerability (the sprite blinks). Enemies have no attack AI yet

### 5. Health & Damage
- **Current build**: player 100 HP (HUD bar top-left); at 0 HP DEATH plays, then the player
  respawns at the last checkpoint with full health (Game Over comes later)
- **Player Health**: 100 HP max
- **Damage Types**:
  - Projectile hit: 10-30 HP
  - Enemy melee: 5-15 HP
  - Boss attack: 20-50 HP
- **Healing**: Health packs restore 50 HP
- **Death**: HP ≤ 0 → Game Over

### 6. Items & Pickups
Items spawn randomly or on enemy defeat:
- **Health Pack**: +50 HP (max health)
- **Weapon Crate**: Swap to new weapon
- **Speed Boost**: +50% movement speed for 5 seconds
- **Shield**: Absorb 1 hit without damage (3 seconds)
- **Ammo Pack**: Refill ammunition

## Weapon System

### Weapon Types

**1. Basic Gun (Starting)**
- Damage: 10 HP
- Fire Rate: 0.3 sec (3.3 shots/sec)
- Projectile Speed: 500 px/s
- Magazine: Unlimited ammo
- Best for: Beginning players, learning

**2. Assault Rifle**
- Damage: 15 HP
- Fire Rate: 0.1 sec (10 shots/sec)
- Projectile Speed: 600 px/s
- Magazine: 30 rounds
- Best for: Sustained damage, swarms

**3. Shotgun**
- Damage: 30 HP (per pellet)
- Pellets: 8 per shot
- Fire Rate: 0.8 sec (1.25 shots/sec)
- Projectile Speed: 400 px/s (slower spread)
- Magazine: 8 rounds
- Best for: Close range, boss fights

**4. Sniper Rifle** (Unlockable)
- Damage: 50 HP
- Fire Rate: 1.0 sec (1 shot/sec)
- Projectile Speed: 800 px/s (fastest)
- Magazine: 5 rounds
- Best for: Precision, one-shot kills

**5. Minigun** (Unlockable)
- Damage: 8 HP (very fast)
- Fire Rate: 0.05 sec (20 shots/sec)
- Projectile Speed: 550 px/s
- Magazine: 100 rounds
- Best for: Rapid-fire, sustained barrages

### Weapon Mechanics
- **Switch**: E key changes weapon
- **Ammo**: Depletes as you shoot, refills from packs
- **Cooldown**: Can't fire until cooldown expires
- **Accuracy**: 100% (no spread except shotgun)
- **Upgrade**: Each weapon level increases damage by 20%

## Enemy System

### Enemy Types

#### 1. Chaser (Basic)
- **HP**: 30
- **Damage**: 5 HP per hit
- **Speed**: 150 px/s
- **AI**: Chase player when visible, melee attack in range
- **Weapon**: Fists (no projectiles)
- **Behavior**:
  - Idle when far away
  - Chase when player spotted
  - Melee attack from close range
  - Flee if low health
- **Reward**: 10 points, drops items 30% chance

#### 2. Turret (Ranged)
- **HP**: 50
- **Damage**: 10 HP per shot
- **Speed**: 0 (stationary, can rotate)
- **AI**: Fire at player if visible
- **Weapon**: Basic Gun
- **Behavior**:
  - Stays in place (spawned position)
  - Rotates to track player
  - Shoots when player in view
  - Wide field of view (300 px)
- **Reward**: 20 points, drops items 50% chance

#### 3. Ranged (Intermediate)
- **HP**: 40
- **Damage**: 8 HP per shot
- **Speed**: 120 px/s (slower than player)
- **AI**: Keep distance, shoot
- **Weapon**: Assault Rifle
- **Behavior**:
  - Chase player to medium range
  - Maintain distance (200 px away)
  - Retreat if approached
  - Shoot while moving
- **Reward**: 15 points, drops items 40% chance

#### 4. Heavy (Tank)
- **HP**: 100
- **Damage**: 15 HP melee, 12 HP ranged
- **Speed**: 80 px/s (slowest)
- **AI**: Slow but powerful
- **Weapon**: Shotgun
- **Behavior**:
  - Slow relentless advance
  - Charge attack at close range
  - Can't strafe (moves forward only)
  - High damage output
- **Reward**: 30 points, drops items 60% chance

#### 5. Boss (Encounter)
- **HP**: 300-500 (scales with level)
- **Damage**: 20-40 HP (multiple attacks)
- **Speed**: 100 px/s
- **AI**: Multi-phase behavior
- **Weapon**: Multiple (switches weapons)
- **Behavior**:
  - Phase 1 (100% HP): Aggressive chase + projectiles
  - Phase 2 (50% HP): Summons minions, grenade attacks
  - Phase 3 (20% HP): Berserker mode - rapid fire
  - Special attacks: Circle strafe, charge, area damage
- **Reward**: 100 points, guaranteed weapon drop

### Enemy Spawning
- **Now (✅ implemented)**: enemies are placed **in the map file**. The map generator puts spawn points
  on flat, hazard-free floor stretches (never next to the player spawn or a checkpoint), each with a
  patrol range; the game export picks a concrete enemy (look + animations from the enemy generator)
  that fits the room (lab → `LAB_MUTANT`, prison → `PRISON_SHADE`, toxic → `TOXIC_GRUB`,
  machinery → `RUST_CRAWLER`, otherwise `BUNKER_GHOUL` / `STEEL_GRAY`). Enemies can also be added
  or moved by hand in Tiled (`enemies` object layer). In game they patrol back and forth
  (turning at the range ends, walls and ledges). See 05-SYSTEMS-GUIDE §6.
- **Density**: map generator setting *Enemy density* (default 50%), scaled up by region difficulty
- **Trial (2026-10-06, ✅)**: the enemy generator's phase 2 archetypes — CRAWLER (small, fast, low),
  FLYING (drone / mutant wasp that dives from above) and MINI_BOSS (Juggernaut: overhead smash + claw sweep,
  300 HP) — are placed by `world/TrialEnemies` in the rooms right after the start room (room_2: crawler,
  room_3: drone, room_4: wasp, room_6: boss on the current map) until the map generator places them itself.
  They notice the player, chase and attack (`EnemyBrainSystem`, 05-SYSTEMS-GUIDE §6). Drawn at the player's
  pixel density (drawScale 0.5). Turn the trial off by emptying `Constants.TRIAL_ENEMIES`.
- **Planned**: map-generator placement of the new archetypes (crawlers in low passages, flyers in tall rooms,
  the mini-boss in the arena), ranged enemies, timed respawns
- **Difficulty**: Increases with level
  - Level 1: Mostly Chasers, 1 Turret
  - Level 5: Mix of all types, 2 Turrets, Ranged enemies
  - Level 10+: Heavy units, multiple Ranged, Boss fights

## Level & Progression

### Level Structure

**Level = generated underground map + enemies + exit arena (future boss)**

Each level is a bunker map from the map generator: 800–1600 × 80–120 tiles, rooms on several floors joined
by doors, drop shafts and ladders, set pieces (labs, prison corridors, toxic bridges, machinery rooms…),
side branches with chests, secret rooms, checkpoints, and an arena with the EXIT at the far end.

```
Level Progression (planned)
├─ Level 1 (Tutorial)      easy difficulty, low enemy density, small melee enemies
├─ Level 5 (Intermediate)  normal difficulty, mixed enemies, mini-boss in the exit arena
└─ Level 10+ (Expert)      hard difficulty, all enemy types, multi-phase boss
```

## Win & Lose Conditions

### WIN
```
Player reaches EXIT DOOR
└─ All levels beat = Final Victory
└─ Or escape current level alive
```

### LOSE
```
Player Health ≤ 0
└─ Game Over screen
└─ Show: Score, Enemies killed, Distance traveled, Level reached
```
