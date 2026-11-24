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

`GameScreen.render()` chạy theo thứ tự sau. Bước 1–13 nằm trong `GameScreen.update()` và bị bỏ qua khi bảng nhân
vật hoặc menu tạm dừng đang mở, hoặc khi màn hình đang tối dần qua cửa.

| # | Bước | Việc làm |
|---|------|----------|
| 1 | `InputSystem` | Ý định của player → vận tốc, nhảy, ngồi, thang; yêu cầu đánh / bắn / nạp / đổi vũ khí |
| 2 | `WeaponSwitchSystem` | Thực hiện yêu cầu đổi vũ khí, đổi bộ animation theo tư thế cầm |
| 3 | `PatrolSystem` | Quái đi tuần |
| 4 | `EnemyBrainSystem` | Gọi `Enemy.updateBehaviour` của từng quái: mỗi loại tự săn, đánh, bay (ghi đè vận tốc tuần tra) |
| 5 | `PhysicsSystem` | Trọng lực, di chuyển; entity có hộp va chạm đi qua `CollisionSystem` |
| 6 | `HazardSystem` | Checkpoint, bẫy, rơi khỏi map, lối ra; phát `CheckpointEvent`, `ExitReachedEvent` |
| 7 | `ContactDamageSystem` | Chạm quái thì mất máu |
| 8 | `AttackSystem` | Đòn cận chiến theo attack box của khung hiện tại; phá tile BREAKABLE |
| 9 | `ShootingSystem` | Bắn, hồi chiêu, nạp đạn, lửa nòng, vỏ đạn |
| 10 | `ProjectileSystem` | Đạn bay, trúng tile hoặc thân |
| 11 | `HealthSystem` | Bộ đếm bị thương / bất tử, chết, hồi sinh player |
| 12 | `AnimationSystem` | Chọn animation cho từng entity, tăng thời gian |
| 13 | `EffectSystem` | Chạy và xoá entity hiệu ứng |
| 14 | `DoorSystem.update` | Chuyển phòng; chạy cả khi thế giới đang dừng vì màn hình tối dần |
| 15 | Vẽ | Map + entity → ánh sáng → lớp debug → lớp tối qua cửa → HUD → minimap → bảng nhân vật → toast → menu tạm dừng → FPS |

**Các ràng buộc thứ tự mà code dựa vào** (chỉ đổi khi có test chứng minh):
- Yêu cầu đặt ở bước 1 và 4 được tiêu thụ ở bước 2, 8, 9.
- `EnemyBrainSystem` chạy sau `PatrolSystem` để ghi đè được vận tốc tuần tra.
- `AnimationSystem` chạy sau `HealthSystem` và `AttackSystem` để HIT, DEATH, ATTACK được ưu tiên đúng.

## 4. Entity, component và cây nhân vật

```java
Enemy crawler = new CrawlerEnemy(attackRange);   // là Entity: đã có TransformComponent
crawler.setMovement(movement);
crawler.setHealth(new HealthComponent(20));    // EnemyFactory lắp các component này từ character.json
```

`Entity` có một field có kiểu cho mỗi loại component (getter + setter, null = không có). Thêm loại component nghĩa là
thêm một field; cách này được giữ có chủ ý: có kiểu rõ ràng, nhanh, dễ đọc ở quy mô hiện tại.

`Entity` không có constructor riêng. 14 field của nó chia ba nhóm:

| Nhóm | Field |
|------|-------|
| Component bắt buộc | `TransformComponent` (`final`, tạo sẵn, không có setter) |
| Component tuỳ chọn | `MovementComponent`, `ColliderComponent`, `AnimationComponent`, `WeaponComponent`, `PatrolComponent`, `AttackComponent`, `HealthComponent`, `ArsenalComponent`, `SpriteComponent`, `EffectComponent`, `ProjectileComponent`, `DoorComponent` |
| Tham chiếu không phải component | `Controller`: interface ở `com.hung.input` (strategy nhập lệnh), chỉ player có |

`Weapon` cũng không phải component và `Entity` không giữ nó trực tiếp: đó là đối tượng của `com.hung.combat`, nằm trong `WeaponComponent.equipped`.

Entity nào được lắp component nào (ngoài `TransformComponent`):

| Entity | Component | Nơi lắp |
|--------|-----------|---------|
| `Player` | `MovementComponent`, `ColliderComponent`, `AttackComponent`, `HealthComponent`, `AnimationComponent`; có vũ khí thì thêm `ArsenalComponent`, `WeaponComponent`. Kèm tham chiếu `Controller` | `PlayerFactory.createPlayer`, `WeaponFactory.equipArsenal` |
| `Enemy` | `MovementComponent`, `ColliderComponent`, `PatrolComponent`, `HealthComponent`, `AnimationComponent`, `AttackComponent` | `EnemyFactory.createEnemy` |
| Cửa | `DoorComponent` | `EntityFactory.createDoor` |
| Đạn | `ProjectileComponent`; `SpriteComponent` nếu súng có hiệu ứng `tracer` | `ShootingSystem` |
| Vỏ đạn | `SpriteComponent`, `MovementComponent`, `ColliderComponent`, `EffectComponent` | `ShootingSystem` |
| Hiệu ứng | `SpriteComponent`, `EffectComponent` | `Effects.spawn` |

**Dữ liệu chung và đối tượng riêng của quái:**

| Class | Vai trò |
|-------|---------|
| `EnemyDefinition` | Cấu hình đọc từ `character.json`, dùng chung cho các con cùng loại trong một map. Field public nhưng chỉ đọc: sau khi nạp JSON không code nào ghi vào |
| `Enemy` (lớp con) | Đối tượng lúc chạy, riêng từng con: vị trí, máu, hồi chiêu, animation. Không giữ tham chiếu tới definition |
| `EnemyFactory` | Đọc definition + `EnemySpawn`, chọn lớp con, tạo component mới cho từng con. Chỉ ảnh (`Texture`) là dùng chung |

**Cây nhân vật** (`GameCharacter` ở `com.hung.entity`; `Player` và `PlayerFactory` ở `com.hung.entity.player`; các lớp quái và `EnemyFactory` ở `com.hung.entity.enemy`):

```text
Entity                         túi component (cửa, đạn, hiệu ứng là Entity thường)
 └─ GameCharacter «abstract»   getFaction() «abstract», isHostileTo(), canBreakWalls(), isAlive()
     ├─ Player                 phe PLAYER, phá được tường
     └─ Enemy «abstract»       phe ENEMY, getContactDamage(), getKnockbackResistance()
         │                     updateBehaviour() = template: chết → whileDead · bị thương → whileHurt · còn lại → think()
         └─ HunterEnemy «abstract»   think() = phát hiện → đuổi → đánh; aggroRange(), attackCooldown(), chaseSpeedScale() «abstract»
             ├─ MeleeEnemy     small_melee_*: 120 / 1,2 s / ×2,2, dừng cách một tầm vuốt
             ├─ CrawlerEnemy   crawler_01: 160 / 0,9 s / ×1,6
             └─ FlyingEnemy    flyer_wasp_01: bay, không xét mép vực, đánh trên không, xác rơi sau DEATH
```

`EnemyFactory.newEnemy` chọn lớp theo `character.json`:
`locomotion: AIR` → `FlyingEnemy`; `archetype: SMALL_MELEE` → `MeleeEnemy`; còn lại → `CrawlerEnemy`.

**Component được đóng gói** (có quy tắc trải trên nhiều field):

| Component | Quy tắc luôn đúng | API |
|-----------|-------------------|-----|
| `HealthComponent` | 0 ≤ health ≤ maxHealth; isDead ⇔ health == 0; maxHealth ≥ 1 | `new HealthComponent(max)`, `applyDamage(n)`, `restoreFull()`, `setHealthClamped(n)`, các getter |
| `AnimationComponent` | 5 map của tư thế đang dùng (animation, anchor tay, khung gốc, lớp tay trước, attack box) luôn thuộc cùng một tư thế | `useStance(tên)` đổi cả 5 cùng lúc; getter để đọc |
| `World` | Điểm hồi sinh chỉ đổi ở một chỗ | `setRespawnPoint(x, y)`; `getRespawnPoint()` chỉ để đọc |
| `HunterEnemy` (và lớp con) | Hồi chiêu, số đòn đã đánh, trạng thái đang săn chỉ do chính quái cập nhật | private; `isEngaged()` để đọc |

Các field còn lại (bộ đếm, vận tốc, cờ) để public: system cập nhật mỗi khung hình và không có quy tắc nào trải trên chúng.

## 5. Input

- `com.hung.input.Controller` là ý định của player: hướng di chuyển, nhảy, đánh (nhấn / giữ), nạp đạn, ô vũ khí, vũ
  khí kế tiếp. `KeyboardController` cài đặt bằng cách hỏi bàn phím và chuột ngay lúc được gọi, không giữ trạng thái
  giữa các khung hình. `InputSystem` chỉ làm việc qua interface.
- Mọi lần đọc ý định của player đều đi qua controller.
- Quái không có controller: `PatrolSystem` và các class quái (qua `EnemyBrainSystem`) ghi thẳng vào component của quái.

**Phím chọn vũ khí khi bảng nhân vật mở.** Lúc đó bước update (gồm `InputSystem`) không chạy, nên
`GameScreen.handleToggles()` tự đọc `controller.getWeaponSlotPressed()`, đặt `ArsenalComponent.requestedSlot` rồi chạy
`WeaponSwitchSystem`.
- Thường chỉ một trong hai đường đọc phím trong một khung hình. Ngoại lệ: Esc được xử lý **sau** `handleToggles()`.
  Nếu bấm Esc cùng một phím số trong cùng khung khi bảng đang mở, bảng đọc phím và đổi vũ khí, Esc đóng bảng, update
  chạy và `InputSystem` đọc lại phím đó. Lần đọc thứ hai yêu cầu đúng ô đang cầm, và `WeaponSwitchSystem` bỏ qua yêu
  cầu trùng (`requestedSlot != current`), nên không có gì thay đổi.
- Bấm nhiều phím số trong cùng một khung: `KeyboardController` trả về ô có số nhỏ nhất, ở cả lúc chơi lẫn khi bảng
  nhân vật mở.

## 6. Bảng system

| System | Package | Đọc | Ghi |
|--------|---------|-----|-----|
| InputSystem | input | Controller, Movement, Collider, Health, lưới tile | Transform, Movement, yêu cầu Attack / Weapon / Arsenal |
| WeaponSwitchSystem | combat.system | Arsenal, Attack, Health | Weapon, Arsenal, tư thế animation |
| PatrolSystem | ai | Patrol, Movement, Collider, Health, lưới tile | Transform, Patrol |
| EnemyBrainSystem | ai | Health của player; mỗi `Enemy` tự đọc Movement, Collider, Attack, lưới tile | Transform, yêu cầu Attack (do từng `Enemy` ghi) |
| PhysicsSystem | physics | Movement, thông số vật lý của level | Transform (qua CollisionSystem) |
| CollisionSystem | physics | Collider, lưới tile | Transform, Movement (chạm đất, bị chặn) |
| HazardSystem | physics | Collider, lưới tile, checkpoint, lối ra | Điểm hồi sinh, vị trí player; phát sự kiện |
| ContactDamageSystem | combat.system | Collider, Health, `Enemy.getContactDamage()` | Health (qua Damage) |
| AttackSystem | combat.system | Attack, Animation, Health (attack box qua Hitboxes), phe (`GameCharacter.areHostile`), `canBreakWalls()` | Health (qua Damage), lưới tile + map |
| ShootingSystem | combat.system | Weapon, Movement, Health | Weapon; thêm entity đạn và hiệu ứng |
| ProjectileSystem | combat.system | Projectile, Collider, lưới tile, phe | Health (qua Damage); xoá đạn |
| HealthSystem | combat.system | Health, Animation | Bộ đếm của Health, Transform; xoá quái chết, hồi sinh player |
| AnimationSystem | animation.system | Transform, Movement, Health, Attack, Weapon | Animation (loại, thời gian) |
| EffectSystem | animation.system | Sprite, Effect | xoá hiệu ứng đã xong |
| DoorSystem | world.system | Door, thân player | RoomTransition; phát `RoomTransitionEvent` |
| RenderingSystem | rendering | map, Transform, Animation, Weapon, Sprite | màn hình |

System không gọi nhau. Chúng dùng chung vài tiện ích tĩnh: `Damage.apply` (một đòn đánh), `PlayerRespawn.respawn`,
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
| `ExitReachedEvent` | `HazardSystem` | `GameScreen` (đánh dấu ván hoàn thành, mở màn kết thúc) |

- Nơi phát dùng lại một đối tượng sự kiện, nên nơi nghe phải chép những gì cần giữ.
- Mỗi `GameScreen` tạo `EventBus` riêng và đăng ký mọi listener trong constructor; bus và listener bị huỷ cùng màn
  hình, nên không cần gỡ đăng ký.
- `DoorSystem.start()` thông báo phòng đầu tiên và phải chạy sau khi đăng ký listener (`GameScreen.show()`).

## 8. Lưu game

| Class | Trách nhiệm |
|-------|-------------|
| `SaveData` | Cấu trúc JSON ghi vào file lưu của chế độ (schema phiên bản 1), chỉ có field dữ liệu |
| `SaveSnapshot` | `capture(...)` chụp thế giới thành `SaveData`; `apply(...)` đặt `SaveData` vào thế giới vừa nạp. Không đọc ghi file, không cần LibGDX nên test được |
| `SaveManager` | Mỗi chế độ một file trong `~/.mygame/saves/`: đọc (EMPTY / OK / CORRUPT / INCOMPATIBLE, không bao giờ ném lỗi), ghi qua file tạm, nhập save từ ô lưu cũ |
| `GameSession` | Ván đang chơi: chế độ, thời gian chơi, file lưu đang tiếp tục |
| `Settings` | Cài đặt trong `~/.mygame/settings.json` |

`GameScreen` lưu khi có `CheckpointEvent`, khi tới lối ra và khi người chơi rời ván (về menu, thoát, đóng hoặc thu nhỏ
cửa sổ). Chi tiết dữ liệu được lưu: [05-SYSTEMS-GUIDE.md → 10](05-SYSTEMS-GUIDE.md#10-màn-hình-menu-và-lưu-game).

## 9. Package

```text
core/                MyGame, GameScreen, GameMode, Navigator
  event/             EventBus, EventListener (hạ tầng, không phụ thuộc gì)
world/               World, Level, Room, RoomTransition, TileGrid, TileType, PhysicsSettings, EnemySpawn, LightSource
  event/             RoomTransitionEvent, CheckpointEvent, ExitReachedEvent
  loader/            WorldLoader, RoomLoader (chạy lúc nạp map)
  system/            DoorSystem, PlayerRespawn (hành vi theo khung hình)
entity/              Entity, GameCharacter, EntityFactory (cửa)
  player/            Player, PlayerFactory, CharacterDefinition
  enemy/             Enemy, HunterEnemy, MeleeEnemy, CrawlerEnemy, FlyingEnemy, EnemyFactory, EnemyDefinition,
                     EnemySkill, JumpPlanner
component/           Transform, Movement, Collider, Animation, Health, Attack, Weapon, Arsenal, Patrol,
                     Sprite, Effect, Projectile, Door
input/               Controller, KeyboardController, InputSystem, CameraInput
ai/                  PatrolSystem, EnemyBrainSystem
physics/             PhysicsSystem, CollisionSystem, HazardSystem, Hitboxes
combat/              Damage, Effects, Weapon, WeaponEffect, WeaponFactory (dùng chung)
  definition/        WeaponDefinition, FireDefinition, EffectDefinition, AttachmentProfile (JSON)
  system/            AttackSystem, ContactDamageSystem, ShootingSystem, ProjectileSystem, HealthSystem, WeaponSwitchSystem
animation/           AnimationType, AnimationSet, AnimationFrames, HandAnchor (kiểu dùng chung)
  definition/        AnimationDefinition, FrameDefinition (JSON của player)
  system/            AnimationSystem, EffectSystem
rendering/           RenderingSystem, CameraController, LightingRenderer, HudRenderer, StatsPanel, DebugRenderer, FadeRenderer
ui/                  UiTheme, Toast, FpsOverlay
  menu/              Menu, MenuView, MenuScreen, PauseMenu, SettingsMenu
  minimap/           MinimapState, MinimapRenderer
save/                SaveData, SaveSnapshot, SaveManager, Settings, GameSession
util/                Constants, ResourceLoader, SmoothDamp
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
| Số liệu quái | Tầm phát hiện, hồi chiêu, tốc độ đuổi là hằng số trong từng class quái; máu và sát thương là hằng số theo mức LOW / MEDIUM trong `EnemyFactory`, nhân với `EnemySkill` của chế độ |
| `World.getPlayer()` | Trả về `Entity`, không phải `Player`, để các test dựng thế giới bằng entity thường |
| Bảng màu UI | Có ở 2 nơi: file thiết kế `build_asset/build_ui/menu-preview.template.html` và `UiTheme.java`; đổi phải chép tay |
| Class lớn | `MenuView` (518 dòng), `StatsPanel` (340), `MenuScreen` (337), `WorldLoader` (301), `HunterEnemy` (265) |

## 12. Bốn tính chất OOP

| Tính chất | Áp dụng ở đâu |
|-----------|---------------|
| **Kế thừa** | Cây nhân vật ở [mục 4](#4-entity-component-và-cây-nhân-vật): `Player` và `Enemy` kế thừa `GameCharacter`; `MeleeEnemy`, `CrawlerEnemy`, `FlyingEnemy` kế thừa `HunterEnemy`, tức là dùng lại toàn bộ logic săn đuổi và chỉ đổi phần khác. Kế thừa từ LibGDX: `MyGame extends Game`, `GameScreen` / `MenuScreen extends ScreenAdapter`, `CameraInput extends InputAdapter` |
| **Đa hình** | `EnemyBrainSystem` gọi `enemy.updateBehaviour(...)` mà không biết loại quái: crawler dừng ở mép vực, flyer bay qua. `AttackSystem` / `ProjectileSystem` hỏi `GameCharacter.areHostile(a, b)` (dựa trên `getFaction()` override) và `canBreakWalls()`. `ContactDamageSystem` hỏi `getContactDamage()`, `EnemyFactory` hỏi `getKnockbackResistance()`. Qua interface: `Controller`, `EventListener<T>`, `Navigator`, `PauseMenu.Actions`, `Disposable` |
| **Trừu tượng** | Lớp trừu tượng `GameCharacter` (`getFaction()`), `Enemy` (`think()`), `HunterEnemy` (`aggroRange()`, `attackCooldown()`, `chaseSpeedScale()`). Template method `Enemy.updateBehaviour` cố định thứ tự các bước, lớp con chỉ điền bước. Ngoài ra: interface `Controller`, `Navigator`; `Damage.apply`, `Hitboxes`, `WorldLoader.load`, `SaveSnapshot` giấu chi tiết sau một lời gọi |
| **Đóng gói** | Trạng thái săn đuổi trong `HunterEnemy` (`cooldown`, `memory`, `isEngaged`, `isJumping`) và `FlyingEnemy` (`homeFloorY`, `hasCrashed`) là private; con số của từng loại là hằng số private trong chính class đó. `HealthComponent`, `AnimationComponent.useStance`, `World`, `Level`, `TileGrid`, `Weapon` (chỉ getter), `SaveManager` |

Test của cây nhân vật: `core/src/test/java/com/hung/entity/enemy/EnemyHierarchyTest.java` (phe, phá tường, flyer đánh trên không, săn rồi chờ hồi chiêu, melee đuổi, đánh, giữ khoảng cách và dừng ở mép vực, quái chết / bị thương không nghĩ) và `EnemySkillTest.java` (nhớ, bị bắn từ xa, gọi bầy, nhảy, đón đầu, không đánh với).
