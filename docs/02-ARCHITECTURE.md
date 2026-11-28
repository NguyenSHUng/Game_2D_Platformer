# 02 - Kiến trúc

[← Mục lục](README.md)

Game được ghép thế nào, các phần chạy theo thứ tự nào và trao đổi dữ liệu ra sao. Danh sách từng file kèm vai trò:
[01-SOURCE-MAP.md](01-SOURCE-MAP.md). Trang này chỉ nhắc tới class có thật trong code.

## 1. Thiết kế

Kiến trúc **component-based kiểu ECS**, cộng một **cây kế thừa cho nhân vật** (player và quái):

| Thành phần | Vai trò |
|------------|---------|
| `Entity` | Một túi component: luôn có `TransformComponent`, các component khác tuỳ chọn (null = không có) |
| Cây nhân vật | `GameCharacter` → `Player`, `Enemy` → các loại quái ([mục 4](#4-entity-component-và-cây-nhân-vật)). Mỗi loại quái là một class, tự giữ con số và hành vi săn đuổi của mình |
| Component | Dữ liệu. Phần lớn để field public cho system đọc ghi mỗi khung hình; chỉ đóng gói khi có quy tắc cần giữ ([mục 4](#4-entity-component-và-cây-nhân-vật)) |
| System | Hành vi. Mỗi system duyệt `World.getEntities()` và bỏ qua entity thiếu component nó cần |
| Factory | `PlayerFactory` (tạo `Player`), `EntityFactory` (cửa), `EnemyFactory` (chọn lớp quái theo file), `WeaponFactory` lắp component từ file JSON |

Component giữ dữ liệu, system chạy theo thứ tự cố định; class nhân vật thêm phần **khác nhau giữa các loại** (phe,
cách săn, đòn kế tiếp, cách bay). Bốn tính chất OOP được áp dụng ở đâu: [mục 12](#12-bốn-tính-chất-oop).

## 2. Vòng đời ứng dụng

```text
Lwjgl3Launcher ─► MyGame.create(): SpriteBatch, ResourceLoader, SaveManager, Settings, UiTheme
                  └─► MenuScreen (màn hình chính)
                        │ New Game / Continue  (Navigator)
                        ▼
                  MyGame.startRun(GameSession): WorldLoader.load(map) → GameScreen (áp file lưu nếu Continue)
                        │ Esc → "Save and exit to menu" (Navigator)
                        ▼
                  MyGame.endRun(): huỷ GameScreen, gỡ map khỏi ResourceLoader → MenuScreen
```

- Mọi lần chuyển màn hình đi qua `Gdx.app.postRunnable`, nên màn hình đang vẽ luôn vẽ xong khung hình hiện tại.
- `MyGame` giữ các dịch vụ sống suốt chương trình (batch, tài nguyên, save, settings, UI theme). Một ván chơi có
  `World`, các system và `EventBus` riêng; tất cả bị huỷ cùng lúc khi ván kết thúc.
- Map được gỡ khỏi bộ nạp sau mỗi ván vì `AttackSystem` sửa trực tiếp TiledMap khi phá tường; ván sau nạp lại bản gốc.

## 3. Thứ tự một khung hình

`GameScreen.render()` chạy theo thứ tự sau. Bước 1–15 nằm trong `GameScreen.update()` và bị bỏ qua khi bảng nhân
vật hoặc menu tạm dừng đang mở, hoặc khi màn hình đang tối dần qua cửa.

| # | Bước | Việc làm |
|---|------|----------|
| 1 | `InputSystem` | Ý định của player → vận tốc, nhảy, ngồi, thang; yêu cầu đánh / bắn / nạp / đổi vũ khí / tương tác |
| 2 | `WeaponSwitchSystem` | Thực hiện yêu cầu đổi vũ khí, đổi bộ animation theo tư thế cầm |
| 3 | `PatrolSystem` | Quái đi tuần |
| 4 | `EnemyBrainSystem` | Gọi `Enemy.updateBehaviour` của từng quái: mỗi loại tự săn, đánh, bay (ghi đè vận tốc tuần tra) |
| 5 | `PhysicsSystem` | Trọng lực, di chuyển; entity có hộp va chạm đi qua `CollisionSystem` |
| 6 | `HazardSystem` | Checkpoint, bẫy, rơi khỏi map, lối ra; phát `CheckpointEvent`, `ExitReachedEvent` |
| 7 | `ChestSystem` | Mở rương theo yêu cầu tương tác, ném vật phẩm ra; phát `ChestOpenedEvent` |
| 8 | `PickupSystem` | Vật phẩm bay / nảy / nằm yên; player chạm thì nhặt; phát `PickupCollectedEvent` |
| 9 | `ContactDamageSystem` | Chạm quái thì mất máu |
| 10 | `AttackSystem` | Đòn cận chiến theo attack box của khung hiện tại; phá tile BREAKABLE |
| 11 | `ShootingSystem` | Bắn, hồi chiêu, nạp đạn từ dự trữ, lửa nòng, vỏ đạn |
| 12 | `ProjectileSystem` | Đạn bay, trúng tile hoặc thân; biến mất khi rời phòng lúc bắn |
| 13 | `HealthSystem` | Bộ đếm bị thương / bất tử, chết (phát `EnemyKilledEvent`), hồi sinh player |
| 14 | `AnimationSystem` | Chọn animation cho từng entity, tăng thời gian |
| 15 | `EffectSystem` | Chạy và xoá entity hiệu ứng |
| 16 | `DoorSystem.update` | Chuyển phòng; chạy cả khi thế giới đang dừng vì màn hình tối dần |
| 17 | Vẽ | Map + entity → ánh sáng → lớp debug → lớp tối qua cửa → HUD → minimap → bảng nhân vật → toast → menu tạm dừng → FPS |

**Các ràng buộc thứ tự mà code dựa vào** (chỉ đổi khi có test chứng minh):
- Yêu cầu đặt ở bước 1 và 4 được tiêu thụ ở bước 2, 7, 10, 11.
- `EnemyBrainSystem` chạy sau `PatrolSystem` để ghi đè được vận tốc tuần tra.
- `ChestSystem` và `PickupSystem` chạy sau `PhysicsSystem`, nên dùng vị trí player của khung này; `ChestSystem` trước
  `PickupSystem` để vật phẩm vừa ném bắt đầu bay ngay khung đó.
- `AnimationSystem` chạy sau `HealthSystem` và `AttackSystem` để HIT, DEATH, ATTACK được ưu tiên đúng.

## 6. Bảng system

| System | Package | Đọc | Ghi |
|--------|---------|-----|-----|
| InputSystem | input | Controller, Movement, Collider, Health, lưới tile | Transform, Movement, yêu cầu Attack / Weapon / Arsenal / Interact |
| WeaponSwitchSystem | combat.system | Arsenal, Attack, Health | Weapon, Arsenal, tư thế animation |
| PatrolSystem | ai | Patrol, Movement, Collider, Health, lưới tile | Transform, Patrol |
| EnemyBrainSystem | ai | Health của player; mỗi `Enemy` tự đọc Movement, Collider, Attack, lưới tile | Transform, yêu cầu Attack (do từng `Enemy` ghi) |
| PhysicsSystem | physics | Movement, thông số vật lý của level | Transform (qua CollisionSystem) |
| CollisionSystem | physics | Collider, lưới tile | Transform, Movement (chạm đất, bị chặn) |
| HazardSystem | physics | Collider, lưới tile, checkpoint, lối ra | Điểm hồi sinh, vị trí player; phát sự kiện |
| ChestSystem | world.system | Interact, Health / Collider của player, Chest, lưới tile | Chest, Sprite, Interact; thêm entity vật phẩm; phát sự kiện |
| PickupSystem | combat.system | Pickup, Collider | Pickup, Transform, Health / dự trữ của player (qua `Pickups`); xoá vật phẩm đã nhặt, thêm hiệu ứng; phát sự kiện |
| ContactDamageSystem | combat.system | Collider, Health, `Enemy.getContactDamage()` | Health (qua Damage) |
| AttackSystem | combat.system | Attack, Animation, Health (attack box qua Hitboxes), phe (`GameCharacter.areHostile`), `canBreakWalls()` | Health (qua Damage), lưới tile + map |
| ShootingSystem | combat.system | Weapon, Movement, Health | Weapon; thêm entity đạn và hiệu ứng |
| ProjectileSystem | combat.system | Projectile, Collider, lưới tile, phe | Health (qua Damage); xoá đạn |
| HealthSystem | combat.system | Health, Animation | Bộ đếm của Health, Transform; xoá quái chết, hồi sinh player; phát `EnemyKilledEvent` |
| AnimationSystem | animation.system | Transform, Movement, Health, Attack, Weapon | Animation (loại, thời gian) |
| EffectSystem | animation.system | Sprite, Effect | xoá hiệu ứng đã xong |
| DoorSystem | world.system | Door, thân player | RoomTransition; phát `RoomTransitionEvent` |
| RenderingSystem | rendering | map, Transform, Animation, Weapon, Sprite, Pickup (+ `ItemArt`) | màn hình |

System không gọi nhau. Chúng dùng chung vài tiện ích tĩnh: `Damage.apply` (một đòn đánh), `Pickups.apply` (một vật phẩm), `LootDrop`, `PlayerRespawn.respawn`,
`Effects.spawn`, `Hitboxes` (hộp va chạm trong toạ độ thế giới), `AnimationFrames` (khung hiện tại).
`PhysicsSystem` sở hữu `CollisionSystem`.

## 7. Giao tiếp giữa các phần

| Cách | Khi nào dùng | Ví dụ |
|------|--------------|-------|
| Dữ liệu trong component (mặc định) | Một system để lại yêu cầu hay kết quả, system sau xử lý | `attack.isRequested`, `weapon.isFireRequested`, `arsenal.requestedSlot`, `movement.isGrounded` |
| `EventBus` (`com.hung.core.event`) | Một nơi phát cần tới nhiều nơi nghe mà không nên biết nhau | Bảng dưới |
| `Navigator` / `PauseMenu.Actions` | Màn hình và menu báo lựa chọn lên trên | `MyGame` chuyển màn hình; `GameScreen` xử lý lựa chọn tạm dừng |

| Sự kiện (`com.hung.world.event`) | Nơi phát | Nơi nghe |
|----------------------------------|----------|----------|
| `RoomTransitionEvent` | `DoorSystem` | `CameraController` (giới hạn trong phòng), `MinimapState` (đánh dấu phòng) |
| `CheckpointEvent` | `HazardSystem` | `GameScreen` (lưu game, hiện toast) |
| `ExitReachedEvent` | `HazardSystem` | `ScoreKeeper` (điểm hoàn thành), rồi `GameScreen` (đánh dấu ván hoàn thành, lưu, mở màn kết thúc) |
| `EnemyKilledEvent` | `HealthSystem` | `ScoreKeeper` (điểm hạ quái, kiểm tra dọn phòng) |
| `ChestOpenedEvent` | `ChestSystem` | `ScoreKeeper` (điểm rương) |
| `RoomClearedEvent` | `ScoreKeeper` | `GameScreen` (toast) |
| `PickupCollectedEvent` | `PickupSystem` | `GameScreen` (toast "+25 HP"…) |

- Nơi phát dùng lại một đối tượng sự kiện, nên nơi nghe phải chép những gì cần giữ.
- Listener được gọi theo thứ tự đăng ký: `ScoreKeeper` đăng ký trước các listener lưu game / mở màn kết thúc.
- Mỗi `GameScreen` tạo `EventBus` riêng và đăng ký mọi listener trong constructor; bus và listener bị huỷ cùng màn
  hình, nên không cần gỡ đăng ký.
- `DoorSystem.start()` thông báo phòng đầu tiên và phải chạy sau khi đăng ký listener (`GameScreen.show()`).

## 8. Lưu game

| Class | Trách nhiệm |
|-------|-------------|
| `SaveData` | Cấu trúc JSON ghi vào file lưu của chế độ (schema phiên bản 2; đọc được phiên bản 1), chỉ có field dữ liệu |
| `SaveSnapshot` | `capture(...)` chụp thế giới thành `SaveData`; `apply(...)` đặt `SaveData` vào thế giới vừa nạp. Không đọc ghi file, không cần LibGDX nên test được |
| `SaveManager` | Mỗi chế độ một file trong `~/.mygame/saves/`: đọc (EMPTY / OK / CORRUPT / INCOMPATIBLE, không bao giờ ném lỗi), ghi qua file tạm, nhập save từ ô lưu cũ |
| `GameSession` | Ván đang chơi: chế độ, thời gian chơi, điểm (`RunStats`), file lưu đang tiếp tục |
| `RunStats` / `ScoreKeeper` / `ScoreRules` (`com.hung.score`) | Điểm, thống kê và id đã tính điểm của ván; biến sự kiện thành điểm, mỗi id một lần; bảng điểm |
| `Settings` | Cài đặt trong `~/.mygame/settings.json` |

`GameScreen` lưu khi có `CheckpointEvent`, khi tới lối ra và khi người chơi rời ván (về menu, thoát, đóng hoặc thu nhỏ
cửa sổ). Chi tiết dữ liệu được lưu: [05-SYSTEMS-GUIDE.md → 10](05-SYSTEMS-GUIDE.md#10-màn-hình-menu-và-lưu-game).

## 9. Package

```text
core/                MyGame, GameScreen, GameMode, Navigator
  event/             EventBus, EventListener (hạ tầng, không phụ thuộc gì)
world/               World, Level, Room, RoomTransition, TileGrid, TileType, PhysicsSettings, EnemySpawn, LightSource
  event/             RoomTransitionEvent, CheckpointEvent, ExitReachedEvent, EnemyKilledEvent, ChestOpenedEvent,
                     RoomClearedEvent, PickupCollectedEvent
  loader/            WorldLoader, RoomLoader (chạy lúc nạp map)
  system/            DoorSystem, ChestSystem, ChestReach, PlayerRespawn (hành vi theo khung hình)
entity/              Entity, GameCharacter, EntityFactory (cửa)
  player/            Player, PlayerFactory, CharacterDefinition
  enemy/             Enemy, HunterEnemy, MeleeEnemy, CrawlerEnemy, FlyingEnemy, EnemyFactory, EnemyDefinition,
                     EnemySkill, JumpPlanner
component/           Transform, Movement, Collider, Animation, Health, Attack, Weapon, Arsenal, Patrol,
                     Sprite, Effect, Projectile, Door, Pickup, Interact, Chest
input/               Controller, KeyboardController, InputSystem, CameraInput
ai/                  PatrolSystem, EnemyBrainSystem
  tactic/            HuntTactic, HuntContext, TacticActions, DirectChaseTactic, HitAndRunTactic, TacticPolicy
                     (lá: không phụ thuộc package nào khác của game)
physics/             PhysicsSystem, CollisionSystem, HazardSystem, Hitboxes
combat/              Damage, Effects, Weapon, WeaponEffect, WeaponFactory (dùng chung);
                     AmmoType, PickupType, Pickups, ChestTier, LootTable, LootDrop, ItemArt (vật phẩm và rương)
  definition/        WeaponDefinition, FireDefinition, EffectDefinition, AttachmentProfile, ItemArtDefinition (JSON)
  system/            AttackSystem, ContactDamageSystem, ShootingSystem, ProjectileSystem, HealthSystem, WeaponSwitchSystem,
                     PickupSystem
animation/           AnimationType, AnimationSet, AnimationFrames, HandAnchor (kiểu dùng chung)
  definition/        AnimationDefinition, FrameDefinition (JSON của player)
  system/            AnimationSystem, EffectSystem
rendering/           RenderingSystem, CameraController, LightingRenderer, HudRenderer, InteractionPrompt, StatsPanel, DebugRenderer, FadeRenderer
ui/                  UiTheme, Toast, FpsOverlay
  menu/              Menu, MenuView, MenuScreen, PauseMenu, SettingsMenu
  minimap/           MinimapState, MinimapRenderer
save/                SaveData, SaveSnapshot, SaveManager, Settings, GameSession
score/               ScoreRules, RunStats, ScoreKeeper (điểm của ván)
util/                Constants, GameLog, ResourceLoader, SmoothDamp
```

Package gốc của một domain chứa phần dùng chung; package con phụ thuộc package gốc, không theo chiều ngược lại.
`entity` tách `entity.player` và `entity.enemy` (mỗi bên: lớp nhân vật, factory, definition). `rendering`, `save`, `component`, `input`, `ai`, `physics`, `util` để phẳng vì chia ra sẽ chỉ còn 1–2 class
mỗi package.

## 10. Quy tắc kiến trúc

1. **Entity là túi chứa, nhân vật là lớp con.** Khả năng dùng chung (máu, va chạm, animation) là component + system.
   Điểm khác nhau giữa các loại nhân vật là method override trong cây `GameCharacter`; không tạo lớp con cho cửa, đạn,
   hiệu ứng.
2. **System không gọi system.** Chia sẻ qua component; quy tắc dùng chung đặt trong tiện ích tĩnh
   (`Damage.apply(target, amount, sourceX)` thay vì tự trừ máu); chỉ phát sự kiện khi nơi nhận phải độc lập với nơi gửi.
3. **Đổi trạng thái được đóng gói qua API của nó** (`HealthComponent`, `AnimationComponent.useStance`,
   `World.setRespawnPoint`).
4. **Ý định của player lấy từ `Controller`.** Không đọc phím cho hành động gameplay ở nơi khác.
5. **Giữ thứ tự khung hình** ([mục 3](#3-thứ-tự-một-khung-hình)) trừ khi có test chứng minh cần đổi.
6. **Nạp tài nguyên trước**, không nạp trong `update()` / `render()`; không cấp phát trong code chạy mỗi khung hình.
7. **Dữ liệu từ file.** Map, nhân vật, quái, vũ khí là JSON / TMX do tool trong `build_asset/` sinh ra; giữ ổn định
   schema của chúng và schema file lưu.

## 11. Nợ kỹ thuật đã biết

| Vấn đề | Chi tiết |
|--------|----------|
| Vòng phụ thuộc giữa package (nhóm gameplay) | `component` ↔ `combat` (`WeaponComponent` → `Weapon`; component hiệu ứng và đạn → `WeaponEffect`), `component` ↔ `entity` (chủ của viên đạn), `component` ↔ `animation`, `entity` ↔ `input` (`Entity` giữ `Controller`), `entity.player` / `entity.enemy` → `world` → `entity` → … (`World` chứa entity; factory đọc `PhysicsSettings`, `EnemySpawn`), `physics` ↔ `world.system` (`HazardSystem` → `PlayerRespawn`, `DoorSystem` → `Hitboxes`) |
| Vòng phụ thuộc (nhóm màn hình) | `core` ↔ `save`, `core` ↔ `ui.menu` (qua `GameMode`, `Navigator`) |
| Phụ thuộc đáng lưu ý | `save.SaveSnapshot` đọc `ui.minimap.MinimapState`; `input.CameraInput` điều khiển `rendering.CameraController` |
| Phá tường | `AttackSystem` sửa TiledMap đang cache; `MyGame` gỡ map sau mỗi ván. Tường đã phá và quái đã giết không được lưu |
| Số liệu quái | Tầm phát hiện, hồi chiêu, tốc độ đuổi là hằng số trong từng class quái; máu và sát thương là hằng số theo mức LOW / MEDIUM trong `EnemyFactory`, nhân với `EnemySkill` của chế độ. Thông số HitAndRun (1,8 tầm đánh, 1 s, 0,7 tốc độ) và vùng bắt đầu là hằng số |
| `HuntContext` mỗi khung | Một record mới mỗi khung cho mỗi quái đang săn (ngược quy tắc 6, chấp nhận vì nhỏ và sống ngắn) |
| `HunterEnemy.approach()` / `retreat()` | Cần world của `think()` đang chạy (`thinkingWorld`); gọi ngoài `think()` là lỗi. Là method public vì thuộc interface |
| `World.getPlayer()` | Trả về `Entity`, không phải `Player`, để các test dựng thế giới bằng entity thường |
| Bảng màu UI | Có ở 2 nơi: file thiết kế `build_asset/build_ui/menu-preview.template.html` và `UiTheme.java`; đổi phải chép tay |
| Class lớn | `MenuView` (518 dòng), `StatsPanel` (448, quá mức 400), `HunterEnemy` (378, gần mức 400), `MenuScreen` (337), `WorldLoader` (310) |

## 12. Bốn tính chất OOP

| Tính chất | Áp dụng ở đâu |
|-----------|---------------|
| **Kế thừa** | Cây nhân vật ở [mục 4](#4-entity-component-và-cây-nhân-vật): `Player` và `Enemy` kế thừa `GameCharacter`; `MeleeEnemy`, `CrawlerEnemy`, `FlyingEnemy` kế thừa `HunterEnemy`, tức là dùng lại toàn bộ logic săn đuổi và chỉ đổi phần khác. Kế thừa từ LibGDX: `MyGame extends Game`, `GameScreen` / `MenuScreen extends ScreenAdapter`, `CameraInput extends InputAdapter` |
| **Đa hình** | `EnemyBrainSystem` gọi `enemy.updateBehaviour(...)` mà không biết loại quái: crawler dừng ở mép vực, flyer bay qua. `HunterEnemy` gọi `tactic.decide(...)` mà không biết tactic nào (Strategy): cùng một con melee, map Medium vùng 3–4 đánh rồi lùi, chỗ khác đuổi thẳng; `approach()` / `retreat()` lại đa hình theo lớp quái (`chase()`, `canBackOff()`). `AttackSystem` / `ProjectileSystem` hỏi `GameCharacter.areHostile(a, b)` (dựa trên `getFaction()` override) và `canBreakWalls()`. `ContactDamageSystem` hỏi `getContactDamage()`, `EnemyFactory` hỏi `getKnockbackResistance()`. Qua interface: `Controller`, `EventListener<T>`, `Navigator`, `PauseMenu.Actions`, `Disposable` |
| **Trừu tượng** | Lớp trừu tượng `GameCharacter` (`getFaction()`), `Enemy` (`think()`), `HunterEnemy` (`aggroRange()`, `attackCooldown()`, `chaseSpeedScale()`). Interface `HuntTactic` / `TacticActions`: tactic chỉ thấy `HuntContext` và 5 hành động, không thấy entity, component hay world. Template method `Enemy.updateBehaviour` cố định thứ tự các bước, lớp con chỉ điền bước. Ngoài ra: interface `Controller`, `Navigator`; `Damage.apply`, `Hitboxes`, `WorldLoader.load`, `SaveSnapshot` giấu chi tiết sau một lời gọi |
| **Đóng gói** | Trạng thái săn đuổi trong `HunterEnemy` (`cooldown`, `memory`, `isEngaged`, `isJumping`), `HitAndRunTactic` (`state`, `retreatTime`) và `FlyingEnemy` (`homeFloorY`, `hasCrashed`) là private; con số của từng loại là hằng số private trong chính class đó. `HealthComponent`, `AnimationComponent.useStance`, `World`, `Level`, `TileGrid`, `Weapon` (chỉ getter), `SaveManager` |

Test của cây nhân vật: `core/src/test/java/com/hung/entity/enemy/EnemyHierarchyTest.java` (phe, phá tường, flyer đánh trên không, săn rồi chờ hồi chiêu, melee đuổi, đánh, giữ khoảng cách và dừng ở mép vực, quái chết / bị thương không nghĩ) và `EnemySkillTest.java` (nhớ, bị bắn từ xa, gọi bầy, nhảy, đón đầu, không đánh với).

Test của độ khó và AI quái (cùng package, trừ `ai/tactic`):

| Test | Bảo vệ |
|------|--------|
| `EnemyStatScalingTest`, `HunterScalingTest` | Bảng golden của chỉ số: `EnemySkill` từng chế độ, máu / sát thương theo mức, chạm, kháng đẩy lùi, tốc độ đi / đuổi, trần 84, hồi chiêu, tầm phát hiện; cả 5 quái thật từ `character.json` |
| `EnemyRoomTest` | Phòng nhà (thấy, gọi bầy, không bước ra, đi vào lại, tuần tra sau nhảy), khoảng dừng của crawler / ghoul / wasp, `Level.findRoom` |
| `DirectChaseParityTest` | 45 kịch bản × mọi khung (vị trí, vận tốc, hướng, yêu cầu đòn, hồi chiêu, engaged) so với baseline ghi trước khi tách Strategy (`src/test/resources/parity/direct_chase_baseline.txt`; xoá file thì test ghi lại và cố ý fail một lần) |
| `ai/tactic/DirectChaseTacticTest`, `HitAndRunTacticTest`, `TacticPolicyTest` | Tactic thuần Java (không quái, không LibGDX): thứ tự hành động, từng chuyển trạng thái, ma trận vùng, instance riêng |
| `HitAndRunEnemyTest` | HitAndRun trên quái thật: lùi dưới trần, không lùi xuống vực / gai / ra khỏi phòng (kể cả tactic "ẩu"), quái bay, bị đánh, cả trận |
| `core/GameModeTacticTest` | Chế độ → policy; số quái HitAndRun trên map thật (Easy 0, Medium 30, Hard 0), mọi quái có vùng |
| `MediumMapPlaytestTest` | Playtest kịch bản trên map Medium thật (địa hình, phòng, quái): không quái nào rời phòng, đứng trên hazard, rơi khỏi map; số liệu so với đuổi thẳng ghi ra `core/build/playtest/medium.txt` |
