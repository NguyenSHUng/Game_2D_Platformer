# Character & Weapon System

[← Index](README.md)

Source of truth for how characters and weapons are loaded, attached and drawn.
Related: [05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md) (coordinates, data files, game loop).

## 1. Purpose

Character animation and weapon sprites are **separate assets**. The character sheets contain
only the body; the weapon is its own texture. At runtime the renderer draws the character
frame, then draws the equipped weapon at the character's hand for that frame, using the same
facing direction.

This means:
- Any weapon can be held by any character that defines hand positions.
- No combined "character-with-weapon" sprite sheets are needed.
- Adding a weapon never touches character animation code, and vice versa.

## 2. Current Asset Specification

All asset-specific numbers live in JSON next to the asset, never in rendering code. Both assets come
from the generators in `build_asset/` (2D side view, same military palette).

### Character: `sprites/players/player_soldier/` (player generator, `tools/export-game.mjs`)

| File | Content |
|------|---------|
| `<anim>.png` (idle, walk, attack_knife, hit, death, crouch, jump) | one row of 64×64 frames: the body **without** a weapon |
| `<anim>_hand.png` | same frames, only the front hand (drawn over the weapon's grip) |
| `player_soldier.json` | `weaponLayer: "SEPARATE"`, `drawScale 0.5`, per-animation `sheet`, `handSheet`, `frames[]` (durations, events, attack boxes), `handFrames` (hand anchor + rotation of every frame) |

- Visible body ≈ 53 px tall; drawn at `drawScale 0.5` → ≈ 26.5 world units (1.66 tiles).
- `--weapon baked` in the export script ships strips with the knife drawn in (then no weapon is equipped).

### Weapon: `sprites/weapons/combat_knife/` (weapon generator, `tools/export-game.mjs`)

| File | Content |
|------|---------|
| `combat_knife.png` | 24×9 RGBA, transparent, facing right (pommel left, tip right) |
| `combat_knife.json` | `id, name, texture, drawScale, pivot (5,4) = grip anchor, rotation 0, tipAnchor (22,3)` + generator metadata |
| `attachments/player_soldier/combat_knife.json` | `weaponScale 1`: one knife pixel = one soldier pixel |

- Art faces **right**. Left-facing is produced by flipping, never by a second asset.
- With the profile the knife is drawn at the soldier's `drawScale` (0.5): body and weapon share one pixel density.
- The map property `playerWeapon` names another weapon; without it a `SEPARATE` player holds
  `Constants.DEFAULT_PLAYER_WEAPON`.

### Firearm: `sprites/weapons/service_pistol/` (weapon generator Phase 3)

| File | Content |
|------|---------|
| `service_pistol.png` | 16×14 RGBA, muzzle right, transparent trigger-guard opening |
| `service_pistol.json` | pivot = grip (4,7), `supportAnchor` (3,8), `muzzleAnchor` (15,3), `ejectionAnchor`, `stance: PISTOL`, `fire`, `effects` |
| `fx/muzzle_flash.png`, `fx/tracer.png`, `fx/casing.png`, `fx/impact.png` | effect strips (frame size / durations / origin in the JSON) |

- Two-handed: the player's `*_PISTOL` animations (character generator) put the back hand on the grip by IK;
  the game only places the pistol's pivot on the front hand (`handFrames`), the front-hand strip goes on top.
- Switching weapons switches the animation set (`AnimationComponent.stances`, `AnimationFrames.useStance`).

### Rifle: `sprites/weapons/assault_rifle/` (weapon generator Phase 4)

- 31–34 × 13–15 px M4-style profile; pivot = **front-hand hold under the handguard** (the frame's weapon anchor),
  `supportAnchor` = pistol grip (back hand) 9 px behind / 1 px below, `stockAnchor` = butt, `muzzleAnchor`.
- The player's `*_RIFLE` animations put the front hand forward and the back hand on the grip by IK at that span
  (`Skeleton.STANCE_SUPPORT.RIFLE`), so the stock lands on the shoulder. `fire.mode` = AUTO.

### Units and pixel-art rendering

- **1 world unit = 1 map pixel.** Source texture pixels are converted to world units by each asset's `drawScale`; texture resolution is never assumed to equal game size.
- Textures load with **nearest-neighbor** filtering (`ResourceLoader`).
- The character's feet point is snapped to whole world units before drawing, so the body and weapon stay aligned and don't shimmer while moving.
- Character and weapon share one pixel density (attachment profile `weaponScale 1`). Another pair may differ; changing `drawScale` / `weaponScale` in JSON is enough to tune it.

## 3. Architecture

The project is component-based: **components are data, systems are behavior** (see
[02-ARCHITECTURE.md](02-ARCHITECTURE.md)). The target concept maps onto existing pieces
instead of new manager classes:

```
Entity (player)
 ├── TransformComponent      position, height, isFacingRight  ← one direction for body + weapon
 ├── AnimationComponent      animations, current state, stateTime, hand anchors   ("AnimationController" data)
 │     └── AnimationSystem   picks state, advances time                            ("AnimationController" logic)
 ├── WeaponComponent         equipped Weapon + aimRotation + size/pivot/anchors    ("WeaponController")
 │     │                     resolved for this character (section 12)
 │     └── Weapon            TextureRegion, drawScale, pivot, base rotation        (built from weapon JSON)
 └── RenderingSystem
       ├── drawCharacter()   current frame, mirrored when facing left              ("CharacterRenderer")
       └── drawWeapon()      weapon at the hand anchor, mirrored + rotated         ("WeaponRenderer")
```

| Piece | Package | Responsibility |
|-------|---------|----------------|
| `AnimationComponent` | `component` | Animations per `AnimationType`, current type, `stateTime`, `drawScale`, `footOffset`, **hand anchors per type** |
| `AnimationSystem` | `animation` | Chooses IDLE / WALK / JUMP from movement; resets/advances `stateTime`. Knows nothing about weapons. |
| `HandAnchor` | `animation` | Where the hand is in a frame (`x`, `y`, `rotation`). Part of the character data. |
| `WeaponDefinition` | `combat` | JSON data for one weapon |
| `Weapon` | `combat` | Runtime weapon shared by every holder: region, default `drawScale`, default pivot, base rotation |
| `AttachmentProfile` | `combat` | JSON data: how one character holds one weapon (section 12) |
| `WeaponComponent` | `component` | What the entity holds (`equipped`), `aimRotation`, and the weapon's size, origin and hand anchors **resolved for this holder** |
| `EntityFactory.equipWeapon` | `entity` | Applies a profile (if any) and fills `WeaponComponent` |
| `RenderingSystem` | `rendering` | Draws each entity's body, then its weapon, in one entity pass between the map's tile layers |
| `WorldLoader` | `world` | Loads the weapon JSON named by the level, looks up the profile, equips the player |

**Why not separate CharacterRenderer / WeaponRenderer / controller classes?** A character's weapon
must be drawn immediately after that character so a character drawn later covers both. Keeping both
draws in `RenderingSystem` preserves that with one batch pass. The split into two private methods keeps the responsibilities
separate without extra objects.

## 4. Animation

- **States** (`AnimationType`): `IDLE`, `WALK`, `JUMP`. Future: `SHOOT`, `RUN`, `DEATH`, …
  (add the enum value, choose it in `AnimationSystem.resolveType`, add it to the JSON).
- **Current state**: `AnimationComponent.currentType`, chosen each frame by `AnimationSystem`:
  airborne → `JUMP`, moving → `WALK`, else `IDLE`. A type missing from the JSON falls back to `IDLE`.
- **Frame timing**: `stateTime` resets to 0 when the state changes and grows by `delta` otherwise.
  Each animation has its own `frameDuration` and `playMode` (`LOOP`, `NORMAL`, …).
- **Sprite sheets**: one row of equal frames, split with `TextureRegion.split(sheet, frameWidth, frameHeight)`,
  first `frameCount` frames used.
- **Frame selection**: `frameIndex = animation.getKeyFrameIndex(stateTime)`; the frame is
  `getKeyFrames()[frameIndex]`. The same `frameIndex` selects the hand anchor (section 6).

The animation code has no reference to weapons. Hand anchors are plain character data.

## 5. Weapon

### Weapon JSON (`sprites/weapons/<type>/<name>.json`)

```json
{
  "id": "compact-military_v2",
  "name": "Compact Military Pistol (v2)",
  "texture": "sprites/weapons/gun/compact-military_v2.png",
  "drawScale": 1.5,
  "pivot": { "x": 8, "y": 16 },
  "rotation": 0
}
```

| Field | Meaning |
|-------|---------|
| `id` | Unique weapon id; names the attachment profile file (section 12) |
| `texture` | Weapon image; art must face right |
| `drawScale` | World units per weapon pixel. Render size = texture size × `drawScale` (aspect preserved) |
| `pivot` | Grip point in weapon pixels, **top-left origin** (as read in an image editor). Placed on the hand; rotation happens around it |
| `rotation` | Base rotation in degrees (counter-clockwise), to correct art that isn't perfectly horizontal |

### Runtime

- `Weapon` holds the `TextureRegion`, default `drawScale`, default `pivot` (weapon pixels) and base `rotation`.
  It is shared by every entity holding that weapon.
- `WeaponComponent.equipped` is the weapon the entity holds (`null` = unarmed, nothing drawn).
- `WeaponComponent.width`/`height`/`originX`/`originY` are the world size and pivot **for this holder**,
  filled by `EntityFactory.equipWeapon` (profile `weaponScale`/`pivot` applied, else the weapon's defaults).
- `WeaponComponent.handAnchors` holds the profile's resolved anchors per animation (empty = use the character's).
- `WeaponComponent.aimRotation` is extra rotation for future aiming (0 for now).
- The map chooses the player's weapon: map property `playerWeapon` (e.g. `sprites/weapons/rifle/rifle.json`) in the `.tmx`, next to `playerCharacter` (both optional; the generated maps don't set them until a player character exists).

**Adding a weapon**: drop the PNG (facing right, transparent background), add its JSON, point
`playerWeapon` at it. No code changes.

## 6. Weapon Attachment

The weapon is positioned **relative to the character's feet point**, never in unrelated world
coordinates.

### Hand anchors (in `character.json`, per animation)

```json
{ "type": "WALK", ..., "hand": { "x": 46, "y": 18, "rotation": 0 } }
{ "type": "JUMP", ..., "handFrames": [ { "x": 39, "y": 35, "rotation": -75 }, ... one per frame ] }
```

- `hand` — one anchor for every frame of the animation.
- `handFrames` — one anchor per frame (must have `frameCount` entries); overrides `hand`.
- `x`, `y` — hand position in **frame pixels, top-left origin** (read straight from an image editor;
  exact convention in section 12).
- `rotation` — how the weapon is angled in that pose, degrees, counter-clockwise, as seen facing right.
- These are the character's **defaults** for any weapon. An attachment profile (section 12) can
  replace them for one specific weapon.
- No anchor for the current animation (neither profile nor character) → the weapon is not drawn in that state.

### From anchor to world (facing right)

```
feet    = round(position.x), round(position.y + height)     ← whole world units
offsetX = (hand.x - frameWidth / 2)              * characterScale
offsetY = (frameHeight - hand.y - footOffset)    * characterScale
hand    = feet + (offsetX × direction, offsetY)             ← direction = +1 right, −1 left
```

The character frame is drawn from the same rounded `feet`, so body and weapon never drift apart.

| Transform value | Source |
|-----------------|--------|
| `offsetX`, `offsetY` | Hand anchor of the current frame, scaled by the character's `drawScale` |
| `rotation` | `hand.rotation + weapon.rotation + aimRotation` |
| `scaleX`, `scaleY` | `±1` (sign = facing), `1`. Size itself comes from `WeaponComponent.width/height` |
| pivot / origin | `WeaponComponent.originX/originY`; the weapon is drawn so its pivot sits on the hand |

Per-frame anchors (`handFrames`) handle poses where the hand moves (the jump). No IK.

### Measured default anchors for `dark_solider`

| Animation | Anchor(s) | Notes |
|-----------|-----------|-------|
| IDLE | (39, 35), −75° | Arm hanging; pistol held low, pointing down-forward |
| WALK | (49, 18), 0° | Pose from `pose_transfer.py`: front fist box x 45–51, y 15–21; bobs 1 px between frames (exact per-frame values live in the profile) |
| JUMP | per frame | Crouch → raised arm (frame 4) → landing; see `character.json` |

## 7. Direction

- Directions: `RIGHT`, `LEFT`, stored once as `TransformComponent.isFacingRight` and set by `InputSystem`
  from horizontal input. Body and weapon both read it.
- Facing left:
  - Character frame is drawn with negative width (mirrored around the feet point).
  - Hand offset is mirrored: `offsetX → −offsetX`.
  - Weapon is drawn with `scaleX = −1` (mirrored around its pivot) and rotation negated
    (`rotation → −rotation`), so a weapon angled up while facing right is still angled up facing left.
- No separate left-facing assets.

## 8. Rendering

`GameScreen` uses the existing camera (`CameraController`, world units = map pixels):

```
tile layers behind (walls, decor_back, terrain, props)
SpriteBatch.begin()
    for each entity:
        draw character frame (body)
        draw weapon (if equipped and the frame has a hand anchor)
        draw front-hand frame (if the animation has a handSheet) — fingers close over the grip
SpriteBatch.end()
tile layer in front (decor_front)
```

The weapon is drawn with
`batch.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation)`, which covers
position, scale, rotation, pivot/origin and horizontal flip in one call.

Drawing the weapon after the body puts it in front of the body; the front-hand strip then puts the
glove back on top of the handle (weapon generator spec §23: body → back hand → weapon → front hand).
Melee: the HIT frame's attack box is stretched to the weapon's `tipAnchor` (`Hitboxes.weaponTip`), placed
exactly like the drawn blade (pivot on the hand, hand rotation, mirrored when facing left).

## 9. Future Aiming

The transform already separates pose rotation from aim rotation. Mouse aiming only needs to
write `isFacingRight` and `WeaponComponent.aimRotation`:

```
mouse position (screen)
      ↓  camera.unproject
mouse position (world)
      ↓  minus weapon pivot
direction vector
      ↓  angle α = atan2(dy, dx) in degrees
isFacingRight = cos(α) ≥ 0
aimRotation   = isFacingRight ? α : 180° − α        (normalized to −180..180)
      ↓
renderer: rotation = (hand.rotation + weapon.rotation + aimRotation) × (facing ? 1 : −1)
```

When aiming is active, aim poses would normally use `hand.rotation = 0` so the aim angle alone
decides the weapon angle. A muzzle point (for projectiles) can later be added to the weapon JSON
the same way as `pivot`. Not implemented in this milestone.

## 10. Implementation Plan

1. **Existing architecture integration** — keep the ECS-lite layout: new data goes into components
   and JSON; drawing stays in `RenderingSystem`; loading stays in `WorldLoader`/`EntityFactory`.
2. **Character animation** — unchanged behavior. Add `HandAnchor` and optional `hand` / `handFrames`
   to `AnimationDefinition`; `EntityFactory` copies them into `AnimationComponent.handAnchors`.
3. **Weapon model** — `WeaponDefinition` (JSON) and `Weapon` (runtime) in `com.hung.combat`;
   `WeaponComponent` in `com.hung.component`; `Entity` gets an optional weapon slot.
4. **Weapon attachment** — the level gets an optional `playerWeapon` (now a `.tmx` map property); `WorldLoader` loads and equips it.
   Measure hand anchors from the sprite sheets and add them to `character.json`.
5. **Weapon rendering** — split `RenderingSystem.drawEntity` into `drawCharacter` + `drawWeapon`,
   using the same frame index for the hand anchor. Nearest filtering set explicitly in `ResourceLoader`;
   snap the feet point to whole world units.
6. **Left/right direction** — mirror the hand offset, flip the weapon with `scaleX = −1`.
7. **Rotation** — negate rotation when mirrored; pose rotation from anchors, `aimRotation` for later.
8. **Testing** — build; render idle, walk and jump facing both ways; set `aimRotation` to confirm
   rotation mirrors; swap to the other pistol to confirm no code change is needed.

### How it was verified

An offscreen test run (not part of the repo) loaded `map1` through `WorldLoader`, replaced the
player's controller with a scripted one, stepped `GameScreen` at a fixed 1/60 s and saved frames
from a `FrameBuffer`. Checked: idle / walk / jump (crouch + apex) facing right and left,
`aimRotation = 30` facing right and left, and the player re-equipped with `compact-military.json`
at runtime.

To check by hand (once a map sets `playerCharacter`): `gradlew lwjgl3:run`, move with A/D or arrows, jump with Space;
`F3` shows the collision box and the feet point the hand offsets are measured from. Tune `hand` / `handFrames`
in `character.json` and `drawScale` / `pivot` in the weapon JSON — no rebuild of code is needed,
only a restart.

## 11. Acceptance Criteria

- [x] Project builds successfully.
- [x] Existing character animation still works.
- [x] Weapon is rendered independently.
- [x] Weapon follows the character.
- [x] Weapon can be positioned near the hands.
- [x] Character can face left/right.
- [x] Weapon mirrors correctly.
- [x] Weapon rotation works.
- [x] Weapon scale is configurable.
- [x] Weapon attachment offset is configurable.
- [x] A new weapon can be added without changing character animation code.
- [x] No combined character-with-weapon sprite sheets are required.

## 12. Attachment Profiles (character × weapon)

The hand anchors in `character.json` (section 6) are the character's defaults and work with any
weapon. An **attachment profile** customizes how **one** character holds **one** weapon: where the
hand is per frame, how big the weapon is, and which weapon pixel the hand grips. It is the single
file the Image_Processing tools write and the game reads (section 13).

### Location and lookup

- Path: `assets/attachments/<characterId>/<weaponId>.json`
  - `characterId` = `id` in `character.json`, `weaponId` = `id` in the weapon JSON.
- Looked up by `WorldLoader` when it equips a weapon. No file → the character's default anchors and
  the weapon's defaults are used. The log says which case applied.
- The `character` and `weapon` fields inside the file must equal those ids (catches copied files).

### Format

```json
{
  "character": "dark_solider",
  "weapon": "compact-military_v2",
  "weaponScale": 0.5,
  "pivot": { "x": 8, "y": 16 },
  "animations": {
    "WALK": {
      "auto":   [ { "x": 49, "y": 18, "rotation": 0 }, "... one per frame, or one for all" ],
      "adjust": [ { "dx": 0, "dy": 0, "rotation": 0 } ]
    }
  }
}
```

| Field | Required | Meaning |
|-------|----------|---------|
| `character`, `weapon` | yes | Ids of the pair |
| `weaponScale` | no | Size of one weapon pixel measured in **character pixels** (1 = same pixel size, like the Image_Processing previews). World scale = `weaponScale × character drawScale`. Absent → the weapon JSON's `drawScale` |
| `pivot` | no | Grip point for this pair, weapon pixels. Absent → the weapon JSON's `pivot` |
| `animations` | no | Keyed by `AnimationType` name. Animations not listed use the character's default anchors |
| `animations.<T>.auto` | no | Hand anchors (`x`, `y`, `rotation`) detected by the tool. Absent → the character's default anchors for `<T>` |
| `animations.<T>.adjust` | no | Manual corrections added on top: `dx`, `dy` (frame pixels), `rotation` (degrees) |

### Resolution (done once when equipping)

```
base[i]  = auto[i]                    if auto is present
         = character default[i]       otherwise (error if the character has none for <T>)
final[i] = base[i] + adjust[i]        x + dx, y + dy, rotation + rotation
```

- `auto` / `adjust` each have **1 entry** (applies to every frame) or **N entries**; when both have
  more than one they must have the same N, and N must equal the animation's `frameCount`.
- The result is stored in `WeaponComponent.handAnchors`; the renderer never reads the profile.

### Who writes what

| Field | Written by | Rule |
|-------|------------|------|
| `animations.<T>.auto` | `export_profile.py` | Re-running detection overwrites it — don't hand-edit |
| everything else (`adjust`, `weaponScale`, `pivot`, other animations) | You (tuner or text editor) | Tools never write it |

So redrawing a sprite and re-running detection keeps every manual tweak.

The exporter only **prints** the grip it detects as a suggestion. Foregrip detection is right for
rifles but wrong for pistols (on `compact-military_v2` it finds the whole grip + trigger-guard blob,
centre ≈ (8.2, 19.6), vs the hand-set (8, 16)), so writing it automatically would move weapons that
were already right.

### Current profiles

| File | Notes |
|------|-------|
| `attachments/dark_solider/compact-military_v2.json` | `weaponScale: 0.5` (same size as before profiles); WALK `auto` = front-fist box centres (first converted by hand from `layers.json`; `export_profile.py` produces the identical values); IDLE and JUMP use the character defaults |
| `attachments/dark_solider/rifle.json` | Created by `export_profile.py`. WALK `auto` detected; `adjust dx: 2` reproduces the `weapon_layers.py --dx 2` default used for the reference `character-walk-with-gun.png`; no `weaponScale` (the rifle's `drawScale: 3` = character pixel size); IDLE and JUMP use the character defaults |

The pistol `compact-military` has no profile, so it uses the character defaults and its own `drawScale`.

### Verification (Phase 1)

Offscreen run (not in the repo) with the player frozen in chosen frames, comparing each render
against the same pose without a weapon:

| Case | Result |
|------|--------|
| Profile found | Log: `[WorldLoader] Attachment profile: attachments/dark_solider/compact-military_v2.json` |
| `weaponScale 0.5` | Weapon 48×48 world units, origin (12, 24) — unchanged from before |
| `adjust dx = +6` | Weapon moves +16 screen px facing right, −16 facing left (6 px × 3 × 0.9375) |
| `weaponScale 1.0` | Weapon twice as large |
| WALK frame 2 vs 0 | Moves by the 1 px difference between the per-frame anchors |
| No profile | Same position as the profile's frame 0 (character default WALK anchor = (49, 18)) |
| IDLE (not in profile) | Uses the character default anchor |
| Bad profiles | Clear errors: wrong `adjust` count, wrong `auto` count, unknown animation name |

### Coordinate convention (shared by Java and Python)

- Frame and weapon coordinates are **continuous, top-left origin**: the pixel at index `(i, j)`
  covers `[i, i+1) × [j, j+1)`; its centre is `(i + 0.5, j + 0.5)`.
- numpy `arr[y, x]` indices → add 0.5 for the pixel centre. An inclusive box `[x0, y0, x1, y1]`
  has centre `((x0 + x1 + 1) / 2, (y0 + y1 + 1) / 2)`.
- Rotation: degrees, counter-clockwise as seen facing right (+ = muzzle up).
- Placement and flipping follow sections 6–7 exactly, so a tool preview built from these rules
  matches the game.

### Precedence summary

| Value | From profile | Fallback |
|-------|--------------|----------|
| Hand anchor | resolved `animations.<T>` | `character.json` `hand` / `handFrames` → none: weapon hidden |
| World scale | `weaponScale × character drawScale` | weapon `drawScale` |
| Pivot | `pivot` | weapon `pivot` |
| Base rotation | — | weapon `rotation` |

## 13. Image_Processing Pipeline & Roadmap

`Image_Processing/` (repo root, Python + Pillow + numpy) prepares art before the game runs. It is
not shipped with the game.

| Script | What it does | Used by the game today |
|--------|--------------|------------------------|
| `pose_transfer.py` | Transfers the upper body of a pose image onto every frame of a walk sheet | Its output is `character-walk.png` |
| `weapon_layers.py` | Finds the front hand per frame (visor-relative band), places the weapon on its foregrip, writes 3 layers (character / weapon / finger grip) + `layers.json`. Detection lives in `detect_hands()` / `detect_hold()` | No — its stacked output is the reference `character-walk-with-gun.png` |
| `export_profile.py` | Runs `detect_hands()` on a character animation and writes `animations.<T>.auto` into the pair's attachment profile | Yes — writes `assets/attachments/…` |
| `weapon_tuner.py` | Window to tune `adjust` (per animation, shared or per frame), `weaponScale` and `pivot` with a live preview drawn by the game's rules; `--render` saves every frame to one image | Yes — writes `assets/attachments/…` |

### `export_profile.py`

```
cd Image_Processing
python export_profile.py sprites/players/dark_solider/character.json sprites/weapons/rifle/rifle.json --animation WALK
```

- Inputs are the game's own JSON files (asset-relative paths, as in the map and character files, or normal paths);
  the sprite sheet, frame size and frame count come from `character.json`.
- Creates the profile if missing; otherwise replaces only `animations.<T>.auto` and keeps the rest.
- Options with the same meaning as in `weapon_layers.py`: `--hand-w`, `--tilt` (`auto` = median arm
  angle), `--rot-threshold`. Anchor `rotation` = tilt + per-frame rotation. `--dry-run` prints without writing.
- Anchors are box centres (`(x0 + x1 + 1) / 2`), rounded to whole pixels so that at weapon pixel
  size = character pixel size the weapon stays on the character's pixel grid.
- **Detection limit**: the front hand must be the frontmost pixel 2–14 rows below the red visor —
  true for arms-forward poses (WALK), not for IDLE (arms down) or most JUMP frames. Those stay on
  the character defaults until tuned (Phase 3).

**Difference from `weapon_layers.py` previews**: `weapon_layers.py` places the weapon with Python's
`round()`, which rounds .5 to the nearest even number (`round(2.5) = 2`, `round(3.5) = 4`). On
frames where the hand is 1 px lower, its weapon moves 2 px. The profile follows the hand exactly, so
x matches the preview on all 8 WALK frames and y differs by 1 px on frames 1, 2, 5, 7. The layer
script was left unchanged (regression-tested byte-identical to the original).

### `weapon_tuner.py`

```
cd Image_Processing
python weapon_tuner.py sprites/players/dark_solider/character.json sprites/weapons/rifle/rifle.json
python weapon_tuner.py ... --render check.png      # no window: all frames, facing right and left
```

| Keys | Action |
|------|--------|
| `1`–`9` | Animation (order of `character.json`) |
| `,` `.` / PgUp PgDn | Previous / next frame |
| Arrows (Shift = 5 px), mouse drag | Move the weapon → `adjust.dx/dy` (moves on screen as you press, also when facing left) |
| `Q` / `E` (Shift = 5°), mouse wheel | Rotate → `adjust.rotation` (+ = muzzle up) |
| Ctrl+Arrows | Move the grip point on the weapon → `pivot` (affects every animation) |
| `-` / `=` | `weaponScale` −/+ 0.05 (character pixels) |
| `F` | This animation: one shared `adjust` ↔ one per frame (shared takes the current frame's values) |
| `C` / `R` | Copy this frame's adjust to every frame / reset it to 0 |
| Space, `M`, `O`, `H`, `[` `]` | Play, mirror (face left), onion skin (previous frame faded), markers (green = feet, red = hand), zoom |
| Ctrl+S, Ctrl+Z | Save, undo (window title shows `*` while unsaved; closing asks to save) |

- Base anchors come from `auto` when present, else from `character.json` — the same rule as the
  game — so IDLE / JUMP can be tuned without detection: their profile entry then holds only `adjust`.
- Save writes `adjust` (per animation), `weaponScale` and `pivot` if you changed them; `auto` and any
  other keys stay as they were. An all-zero `adjust` is not added to an animation that had none.
- Preview = game: `compose()` places the frame, hand anchor, pivot, scale, rotation and mirror by
  sections 6, 7 and 12. Pillow's rotation samples pixels slightly differently from the GPU, so rotated
  weapons can differ by a few edge pixels (see verification).

### Verification (Phase 2)

| Check | Result |
|-------|--------|
| `weapon_layers.py` refactor | Original vs refactored, 4 argument sets (defaults; `--tilt auto`; `--scale 2 --dx 0 --dy 1 --finger-style copy`; `--hold 18,15 --rot-threshold 5 --hand-margin -1`): all outputs and stdout byte-identical. Default args reproduce the existing `layers/` exactly |
| Pistol profile re-export | `auto` identical to the Phase 1 values; `adjust`, `weaponScale` kept |
| Rifle profile | Created with 8 WALK anchors; after adding `adjust` + `weaponScale` by hand, re-export kept both (file byte-identical) |
| Wrong ids | Refused (`… is for dark_solider / other`) |
| Placement vs `layers.json` | x equal on all frames; y differs by 1 px on frames 1, 2, 5, 7 (rounding, see above) |
| In game (offscreen render) | Log `Attachment profile: attachments/dark_solider/rifle.json`; rifle 96×96 world units, origin (60, 48); front hand on the foregrip on all WALK frames, mirrored correctly facing left; with `adjust dx 2` it matches the reference sheet apart from the finger overlay (Phase 4) |

### Verification (Phase 3)

| Check | Result |
|-------|--------|
| Editing (30 checks) | The window's key/mouse handlers driven on a hidden window against a copy of `assets/`: nudge, rotate, undo, per-frame toggle, copy/reset, drag (also mirrored), scale, pivot, save, reload. Saved file keeps `auto`, key order, and gives the same final anchors when reloaded |
| Preview = game | The tuned copy (WALK dx 3, IDLE dy −4 / +60°, JUMP per-frame, scale 1, pivot (21, 16)) rendered by Java at 1 world unit = 1 px vs `compose()` at zoom 3, 17 frames × 2 directions: **all 16 WALK frames pixel-identical**; rotated frames differ by 0–23 edge pixels of ~6 400 (0.032 % overall) |
| Real profiles | Untouched by the tests (checksums) |

### Decisions

1. **Runtime composition.** Tools produce data (and later a finger-overlay layer), not sheets with
   the weapon baked in. `character-walk-with-gun.png` stays as a visual reference.
2. **No fist trimming for game assets** (`weapon_layers.py --hand-margin -1`): trimming edits the
   character sheet per weapon, so the sheet would stop working with other weapons. The finger
   overlay (Phase 4) covers the grip instead.
3. **One profile file per pair** — re-running a tool for one pair never touches another.
4. **Tune in Python before running the game**, using the rules in sections 6, 7 and 12 so the
   preview equals the game.

### Target workflow

```
1. pose image ─► pose_transfer.py ─► character-walk.png
2. add weapon PNG + weapon JSON (with id) under assets/sprites/weapons/
3. export_profile.py: detect hands ─► writes animations.<T>.auto in attachments/<char>/<weapon>.json
4. weapon_tuner.py: nudge / rotate / scale per frame, preview both directions ─► writes adjust, weaponScale, pivot
5. gradlew lwjgl3:run ─► game resolves the profile for the equipped pair
6. not right? back to 4 — manual values survive re-detection
```

### Phases

| Phase | Scope | Done when | Status |
|-------|-------|-----------|--------|
| 0 | This contract: profile format, location, resolution, coordinate convention | Documented here | ✅ |
| 1 | Game reads profiles: ids in character/weapon JSON, `AttachmentProfile`, lookup in `WorldLoader`, resolution in `EntityFactory.equipWeapon`, renderer uses resolved values; fallback to character defaults | Editing a number in a profile moves the weapon in game; deleting the file falls back cleanly | ✅ |
| 2 | Detection split out of `weapon_layers.py` (`detect_hands`, `detect_hold`); new `export_profile.py` writes `auto` only (a separate script rather than a `--export-profile` flag, because its inputs are the game JSON files, not raw images) | A generated profile for dark_solider × rifle looks right in game | ✅ |
| 3 | `weapon_tuner.py` (tkinter): zoom, onion skin, play, mirror, nudge/rotate/scale/pivot, undo, save `adjust`; `--render` contact sheet | IDLE / WALK / JUMP of one pair can be tuned without opening the game, and the game draws what the preview shows | ✅ (tool; the actual tuning of IDLE / JUMP is yours) |
| 4 | Finger-grip overlay per pair: tool writes the layer, profile gets `animations.<T>.gripOverlay`, renderer draws character → weapon → overlay (mirrored) | Fingers wrap the grip like the reference sheet, composed at runtime | Planned |
| 5 | Reload profiles in game without restarting; show hand anchors in the `F3` overlay | Edit → reload → visible immediately | Planned |

Later, for mouse aiming: GPU rotation of pixel art is jagged. `weapon_layers.py` already has a
pixel-preserving shear tilt; it can pre-render a weapon at 16–32 angles for the game to pick from.
