# 05 - Dữ liệu và các system

[← Mục lục](README.md)

Định dạng file dữ liệu game đọc, và từng system làm gì, theo quy tắc nào. Thứ tự các system trong một khung hình:
[02-ARCHITECTURE.md → mục 3](02-ARCHITECTURE.md#3-thứ-tự-một-khung-hình). Con số gameplay: [04-GAME-DESIGN.md](04-GAME-DESIGN.md).

| Mục | Nội dung |
|-----|----------|
| [0](#0-toạ-độ-và-file-dữ-liệu) | Toạ độ và file dữ liệu (map, quái, player, vũ khí) |
| [1](#1-input) | Input |
| [2](#2-vật-lý) | Vật lý |
| [3](#3-va-chạm-và-vùng-nguy-hiểm) | Va chạm và vùng nguy hiểm |
| [4](#4-chiến-đấu) | Chiến đấu |
| [5](#5-máu-và-sát-thương) | Máu và sát thương |
| [6](#6-ai-của-quái) | AI của quái |
| [7](#7-animation-và-hiệu-ứng) | Animation và hiệu ứng |
| [8](#8-vẽ) | Vẽ |
| [9](#9-thế-giới-phòng-và-cửa) | Thế giới, phòng và cửa |
| [10](#10-màn-hình-menu-và-lưu-game) | Màn hình, menu và lưu game |

## 0. Toạ độ và file dữ liệu

### Toạ độ

- 2D góc nhìn ngang: x trái / phải, **y hướng lên**, trọng lực kéo về −y.
- **1 đơn vị thế giới = 1 pixel của map**; tile 16 × 16 px; gốc ở góc dưới trái của map.
- `TransformComponent.position` là **điểm chân** (giữa đáy) của entity. Sprite vẽ căn giữa theo x, chân đặt trên y;
  hộp `ColliderComponent` trải `x ± width/2`, `y .. y + height`.
- Toạ độ trong JSON của tool (khung hình, anchor, hitbox) là pixel của khung, gốc trên trái, y hướng xuống. `Hitboxes`,
  `RenderingSystem` đổi sang toạ độ thế giới (lật khi entity quay trái).
- Vùng nhìn tối thiểu `Constants.VIEW_MIN_WIDTH × VIEW_MIN_HEIGHT` = 640 × 360 đơn vị (≈ 40 × 22 tile), nên cửa sổ
  1280 × 720 hiển thị pixel art 16 px ở tỉ lệ 2×.

### Map: `assets/maps/<tên>/map_<chế độ>.tmx` (Tiled)

Xuất bởi map generator (`build_asset/build_map/platformer-map-generator`, `node tools/export-game.mjs --mode easy|medium|hard`).
File mở được bằng [Tiled](https://www.mapeditor.org) để chỉnh tay. Mỗi chế độ một map: `GameMode.EASY` / `MEDIUM` / `HARD` → `Constants.EASY_MAP_PATH` / `MEDIUM_MAP_PATH` / `HARD_MAP_PATH`.
Mỗi map một thư mục (`bunker_easy`, `bunker_medium`, `bunker_hard`). Tool chỉ đặt những quái đang có trong `assets/sprites/enemies/`; bảng `MODES` có `roomWidthMin` / `roomWidthMax` / `roomHeight` (cỡ ô phòng, tile), `enemyDensity`,
`enemySpacing` (bề rộng phòng cho mỗi quái), `enemiesPerRun`, `enemyMinRun`, `enemiesOnTiers`, `featureSpacing` và `mix` (bao nhiêu melee đổi thành crawler / wasp, rải đều). Object `ENEMY` của quái bay đặt cao 40 px trên sàn.

| Phần | Nội dung |
|------|----------|
| Layer tile | `walls` · `decor_back` · `terrain` · `props` · `decor_front` (vẽ từ sau ra trước; entity nằm giữa `props` và `decor_front`) |
| Tileset `terrain` | Mỗi tile có thuộc tính **`type`**: `SOLID`, `PLATFORM`, `LADDER`, `SPIKE`, `TOXIC`, `BREAKABLE`, `EMPTY` → va chạm |
| Object `spawns` | Hình chữ nhật `PLAYER_SPAWN` và `EXIT` (1 × 2 tile; cạnh dưới = chân) |
| Object `enemies` | Mỗi quái một `ENEMY`; thuộc tính `definition` (đường dẫn JSON quái), `patrolMinX`, `patrolMaxX`, `facing`, `room` |
| Object `pois` | Checkpoint (`CHECKPOINT`) và các điểm khác |
| Object `lights` | Nguồn sáng (`LightSource`: loại, bán kính px, màu, cường độ, kiểu nhấp nháy, pha) |
| Object `rooms` | Mỗi phòng một `ROOM` = **giới hạn camera** của phòng; `kind`, `region`, `onPath` |
| Object `doors` | Mỗi cửa một `DOOR` = **vùng kích hoạt**; `room`, `targetRoom`, `targetDoor` (null = hố rơi một chiều), `kind` (`SIDE` / `LADDER` / `DROP`), `direction`, điểm xuất hiện `spawnX` / `spawnY` (pixel Tiled, y xuống) |
| Thuộc tính map | `physics.moveSpeed`, `physics.jumpHeight`, `physics.jumpDistance` (tile), `seed`, `theme`; tuỳ chọn `playerCharacter`, `playerWeapon` (đường dẫn JSON) |

- **Hợp đồng vật lý:** map generator chỉ tạo khoảng nhảy làm được với 80% `physics.*`, nên `PhysicsSettings` tính trọng
  lực và lực nhảy từ chính các giá trị đó: thời gian bay T = jumpDistance / moveSpeed, trọng lực = 8·jumpHeight / T²,
  lực nhảy = 4·jumpHeight / T. Đổi tốc độ player ở map generator rồi xuất lại, không cứng trong code.
- Tool ghi thêm `export/maps/<tên>/level.json` trong thư mục của nó (dữ liệu đầy đủ, hub trong `build_asset/` đọc); game không dùng.

### Quái: `assets/sprites/enemies/<id>/character.json`

Do enemy generator (`build_asset/build_enemy/enemy-character-generator`) tạo; `node tools/sync-game.mjs` chép 5 quái của game; tool map chỉ đặt vị trí, không chép sprite.
Đọc vào `EnemyDefinition` (bỏ qua trường lạ):

| Trường | Ý nghĩa |
|--------|---------|
| `drawScale` | Pixel sprite → đơn vị thế giới (0,5 cho mọi quái hiện có; không có = 1) |
| `anchors.footAnchor` / `anchors.feet` | Đường sàn trong khung hình |
| `spriteSheet` | `image`, `frameWidth`, `frameHeight` |
| `animations.<tên>` | `state` (`IDLE`, `MOVE`→WALK, `ATTACK_1`→ATTACK, `HIT`, `DEATH`), `loop`, `frames[]` (vị trí, `duration` ms, `bounds`, `attackActive`, `hitbox`) |
| `animations.move.move` | `groundSpeed` / `airSpeed`: tốc độ không trượt chân (px/s) |
| `archetype` | `SMALL_MELEE`, `CRAWLER`, `FLYING`: chọn class quái |
| `gameplay` | `durabilityClass`, `damageClass`, `attacks.<state>.reach.world` |

- Hộp va chạm lấy từ `bounds` của khung IDLE đầu tiên: (rộng − 4) × cao, nhân `drawScale` và `SIZE_SCALE` (1,2). Với quái `small_melee_*`, số trong file
  được giữ cố định, không đo lại theo hình.
- Ba con `small_melee_*` xuất bằng `tools/melee-export.mjs` (ghoul thêm `--polish small_melee_ghoul`), không dùng `generate.mjs`; xuất ra
  `export/enemies/` của tool, rồi `node tools/sync-game.mjs` chép cả 5 quái của game vào `assets/`. Wasp chỉnh bằng `tools/wasp-polish.py`.
- `EnemyFactory` đổi các mức LOW / MEDIUM thành con số máu và sát thương ([04 → mục 6](04-GAME-DESIGN.md#6-quái)).

### Player: `assets/sprites/players/<id>/<id>.json`

Do character generator xuất (`build_asset/build_character/player-character-generator`,
`node tools/export-game.mjs --id player_soldier --scale 0.5`). File chứa nhiều dữ liệu hơn; game đọc vào `CharacterDefinition`:

| Trường | Ý nghĩa |
|--------|---------|
| `id` | Tên thư mục profile cầm vũ khí (`assets/attachments/<id>/`) |
| `frameWidth`, `frameHeight` | 64 × 64 |
| `drawScale` | 0,5: khung 64 px = 32 đơn vị, thân ≈ 26,5 đơn vị (1,7 tile) |
| `footOffset` | Pixel trong suốt dưới chân (0) |
| `colliderWidth`, `colliderHeight` | Hộp thân tính bằng **pixel sprite** (14 × 53), nhân `drawScale` |
| `weaponLayer` | `SEPARATE`: dải animation chỉ có thân, vũ khí là sprite riêng, bàn tay trước nằm ở `<anim>_hand.png` |
| `animations[]` | `type` (một `AnimationType`), `stance` (không có / `PISTOL` / `RIFLE`), `sheet`, `handSheet`, `frameCount`, `frameDuration`, `playMode`, `frames[]` (`duration` ms, `event`, `attackBox`), `handFrames` (anchor tay mỗi khung), `walk.groundSpeed`, `hurtbox` (của CROUCH = chiều cao khi ngồi) |

- Mỗi sheet là **một hàng khung bằng nhau**, đọc từ trái sang. Tên file không có thư mục nằm cạnh JSON.
- `IDLE` bắt buộc. Animation có `stance: PISTOL` / `RIFLE` tạo bộ animation thứ hai / thứ ba (IDLE, WALK, CROUCH,
  JUMP, SHOOT); trạng thái bộ đó thiếu (HIT, DEATH) lấy từ bộ mặc định.
- Tốc độ đi / nhảy / leo **không** nằm trong file này mà lấy từ map.
- Player là thuộc tính map `playerCharacter`, nếu không có thì `Constants.DEFAULT_PLAYER_CHARACTER`. Không có file đó
  thì map nạp không có player và camera tự do.
- Player `SEPARATE` cầm `Constants.DEFAULT_PLAYER_WEAPON` (dao) hoặc `playerWeapon` của map, cộng mọi vũ khí trong
  `Constants.DEFAULT_PLAYER_ARSENAL` (súng lục, súng trường): dao = ô 1, súng lục = ô 2, súng trường = ô 3.

### Vũ khí: `assets/sprites/weapons/<id>/<id>.json`

Do weapon generator xuất (`build_asset/build_weapon/pixel-weapon-generator`, `node tools/export-game.mjs`):

| Trường | Ý nghĩa |
|--------|---------|
| `id`, `name`, `texture` | Định danh, tên hiển thị, đường dẫn hình (tính từ gốc asset) |
| `drawScale`, `pivot`, `rotation` | Tỉ lệ, điểm cầm (pixel vũ khí, gốc trên trái), góc gốc |
| `tipAnchor` | Mũi dao (kéo attack box tới đây) |
| `muzzleAnchor`, `ejectionAnchor`, `supportAnchor` (game chưa dùng), `stance` | Súng: đầu nòng, cửa văng vỏ, tay đỡ, bộ animation cầm (`PISTOL` / `RIFLE`) |
| `fire` | `mode` (`SEMI` / `AUTO`), `roundsPerSecond`, `damage`, `magazine`, `reloadSeconds`, `projectileSpeedTiles`, `rangeTiles`, `spreadDegrees`, `recoilDegrees` (game chưa dùng), `shellEject {speedTiles, angleDegrees}` (tile × kích thước tile của map) |
| `effects` | `muzzleFlash`, `tracer`, `casing`, `impact`: mỗi cái `{texture, frameWidth, frameHeight, frameCount, durations (ms), origin, loop}`, ảnh trong `fx/` |

Cách nhân vật cầm vũ khí (anchor tay, profile `assets/attachments/<nhân vật>/<vũ khí>.json`):
[character-weapon-system.md](character-weapon-system.md).

### Thêm nội dung

| Muốn | Làm |
|------|-----|
| Map mới | Tạo trong map tool, `node tools/export-game.mjs --seed <n> --name <tên>`; hoặc sửa bảng `MODES` rồi chạy với `--mode`; trỏ hằng `*_MAP_PATH` tới `maps/<tên>/` |
| Quái mới | `node tools/generate.mjs ...` trong enemy tool, rồi chạy lại map export |
| Thêm / dời quái bằng tay | Mở `.tmx` trong Tiled, nhân bản object trong layer `enemies`, sửa `definition`, `patrolMinX` / `patrolMaxX` |
| Trạng thái animation mới | Thêm vào `AnimationType`, chọn nó trong `AnimationSystem`, thêm vào JSON |

## 1. Input

`com.hung.input` · `InputSystem` đọc `Controller` của entity được điều khiển (kèm collider, máu, lưới tile).

| Việc | Quy tắc |
|------|---------|
| Đi | `velocity.x = direction.x × moveSpeed`; dừng khi đang ngồi hoặc đang vung dao trên mặt đất |
| Nhảy | `velocity.y = jumpSpeed` nếu đã bấm nhảy trong 0,1 s gần nhất (`jumpBufferTime`) **và** đã đứng trên vật gì đó trong 0,08 s gần nhất (`airTime`, coyote time) |
| Ngồi | Xuống khi đứng trên mặt đất → `isCrouching` (collider dùng `crouchHeight`); chỉ đứng dậy khi đủ chỗ trên đầu |
| Xuống bệ | Ngồi + nhảy trên bệ một chiều: dời chân xuống 2 đơn vị dưới mặt bệ |
| Thang | Lên / xuống tại thang → `isClimbing`, căn x vào cột thang; sau đó lên / xuống với `climbSpeed`; trái / phải rời; nhảy để nhảy khỏi; quá bậc trên cùng → đứng lên; chạm sàn → buông |
| Đánh / bắn / nạp / đổi vũ khí | Đặt cờ yêu cầu: `AttackComponent.isRequested`, `WeaponComponent.isFireRequested` (giữ nút: tiếp tục với súng `AUTO`), `isReloadRequested`, `ArsenalComponent.requestedSlot` / `isNextRequested` |
| Không điều khiển | Đang bị thương hoặc đã chết: giữ knockback; `setEnabled(false)` (camera tự do) dừng player |

Phím đặt thành mảng ở đầu `KeyboardController` (player) và `CameraInput` (camera tự do). Khi bảng nhân vật mở, phím
chọn vũ khí được đọc ở `GameScreen` ([02 → mục 5](02-ARCHITECTURE.md#5-input)).

## 2. Vật lý

`com.hung.physics` · `PhysicsSystem`

- Mọi entity có `MovementComponent` (trừ khi đang leo hoặc bay) chịu trọng lực: `velocity.y -= gravity × delta`,
  giới hạn ở `maxFallSpeed`; `gravity` lấy từ `PhysicsSettings` của level.
- Entity có `ColliderComponent` di chuyển qua `CollisionSystem.move(entity, dx, dy)`; entity khác cộng thẳng
  `velocity × delta`. Đạn do `ProjectileSystem` tự di chuyển, `PhysicsSystem` bỏ qua.

## 3. Va chạm và vùng nguy hiểm

`CollisionSystem` (do `PhysicsSystem` gọi): đẩy hộp qua `TileGrid` theo từng trục, cùng luật với bộ kiểm tra của map
generator.

| Hướng | Luật |
|-------|------|
| Ngang | `SOLID` / `BREAKABLE` chặn → `isBlocked` |
| Đi lên | `SOLID` chặn đầu; `PLATFORM` và thang xuyên qua được |
| Rơi xuống | Đáp lên `SOLID`, `PLATFORM` hoặc bậc trên cùng của thang, chỉ khi chân đi qua mặt tile từ trên xuống → `isGrounded`; đang leo thì bậc trên cùng không giữ lại |
| Ngoài map | Hai bên là tường, trên và dưới là khoảng trống |

Hộp va chạm là `Hitboxes.body(entity)`: `width` × `height`, hoặc `crouchHeight` khi ngồi.

`HazardSystem` (sau vật lý, chỉ player):
- Chạm checkpoint (`pois`) → `World.setRespawnPoint`, phát `CheckpointEvent`.
- Chạm 80% dưới của tile `SPIKE` / `TOXIC`, hoặc rơi 64 đơn vị dưới đáy map → `PlayerRespawn.respawn` (không trừ máu).
- Tới lối ra (một lần) → phát `ExitReachedEvent`.

Va chạm thân với thân: `ContactDamageSystem` (quái còn sống chạm player → `Damage.apply`). Quái không chặn đường đi.

## 4. Chiến đấu

`com.hung.combat` (+ `combat.system`, `combat.definition`)

**Cận chiến** (`AttackSystem`, dữ liệu từ animation):
- `AttackComponent`: `damage`, `isRequested`, `isAttacking`, `hasHit`.
- Yêu cầu bắt đầu đòn đánh (nếu còn sống và không bị thương); `AnimationSystem` chạy animation tấn công.
- Ở mỗi khung có attack box (`Hitboxes.fromSprite`, lật khi quay trái, với player kéo dài tới `tipAnchor` của dao), hộp
  gây sát thương mọi entity phe kia (player ↔ không phải player) mà nó chồng lên, và xoá tile `BREAKABLE` khỏi lưới
  lẫn layer `terrain` của map. Đòn kết thúc khi animation tấn công chạy xong.

**Vũ khí trên tay và súng:**

| Phần | Việc |
|------|------|
| `ArsenalComponent` | Danh sách vũ khí mang theo (mỗi vũ khí một `WeaponComponent`, nên băng đạn giữ nguyên khi đổi) và ô đang cầm |
| `WeaponSwitchSystem` | Đổi `Entity.weapon` sang ô được yêu cầu và gọi `AnimationComponent.useStance` theo tư thế của vũ khí; bỏ qua yêu cầu trùng ô đang cầm; không đổi khi đang vung / bị thương / chết; huỷ lượt nạp |
| `WeaponComponent` (trạng thái súng) | `ammo`, `cooldown`, `reloadTime`, `shootTime`, `isFireRequested`, `isReloadRequested` |
| `ShootingSystem` | Bóp cò → bắn khi hết hồi chiêu (1 / roundsPerSecond) và còn đạn (hết thì nạp); đạn sinh ở `Hitboxes.weaponPoint(muzzleAnchor)` theo `Hitboxes.weaponAngle` ± độ lệch; lửa nòng (`Effects.spawn`) và vỏ đạn (có trọng lực, va tile); `shootTime` giữ tư thế bắn trong một lượt animation SHOOT |
| `ProjectileSystem` | Đạn di chuyển từng bước ≤ 2 đơn vị; dừng ở tile `SOLID` / `BREAKABLE` hoặc thân sống đầu tiên của phe kia (`Damage.apply`, hiệu ứng trúng); hết tầm thì xoá; ra khỏi phòng lúc bắn (`ProjectileComponent.room`: phòng đang hiện trên màn hình của player, shooter khác thì phòng chứa nòng súng) thì xoá ngay, không hiệu ứng, trước khi kịp trúng gì ở phòng bên |

## 5. Máu và sát thương

`HealthComponent` (đóng gói): `getHealth()`, `getMaxHealth()`, `isDead()`; thay đổi qua `applyDamage(n)`,
`restoreFull()`, `setHealthClamped(n)`. Các bộ đếm để public: `hurtTime` (thời gian phản ứng HIT, không điều khiển được),
`invulnerability` (số giây bất tử mỗi đòn), `invulnerableTime` (còn lại; sprite nhấp nháy), `knockbackResistance`, `deadTime`.

`Damage.apply(target, amount, sourceX)` (dùng chung cho mọi system gây sát thương): bỏ qua nếu mục tiêu đã chết hoặc
đang bất tử; trừ máu; bắt đầu phản ứng HIT (độ dài animation HIT) hoặc chết; cấp `invulnerability` giây bất tử; đẩy lùi
khỏi `sourceX` (90 đơn vị/s × (1 − kháng)); huỷ đòn đánh, leo thang, ngồi của mục tiêu.

`HealthSystem` (sau các system gây sát thương):
- Đếm lùi các bộ đếm, giảm dần knockback (400 đơn vị/s²).
- Quái chết: chạy DEATH, nằm lại 1,5 s, rồi `World.removeEntity`.
- Player chết: chạy DEATH, chờ 1 s, hồi sinh ở điểm hồi sinh với đầy máu (`PlayerRespawn`, bất tử 1,5 s).

## 6. AI của quái

`com.hung.ai`

**`PatrolSystem`:** mọi entity có `PatrolComponent` đi với `moveSpeed` giữa `minX` và `maxX`, quay đầu ở hai đầu đoạn, khi
bị tường chặn, hoặc trước mép vực (`TileGrid.hasFloorAhead`). Quái bay không xét mép vực. Bỏ qua quái đang bị thương
hoặc đã chết.

**`EnemyBrainSystem`:** chạy sau `PatrolSystem`, gọi `Enemy.updateBehaviour(delta, world, player)` của từng quái. Hành
vi nằm trong class quái (`com.hung.entity.enemy`); thứ tự các bước cố định ở `Enemy`:

| Bước | Mặc định | Override |
|------|----------|----------|
| Đã chết → `whileDead()` | Không làm gì | `FlyingEnemy`: treo trên không khi DEATH chạy, xong thì bật trọng lực, xác rơi xuống sàn |
| `tick(delta)` | — | `HunterEnemy`: đếm lùi hồi chiêu và trí nhớ |
| Bị thương → `whileHurt()` | Không làm gì | `HunterEnemy`: bị khiêu khích (săn dù chưa thấy player) và báo tactic (`onHurt`); `FlyingEnemy`: thêm dừng vận tốc dọc |
| Còn lại → `think()` | (trừu tượng) | `HunterEnemy`: săn (dưới đây) |

`HunterEnemy.think()` ghi đè vận tốc tuần tra khi đang săn. Phần nhìn, nhớ, gọi bầy và nhảy nằm trong `HunterEnemy`; còn
**đánh, áp sát hay đứng chờ** là quyết định của `HuntTactic` của con quái đó (`com.hung.ai.tactic`, xem dưới):
- Phòng nhà (`Enemy.getHomeRoom()`, từ property `room` của quái trên map; thiếu hoặc sai id thì lấy room chứa điểm spawn): quái chỉ thấy
  player **trong phòng nhà**, chỉ gọi quái cùng phòng, không đuổi / lùi ra khỏi phòng (bị đẩy văng ra thì vẫn đi vào lại được), tuần tra
  sau khi nhảy cũng bị kẹp trong phòng. Player rời phòng thì quái quên ngay (kể cả khi bị bắn từ phòng khác). Map không có room: không giới hạn.
- Player trong `aggroRange()` × `aggroScale` (và `aggroHeight()` tính từ `floorY()`), hoặc còn trong trí nhớ (`memorySeconds`), hoặc vừa bị
  bắn / được gọi (`provoke()`) → engaged; tactic quyết định, `chase()` chạy với `moveSpeed × chaseSpeedScale() × chaseScale` (tối đa 84).
- Lúc vừa thấy player, quái gọi mọi `HunterEnemy` cùng phòng trong `alertRange`; con được gọi không gọi tiếp.
- Gặp mép vực, gai, chất độc hay tường: nếu `canJump` và `JumpPlanner` tìm được chỗ đáp thì nhảy, không thì dừng. Player ở bệ thấp ngay trên đầu
  thì nhảy thẳng lên. Đáp xong, quái tuần tra chỗ mới. `FlyingEnemy` bay qua mọi thứ này.
- Trong `attackRange` (80% tầm đánh do tool đo + khoảng nửa bề rộng player), hoặc sắp tới tầm (`aimLeadSeconds`), đúng độ cao
  (`canReachHeight()`), hết hồi chiêu và `canStrike()` (mặt đất: đang chạm đất; bay: luôn được) → đứng lại, yêu cầu đòn ATTACK. Sát thương do
  hitbox của animation qua `AttackSystem`.
- Khoảng dừng (hold distance) = `attackRange × holdRatio()` (mặc định 0,6; `MeleeEnemy` 0,75), nhưng không gần hơn lúc hai thân chạm nhau + 2
  nếu khoảng đó vẫn còn trong tầm đánh. Thực tế chỉ đổi crawler (thân rộng 34): dừng ở ~22,6 thay vì 13,6, không đứng đè lên player.
- Đòn giữ nguyên hướng lúc bắt đầu (nếu không, `PatrolSystem` xoay quái về hướng tuần tra và đòn đánh ngược).
- `afterThink()`: quái mặt đất đứng yên khi đòn đang chạy; `FlyingEnemy` lái độ cao: tuần tra cao
  `FlyingEnemy.CRUISE_HEIGHT` (40) trên sàn, săn ở trên đầu player 18 đơn vị, hạ xuống giữa thân player khi lao.

**Tactic (`com.hung.ai.tactic`)** — Strategy: mỗi `HunterEnemy` giữ một `HuntTactic` riêng (tactic có trạng thái). Mỗi khung engaged và không
đang vung đòn, quái dựng `HuntContext` (dx, tầm đánh, khoảng dừng, trong tầm chưa, đánh được chưa, bước tới / lùi có an toàn không, delta) rồi
gọi `tactic.decide(context, this)`; tactic ra lệnh qua `TacticActions` mà `HunterEnemy` cài: `face`, `strike` (yêu cầu đòn + hồi chiêu),
`approach` (= `chase()` của lớp con), `retreat`, `hold`. Tactic không tự ghi vận tốc.

| Tactic | Hành vi |
|--------|---------|
| `DirectChaseTactic` (mặc định) | Quay mặt → đánh nếu trong tầm và sẵn sàng → không thì áp sát tới khoảng dừng → không thì đứng |
| `HitAndRunTactic` | APPROACH (như DirectChase) → ATTACK → **RETREAT** khi đòn xong: lùi, mặt vẫn hướng player, tới khi cách 1,8 × tầm đánh, phía sau không an toàn hoặc quá 1 s → **RECOVER**: đứng chờ; hết hồi chiêu thì phản đòn nếu player trong tầm, không thì APPROACH. Bị đánh → RETREAT; mất mục tiêu → APPROACH |

`retreat()` chạy ở `chaseSpeed() × 0,7` (luôn dưới 84) và chỉ khi bước lùi an toàn (`canBackOff`: mặt đất phải đứng trên sàn, không có vực,
gai, chất độc phía sau; quái bay luôn được) và còn trong phòng nhà; không thì đứng. Kiểm tra này nằm trong `HunterEnemy`, nên tactic nào cũng
không lùi xuống vực được. Tactic theo chế độ và vùng: `GameMode.tacticPolicy` ([04 → mục 6](04-GAME-DESIGN.md#6-quái)).

Con số theo loại quái: [04 → mục 6](04-GAME-DESIGN.md#6-quái); cây class: [02 → mục 4](02-ARCHITECTURE.md#4-entity-component-và-cây-nhân-vật). Quái không có `Controller`.

## 7. Animation và hiệu ứng

`com.hung.animation` (+ `animation.system`, `animation.definition`) · dùng `Animation<TextureRegion>` của LibGDX.

**`AnimationComponent`:** các bộ animation theo tư thế (`stances`; 5 map của tư thế đang dùng chỉ đổi qua
`useStance`), `currentType`, `stateTime`, `drawScale`, `footOffset`, `strideSpeed`. Thời lượng riêng từng khung được
chơi bằng cách lặp khung trên nhịp 10 ms; `keyFrameSources` đổi khung lặp về khung gốc (cho anchor tay và attack box).

**`AnimationSystem`** mỗi khung hình:
1. Chọn trạng thái (điều kiện đầu tiên khớp): chết → DEATH; `hurtTime` > 0 → HIT; đang tấn công → animation tấn công;
   vừa bắn (`shootTime`) và đứng yên → SHOOT; đang leo → IDLE; đang ngồi → CROUCH; không chạm đất → JUMP;
   |velocity.x| > 1 → WALK; còn lại → IDLE. Trạng thái không có trong dữ liệu → IDLE.
2. Đổi trạng thái → `stateTime = 0`; giữ trạng thái → `stateTime += delta × tốc độ` (WALK: |vx| / `strideSpeed`, giới hạn 0,5–2,5).
3. JUMP: `stateTime` theo vận tốc dọc (bật lên với +jumpSpeed → rơi với −jumpSpeed).

**Hiệu ứng:**
- `Effects.spawn` tạo entity hiệu ứng (`SpriteComponent` + `EffectComponent`), lật theo hướng; `Effects.light` gắn ánh sáng tắt dần.
- `EffectSystem`: chạy animation của sprite, đếm lùi thời gian sống, xoá hiệu ứng xong; vỏ đạn chạm đất thì dừng trượt.

## 8. Vẽ

`com.hung.rendering` (+ `ui.minimap`)

| Phần | Việc |
|------|------|
| `RenderingSystem` | Vẽ layer tile của map bằng `OrthogonalTiledMapRenderer` (chỉ phần camera thấy), entity nằm giữa `props` và `decor_front`. Mỗi nhân vật: thân → vũ khí → bàn tay trước; sprite quay phải, quay trái thì lật. Entity đang bất tử nhấp nháy (ẩn mỗi 0,08 s) |
| `CameraController` | `ExtendViewport` (thấy ít nhất 640 × 360). Bám chân player + 24 đơn vị bằng lò xo giảm chấn mỗi trục (`SmoothDamp`, 0,18 s); nghe `RoomTransitionEvent` để ở trong giới hạn phòng hiện tại và chuyển ngay sang phòng mới. Zoom 0,5–1 (mặc định 0,8), không lớn hơn phòng. Không có phòng thì giới hạn theo map |
| `CameraInput` | Camera tự do (F2): bỏ qua phòng, zoom 0,25–6 |
| `LightingRenderer` | Sau cảnh: light map ¼ độ phân giải (nền màu tối, cộng mọi đèn nhìn thấy của level, đèn player bán kính 56, ánh sáng của hiệu ứng) nhân lên màn hình, rồi một lớp glow cộng thêm. Công thức nhấp nháy khớp map generator. `L` bật tắt |
| `FadeRenderer` | Lớp đen khi qua cửa (độ mờ = `RoomTransition.fade`) |
| `HudRenderer` | Thanh máu và số máu, vũ khí và đạn; phím điều khiển xem ở Settings → Controls (chỉ camera tự do có dòng gợi ý) |
| `StatsPanel` | Bảng nhân vật (I / F1): máu, di chuyển tính bằng tile, hộp thân, phòng thủ, vị trí, điểm hồi sinh, tư thế; mỗi ô vũ khí một thẻ (hình ×4, sát thương, DPS, tầm, băng đạn, kiểu bắn, nạp, tốc độ đạn, độ lệch, thanh so sánh) |
| `DebugRenderer` | F3: tile va chạm (đỏ tường, xanh lơ bệ, xanh lá thang, hồng bẫy, vàng tường phá được), hộp entity, attack box, đoạn tuần tra, checkpoint, điểm xuất phát, lối ra, giới hạn phòng, cửa |
| `MinimapRenderer` | Minimap góc phải trên (2 px mỗi tile, cuộn theo player; `M` = toàn map), vẽ theo `MinimapState` |

## 9. Thế giới, phòng và cửa

`com.hung.world` (+ `world.loader`, `world.system`, `world.event`)

**`World`** chứa `Level` (TiledMap + `TileGrid` + `PhysicsSettings` + điểm xuất phát / lối ra + checkpoint + đèn + phòng),
danh sách entity, player, điểm hồi sinh, `RoomTransition`. Không có logic.

**`WorldLoader.load(mapPath, enemySkill, tacticPolicy, resources)`:** nạp `.tmx` và tileset, dựng lưới va chạm từ layer `terrain`, đọc spawn / lối ra,
layer `enemies`, checkpoint, đèn; xếp hàng nạp mọi định nghĩa và texture (player, vũ khí, quái) rồi
`finishLoading()` một lần; tạo `Level`, quái, player kèm vũ khí, `World`.
`RoomLoader` thêm phòng (layer `rooms`) và một entity cửa cho mỗi object `doors`. Sau khi `EnemyFactory` tạo một quái, loader gán
phòng nhà (`Level.findRoom(spawn.roomId, x, y)`) và tactic theo vùng của phòng đó (`tacticPolicy.create(room.regionId)`).

**Phòng và cửa (theo sự kiện):**

```text
map generator (DoorPlacer) ─► layer "rooms" + "doors" ─► RoomLoader ─► Room[] + entity cửa
                                                                           │
DoorSystem: player chạm cửa của phòng hiện tại                             ▼
  → tối dần (thế giới dừng) → dời player tới door.spawn ─► EventBus.publish(RoomTransitionEvent)
  → sáng lại                                                   │                     │
                                              CameraController ◄┘                     └► MinimapState
                                              (giới hạn phòng mới, chuyển ngay)          (đánh dấu phòng, dời chấm)
```

- `RoomTransitionEvent`: `reason` (`LEVEL_START`, `DOOR`, `RELOCATED`), `previousRoom`, `room`, `cameraBounds`, `spawn`, `doorId`.
- Cửa chỉ hoạt động từ phòng của nó (`DoorComponent.roomId`), nên 2 cửa của một lối đi không tranh nhau. Vận tốc được
  giữ qua cửa. Điểm xuất hiện luôn nằm ngoài vùng kích hoạt cửa của phòng đích (map tool kiểm tra), nên không bị đá
  qua lại. Player rời giới hạn phòng mà không qua cửa (hồi sinh ở checkpoint phòng khác) → phát `RELOCATED`.
- Loại cửa: `SIDE` (lối đi, hai chiều), `LADDER` (giếng thang, hai chiều, xuất hiện trên thang), `DROP` (hố rơi, một chiều).
- Thêm nơi nghe (nhạc theo phòng, đánh thức quái…): `events.subscribe(RoomTransitionEvent.class, listener)` trong
  `GameScreen`; không cần sửa code cửa.

## 10. Màn hình, menu và lưu game

**Điều hướng:** `MyGame` cài đặt `Navigator` (`startNewGame`, `continueGame`, `returnToMainMenu`, `quit`); chuyển màn hình
sau khung hình hiện tại. `MenuScreen` dựng các trang từ file lưu mỗi lần hiện ra. `PauseMenu` báo lựa chọn qua
`PauseMenu.Actions` (do `GameScreen` cài đặt).

**Giao diện:** `ui.menu.MenuView` vẽ ngăn xếp trang `Menu` thành khung terminal, dàn trang trên màn thiết kế 1280 × 720
rồi phóng theo cửa sổ. `UiTheme` giữ bảng màu (chép từ file thiết kế `build_asset/build_ui/menu-preview.template.html`),
font FreeType dựng theo tỉ lệ cửa sổ (`assets/ui/fonts/`: Silkscreen, Pixelify Sans) và texture sọc cảnh báo.

**Chế độ:** `GameMode` = `EASY` (`Constants.EASY_MAP_PATH`), `MEDIUM` (`MEDIUM_MAP_PATH`), `HARD` (`HARD_MAP_PATH`); mỗi chế độ một map riêng. `GameMode.enemySkill` quyết định con số của quái, `GameMode.tacticPolicy` quyết định cách quái đánh theo vùng.

**File lưu** (`SaveData`, JSON, `~/.mygame/saves/<chế độ>.json`, schema phiên bản 1):

| Nhóm | Trường |
|------|--------|
| Nhận dạng | `version`, `mode`, `mapPath`, `mapSignature` (kích thước lưới / số phòng / số checkpoint), `savedAt`, `playTime`, `reason` (`CHECKPOINT` / `QUIT` / `COMPLETE`), `isComplete` |
| Vị trí | `x`, `y` (chân), `respawnX`, `respawnY`, `checkpointIndex`, `checkpointCount` |
| Player | `health`, `maxHealth`, `weaponSlot`, `weaponIds[]`, `ammo[]` (cùng thứ tự) |
| Khám phá | `visitedRooms[]`, `knownRooms[]`, `roomCount`, `roomId` |

- **Chụp** (`SaveSnapshot.capture`): lưu ở checkpoint, hoặc khi player đang chết / đang qua cửa → vị trí = điểm hồi sinh;
  ngược lại vị trí = chỗ đang đứng. Player đang chết thì lưu đầy máu.
- **Áp** (`SaveSnapshot.apply`): chữ ký map khớp → đặt lại vị trí, điểm hồi sinh, minimap; luôn đặt máu
  (`setHealthClamped`), đạn theo id vũ khí, yêu cầu ô vũ khí (đổi ở update đầu tiên). Chữ ký khác → chỉ giữ máu và vũ khí.
- **Ô lưu** (`SaveManager`): đọc không bao giờ ném lỗi; `EMPTY` (không có file), `OK`, `CORRUPT` (không đọc được),
  `INCOMPATIBLE` (phiên bản mới hơn, chế độ lạ, map không còn). Ghi vào file tạm rồi đổi tên, nên lỗi giữa chừng
  không làm mất file cũ. `latestContinuable()` = ô OK chưa hoàn thành, lưu gần nhất.
- **Cài đặt** (`Settings`, `~/.mygame/settings.json`): `isFullscreen`, `isVsync`, `isFpsVisible`; file hỏng thì dùng mặc định.
