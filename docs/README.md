# Java 2D My Game - Documentation Index

**Project**: My Game  
**Technology**: Java 21 LTS + LibGDX  

## Run the Game

```
gradlew lwjgl3:run
```

Requires `JAVA_HOME` to point to a JDK 21.

The game is a **2D side-view platformer**. It loads the bunker map in `assets/maps/` (made by the map generator)
with its enemies (made by the enemy generator) and the player soldier (made by the character generator):

| Action | Keys |
|--------|------|
| Walk | `A` / `D` or arrows |
| Jump (`S` + `Space` drops through a one-way platform) | `Space` |
| Climb ladders / crouch | `W` / `S` or arrows |
| Knife attack | `J` or left click |
| Free camera on / off (then `WASD` pan, `Q`/`E` zoom, `Tab` next enemy, `Home` spawn) | `F2` |
| Debug overlay (collision, boxes, attack boxes, patrols, checkpoints) | `F3` |

Asset tools (run outside the game): `build_asset/build_map`, `build_asset/build_enemy`,
`build_asset/build_character` and `build_asset/build_weapon` — each has a `PROGRESS.md`.
Re-export a map into the game: `node tools/export-game.mjs --seed <n> --name <name>` in
`build_asset/build_map/platformer-map-generator`; the player: `node tools/export-game.mjs --id <id>` in
`build_asset/build_character/player-character-generator`; the knife: `node tools/generate.mjs` then
`node tools/export-game.mjs` in `build_asset/build_weapon/pixel-weapon-generator`.

Full controls: [04-GAME-DESIGN.md → Controls](04-GAME-DESIGN.md#controls).

## Quick Navigation

### Documentation Structure

All documentation is organized in `/docs/` folder:

```
docs/
├── README.md               ← This index
├── 00-OVERVIEW.md          ← Quick project summary (5 min)
├── 02-ARCHITECTURE.md      ← System architecture & design
├── 03-CODE-STANDARDS.md    ← Java style & naming conventions
├── 04-GAME-DESIGN.md       ← Gameplay, mechanics, features
├── 05-SYSTEMS-GUIDE.md     ← Detailed system specifications
├── 07-AGENT-CODING-RULES.md ← Mandatory AI constraints
├── 08-COMMON-PATTERNS.md   ← Copy-paste code templates
├── 09-AI-QUICKSTART.md     ← AI guide (read this first!)
└── character-weapon-system.md ← Character + weapon composition (source of truth)
```

| File | Content |
|------|---------|
| [00-OVERVIEW.md](00-OVERVIEW.md) | Quick project summary (5 min) |
| [02-ARCHITECTURE.md](02-ARCHITECTURE.md) | System architecture & design |
| [03-CODE-STANDARDS.md](03-CODE-STANDARDS.md) | Java style & naming conventions |
| [04-GAME-DESIGN.md](04-GAME-DESIGN.md) | Gameplay, mechanics, features |
| [05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md) | Detailed system specifications |
| [07-AGENT-CODING-RULES.md](07-AGENT-CODING-RULES.md) | Mandatory AI constraints |
| [08-COMMON-PATTERNS.md](08-COMMON-PATTERNS.md) | Copy-paste code templates |
| [09-AI-QUICKSTART.md](09-AI-QUICKSTART.md) | AI guide (read this first!) |
| [character-weapon-system.md](character-weapon-system.md) | Character + weapon composition: hand anchors, weapon JSON, flip/rotation (source of truth) |

### Reading Order by Role

**If you're starting fresh:**
1. [00-OVERVIEW.md](00-OVERVIEW.md) (Quick facts)
2. [04-GAME-DESIGN.md](04-GAME-DESIGN.md) (Understand the game)
3. [02-ARCHITECTURE.md](02-ARCHITECTURE.md) (How it's organized)
4. [05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md) (Each system detailed)

**If you're writing code:**
1. [03-CODE-STANDARDS.md](03-CODE-STANDARDS.md) (Follow rules)
2. [08-COMMON-PATTERNS.md](08-COMMON-PATTERNS.md) (Use templates)
3. [05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md) (Understand what to code)

**If you're using AI to code:**
1. **[09-AI-QUICKSTART.md](09-AI-QUICKSTART.md)** (START HERE)
   - Explains workflow
   - Common patterns by feature
   - Example walkthrough

### For AI Assistants

Then reference:
- **[04-GAME-DESIGN.md](04-GAME-DESIGN.md)** - Game mechanics (what to build)
- **[05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md)** - System specifications (how to build)
- **[07-AGENT-CODING-RULES.md](07-AGENT-CODING-RULES.md)** - Mandatory constraints (rules to follow)
- **[08-COMMON-PATTERNS.md](08-COMMON-PATTERNS.md)** - Code templates (copy-paste)

### 📋 Critical Rules (Always Remember)

| Rule | Why |
|------|-----|
| ✅ Package: `com.hung.*` | Organized code |
| ❌ NO game logic in `lwjgl3/` | Keep it portable |
| ✅ Always use delta time | Frame-rate independent |
| ✅ Components = data only | Clean separation |
| ✅ Systems = behavior only | Single responsibility |
| ❌ NO inheritance chains | Use composition |
| ✅ Cache resources | Don't reload every frame |
| ✅ Constants at top | No magic numbers |

### 🛠️ Core Packages

```java
com.hung.core       // Game loop, entry point
com.hung.world      // World, level (Tiled map + collision grid), loader
com.hung.entity     // Player, Enemy, Projectile
com.hung.component  // TransformComponent, HealthComponent, etc
com.hung.physics    // Collision, hitbox
com.hung.combat     // Weapons, damage, projectiles
com.hung.ai         // Enemy AI, state machines
com.hung.input      // Controllers (keyboard, AI, gamepad)
com.hung.animation  // Sprites, animations
com.hung.rendering  // Camera, sprite rendering
com.hung.audio      // Sounds, music
com.hung.item       // Pickups, upgrades
com.hung.ui         // HUD, menus
com.hung.util       // Helpers, constants
```

### Game Loop Execution Order

Every frame (~60 FPS):

```
1. INPUT PHASE
   └─ KeyboardController reads input
   └─ Sets controller states
   
2. UPDATE PHASE (Systems in order)
   ├─ AISystem: Enemy decisions
   ├─ PhysicsSystem: Position updates
   ├─ AnimationSystem: Frame updates
   ├─ CombatSystem: Weapon cooldowns
   └─ World: Entity-specific updates
   
3. COLLISION PHASE
   └─ CollisionSystem: Detect & handle collisions
   
4. AUDIO PHASE
   └─ SoundManager: Play queued sounds
   
5. RENDER PHASE
   └─ RenderingSystem: Draw to screen
   
6. NEXT FRAME
```

**Never call systems out of order!**

### Getting Help

**If you're stuck:**

1. Check the relevant section in these docs
2. Look for a similar pattern in [08-COMMON-PATTERNS.md](08-COMMON-PATTERNS.md)
3. Review the existing code in your project
4. If using AI: Check [07-AGENT-CODING-RULES.md](07-AGENT-CODING-RULES.md) first
5. Ask clearly with context and existing code

---

**Generated**: September 28, 2026  
**For**: Java 2D side-view platformer shooter  
**Version**: 1.0 Complete
