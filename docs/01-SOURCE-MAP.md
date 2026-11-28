# 01 - Bản đồ mã nguồn

[← Mục lục](README.md)

Tài liệu này mô tả **toàn bộ mã nguồn hiện có** của game, từng package và từng file, để phân tích và đánh giá.
Số liệu lấy từ code ngày 2026-11-22: **109 file Java, 9.451 dòng** trong `core/src/main`, cộng 2 file launcher trong `lwjgl3/` và 12 file test (52 test) trong `core/src/test`.
Khi thêm, xoá hay đổi vai trò một file, hãy cập nhật bảng tương ứng ở đây.

Cột **Dòng** là số dòng của file (kể cả comment). Cột **Đọc / Ghi** chỉ có ở system: component nào system đọc và ghi.

---

## 1. Tổng quan

```text
MyGame/
├── lwjgl3/      Launcher desktop: mở cửa sổ 1280×720 rồi chạy com.hung.core.MyGame (không chứa logic game)
├── core/        Toàn bộ logic game (package com.hung.*)
├── assets/      Dữ liệu game: map (.tmx), sprite + JSON của player / enemy / vũ khí, attachments, ui/
├── docs/        Tài liệu
└── build_asset/ Các tool tạo asset (HTML/JS/Python, chạy ngoài game, không nằm trong git)
```

**Kiến trúc:** dạng ECS rút gọn.
- `Entity` là một túi component; component **chỉ chứa dữ liệu**.
- Các **system** chạy mỗi khung hình, duyệt `World.getEntities()` và bỏ qua entity thiếu component cần thiết.
- Các system không gọi nhau, mà nói chuyện qua dữ liệu trong component hoặc qua `EventBus`.

**Toạ độ:** đơn vị thế giới = pixel của map, trục **y hướng lên** (quy ước LibGDX). Dữ liệu trong JSON của tool (sprite, anchor, hitbox) tính theo pixel khung hình, gốc trên-trái, y hướng xuống. Các class `Hitboxes`, `RenderingSystem` lo việc đổi qua lại.

### Sơ đồ phụ thuộc giữa các package

```text
                    core (MyGame, GameScreen, EventBus)
          ┌───────────┬──────┴──────┬────────────┬───────────┐
          ▼           ▼             ▼            ▼           ▼
        save          ui         rendering    systems*     world ◄── WorldLoader dựng mọi thứ
          │           │             │            │           │
          └───────────┴──────┬──────┴────────────┘           │
                             ▼                               ▼
                  entity (Entity, các Factory) ──────► component (dữ liệu thuần)
                             │                               ▲
                             ▼                               │
                  animation / combat (định nghĩa JSON, Weapon, Effect) ─┘
* systems = ai, input, physics, combat, animation (các *System)
util (Constants, ResourceLoader, SmoothDamp) được mọi package dùng.
```

---

## 2. Vòng đời ứng dụng và vòng lặp game

### Từ lúc mở game

```text
Lwjgl3Launcher.main ─► MyGame.create()
    ├─ SpriteBatch, ResourceLoader, SaveManager, Settings.load() + apply(), UiTheme
    └─ setScreen(MenuScreen)                       ← màn hình chính
          │  New Game / Continue  (Navigator)
          ▼
    MyGame.startRun(GameSession)
    ├─ WorldLoader.load(map)  → World (Level, player, enemy, cửa)
    └─ setScreen(GameScreen)  → áp save nếu Continue
          │  Esc → "Save and return" (Navigator.returnToMainMenu)
          ▼
    MyGame.endRun(): dispose GameScreen, gỡ map khỏi ResourceLoader → về MenuScreen
```

Mọi lần chuyển màn hình đều đi qua `Gdx.app.postRunnable`, nên màn hình đang vẽ luôn vẽ xong khung hình trước khi bị thay.

### Thứ tự trong một khung hình (`GameScreen.render`)

| # | Bước | Ghi chú |
|---|------|---------|
| 0 | Menu tạm dừng / phím tắt | Nếu PauseMenu mở thì chỉ menu nhận phím; nếu không thì xử lý F2, F3, L, M, I/F1, 1/2/3, Esc |
| 1 | `InputSystem` | Ý định từ bàn phím → vận tốc, nhảy, ngồi, thang, yêu cầu tấn công / tương tác |
| 2 | `WeaponSwitchSystem` | Đổi vũ khí đang cầm (và bộ animation theo tư thế) |
| 3 | `PatrolSystem` | Enemy đi tuần |
| 4 | `EnemyBrainSystem` | Enemy có "não" phát hiện, đuổi, tấn công (ghi đè tuần tra) |
| 5 | `PhysicsSystem` (+ `CollisionSystem`) | Trọng lực, di chuyển, va chạm tile |
| 6 | `HazardSystem` | Checkpoint (→ `CheckpointEvent`), bẫy, rơi khỏi map, lối ra (→ `ExitReachedEvent`) |
| 7 | `ChestSystem` | Mở rương bằng `E`, ném vật phẩm (→ `ChestOpenedEvent`) |
| 8 | `PickupSystem` | Vật phẩm bay / nảy / nằm yên, player chạm thì nhặt (→ `PickupCollectedEvent`) |
| 9 | `ContactDamageSystem` | Chạm enemy thì mất máu |
| 10 | `AttackSystem` | Đòn cận chiến theo khung hình có attack box, phá tường BREAKABLE |
| 11 | `ShootingSystem` | Bóp cò, nạp đạn từ dự trữ, sinh đạn và hiệu ứng nòng |
| 12 | `ProjectileSystem` | Đạn bay, trúng tile hoặc cơ thể, biến mất khi rời phòng |
| 13 | `HealthSystem` | Bộ đếm bị thương / bất tử, chết (→ `EnemyKilledEvent`), hồi sinh player |
| 14 | `AnimationSystem` | Chọn animation và tăng thời gian |
| 15 | `EffectSystem` | Chạy và xoá entity hiệu ứng |
| 16 | `DoorSystem.update` | Chạy cả khi thế giới dừng (lúc màn hình tối dần khi qua cửa) |
| 17 | Vẽ | Map + entity (vật phẩm theo `ItemArt`) → ánh sáng → debug (F3) → fade cửa → HUD (máu, đạn băng / dự trữ, điểm, gợi ý `E - Open Chest`) → minimap → bảng nhân vật → toast → menu tạm dừng → FPS |

Bước 1–15 bỏ qua khi bảng nhân vật hoặc menu tạm dừng đang mở, hoặc khi màn hình đang tối dần qua cửa.

---

## 3. Từng package, từng file

Tên file trong cột File tính từ package của mục; `event/`, `system/`… là package con (ví dụ `system/AttackSystem.java` là `com.hung.combat.system.AttackSystem`). Package gốc của một domain chứa phần dùng chung; package con phụ thuộc package gốc, không theo chiều ngược lại.

### 3.1 `core/` (+ `core/event/`) — điểm vào, màn hình chơi, hạ tầng sự kiện

| File | Dòng | Vai trò |
|------|-----:|---------|
| `MyGame.java` | 106 | Lớp `Game` của LibGDX, đồng thời là `Navigator`. Tạo tài nguyên dùng chung (batch, ResourceLoader, SaveManager, Settings, UiTheme), mở MenuScreen, bắt đầu / kết thúc một ván (`startRun`, `endRun`). Kết thúc ván sẽ gỡ map để lần sau nạp lại bản gốc |
| `GameScreen.java` | 399 | Màn hình chơi. Tạo và chạy mọi system theo thứ tự cố định, camera, các renderer, PauseMenu, Toast. Đăng ký `ScoreKeeper` rồi lắng nghe sự kiện checkpoint và lối ra để lưu game, toast khi nhặt đồ / dọn phòng; việc chụp và khôi phục trạng thái giao cho `SaveSnapshot` |
| `Navigator.java` | 20 | Interface chuyển màn hình: `startNewGame`, `continueGame`, `returnToMainMenu`, `quit` |
| `GameMode.java` | 48 | Enum chế độ chơi, mỗi chế độ một file map, một `EnemySkill` (con số) và một `TacticPolicy` (cách đánh theo vùng): EASY, MEDIUM, HARD; cờ khoá (`isAvailable()`), HARD đang khoá |
| `event/EventBus.java` | 44 | Trung tâm observer: publisher phát đối tượng sự kiện, listener đăng ký theo class sự kiện. Danh sách listener kiểu copy-on-write, publish không cấp phát bộ nhớ |
| `event/EventListener.java` | 13 | Interface 1 hàm `onEvent(T)` (dùng được lambda) |

### 3.2 `world/` (+ `event/`, `loader/`, `system/`) — level, phòng, cửa, nạp map

| File | Dòng | Vai trò |
|------|-----:|---------|
| `World.java` | 48 | Chứa Level, danh sách entity, player, điểm hồi sinh (đổi qua `setRespawnPoint`), trạng thái chuyển phòng. Không có logic |
| `Level.java` | 75 | Một level đã nạp: TiledMap để vẽ, lưới va chạm, thông số vật lý, điểm xuất phát, lối ra, checkpoint, đèn, phòng; `findRoom(id, x, y)` (phòng theo id, không có thì phòng chứa điểm) |
| `loader/WorldLoader.java` | 357 | Dựng `World` từ file `.tmx`: lưới va chạm từ layer `terrain`, spawn và lối ra, enemy (kèm phòng nhà và tactic theo vùng), đèn, phòng và cửa, player kèm vũ khí. Xếp hàng nạp mọi texture trước rồi mới tạo entity |
| `loader/RoomLoader.java` | 116 | Đọc layer `rooms` và `doors` của map: phòng (vùng giới hạn camera) và entity cửa |
| `Room.java` | 38 | Một phòng: id, index, loại, vùng, phòng kề, cửa ra vào |
| `system/DoorSystem.java` | 126 | Chuyển phòng: chạm cửa → màn hình tối dần (thế giới dừng) → dời player → phát `RoomTransitionEvent` → sáng lại. Không biết gì về camera hay minimap |
| `system/ChestSystem.java` | 135 | Rương: `E` khi đứng sát → OPENING (dải mở) → OPENED (giữ khung cuối), ném vật phẩm theo `LootTable` / `LootDrop`, phát `ChestOpenedEvent`; `markOpened` cho rương save nói đã mở |
| `RoomTransition.java` | 23 | Trạng thái chuyển phòng (NONE / FADING_OUT / FADING_IN, độ tối, phòng hiện tại) |
| `event/RoomTransitionEvent.java` | 47 | Sự kiện đổi phòng (lý do: LEVEL_START, DOOR, RELOCATED) |
| `event/CheckpointEvent.java` | 24 | Sự kiện chạm checkpoint mới (index, tổng số, điểm hồi sinh) → GameScreen lưu game |
| `event/ExitReachedEvent.java` | 16 | Sự kiện tới lối ra (một lần) → điểm hoàn thành, đánh dấu ván hoàn thành |
| `event/EnemyKilledEvent.java` | 17 | Một quái vừa chết (`HealthSystem`, một lần mỗi quái) → điểm |
| `event/ChestOpenedEvent.java` | 20 | Một rương vừa mở xong (id, tier) → điểm |
| `event/RoomClearedEvent.java` | 20 | Phòng vừa hết quái lần đầu (`ScoreKeeper`) → toast |
| `event/PickupCollectedEvent.java` | 20 | Vừa nhặt một vật phẩm (loại, lượng thật nhận) → toast |
| `TileGrid.java` | 65 | Lưới tile va chạm (hàng 0 ở đáy). Ngoài map: hai bên là tường, trên dưới là khoảng trống. `hasFloorAhead` là phép dò mép vực dùng chung cho PatrolSystem và EnemyBrainSystem |
| `TileType.java` | 33 | Loại tile: SOLID, PLATFORM, LADDER, SPIKE, TOXIC, BREAKABLE… |
| `PhysicsSettings.java` | 44 | Thông số di chuyển dùng chung với tool tạo map (tốc độ, độ cao và khoảng cách nhảy), đổi từ tile sang đơn vị thế giới |
| `EnemySpawn.java` | 20 | Một vị trí đặt enemy (toạ độ, hướng, đoạn tuần tra, file định nghĩa, phòng) |
| `system/PlayerRespawn.java` | 43 | Đưa player về điểm hồi sinh, đứng yên, bất tử một lúc |
| `LightSource.java` | 61 | Một nguồn sáng của map (chỉ để vẽ) |

### 3.3 `entity/` (+ `player/`, `enemy/`) — entity, cây nhân vật và các factory

| File | Dòng | Vai trò |
|------|-----:|---------|
| `Entity.java` | 81 | Túi component: `TransformComponent` bắt buộc (tạo sẵn, `final`), 16 component tuỳ chọn, và tham chiếu `Controller` (interface, không phải component). Chi tiết: [02 → mục 4](02-ARCHITECTURE.md#4-entity-component-và-cây-nhân-vật) |
| `GameCharacter.java` | 35 | Lớp trừu tượng cho mọi nhân vật chiến đấu: phe (`getFaction()` trừu tượng), `isHostileTo`, `areHostile`, `canBreakWalls`, `isAlive` |
| `EntityFactory.java` | 79 | Dựng entity không phải nhân vật: cửa, vật phẩm (`createPickup`), rương (`createChest`, kèm dải mở) |
| `player/Player.java` | 17 | Người chơi: phe PLAYER, phá được tường |
| `player/PlayerFactory.java` | 230 | Dựng `Player` từ file JSON (animation nhiều tư thế, bàn tay, hitbox) |
| `player/CharacterDefinition.java` | 29 | Phần của file JSON player mà game dùng (khung hình, drawScale, collider, `weaponLayer`, animations) |
| `enemy/Enemy.java` | 94 | Lớp trừu tượng của mọi quái: phe ENEMY, sát thương khi chạm, kháng đẩy lùi, phòng nhà (`homeRoom`); template method `updateBehaviour` (chết / bị thương / `think()` trừu tượng) |
| `enemy/MeleeEnemy.java` | 38 | small_melee_*: con số của melee; dừng cách một tầm vuốt (`holdRatio`) |
| `enemy/HunterEnemy.java` | 378 | Lớp trừu tượng của quái biết săn: phát hiện, nhớ, gọi bầy (trong phòng nhà), nhảy, đón đầu; đánh / áp sát / lùi do `HuntTactic` quyết, quái thực hiện qua `TacticActions`; con số do lớp con cho, nhân với `EnemySkill` |
| `enemy/EnemySkill.java` | 33 | Độ mạnh và độ khôn của quái theo chế độ (EASY, MEDIUM, HARD) |
| `enemy/JumpPlanner.java` | 85 | Tìm cú nhảy an toàn trên lưới tile: qua hố, gai, bậc, lên bệ thấp |
| `enemy/CrawlerEnemy.java` | 28 | crawler_01: con số của crawler |
| `enemy/FlyingEnemy.java` | 132 | Quái bay: override đuổi và lùi (bỏ qua mép vực), đánh trên không, lái độ cao, xác rơi sau DEATH |
| `enemy/EnemyFactory.java` | 194 | Chọn lớp quái theo `archetype` của file (`newEnemy`), rồi lắp component từ file: animation + hitbox theo khung, thân, đoạn tuần tra, hiệu ứng. Đổi các mức LOW / MEDIUM / HIGH thành con số máu và sát thương |
| `enemy/EnemyDefinition.java` | 107 | Phần của `character.json` enemy mà game dùng: anchor, sprite sheet, animation (frame, hitbox), khối gameplay. Chỉ đọc sau khi nạp, dùng chung cho mọi con cùng loại |

### 3.4 `component/` — dữ liệu thuần

| File | Dòng | Chứa gì |
|------|-----:|---------|
| `TransformComponent.java` | 18 | Vị trí (chân), vận tốc, hướng mặt |
| `MovementComponent.java` | 36 | Tốc độ, lực nhảy, trạng thái chạm đất / bị chặn / trên thang / ngồi / bay |
| `ColliderComponent.java` | 17 | Hộp va chạm quanh chân (rộng, cao, cao khi ngồi) |
| `AnimationComponent.java` | 96 | Các bộ animation theo tư thế, animation đang chạy, thời gian, drawScale, footOffset. 5 map của tư thế đang dùng (animation, tay, khung gốc, lớp tay trước, attack box) là private, chỉ đổi cùng lúc qua `useStance(name)` |
| `HealthComponent.java` | 71 | Máu và trạng thái chết được đóng gói: chỉ đổi qua `applyDamage`, `heal`, `restoreFull`, `setHealthClamped` (luôn 0 ≤ máu ≤ max, chết ⇔ máu = 0). Bộ đếm bị thương / bất tử, kháng đẩy lùi để public |
| `AttackComponent.java` | 19 | Trạng thái đòn cận chiến, sát thương |
| `WeaponComponent.java` | 51 | Vũ khí đang cầm và cách cầm (kích thước, gốc xoay, anchor tay); với súng có thêm băng đạn, đạn dự trữ và mức tối đa, hồi chiêu, đang nạp |
| `ArsenalComponent.java` | 21 | Danh sách vũ khí mang theo, ô đang cầm, yêu cầu đổi |
| `PatrolComponent.java` | 16 | Đoạn tuần tra (minX, maxX, hướng) |
| `SpriteComponent.java` | 29 | Sprite đặt theo điểm gốc (hiệu ứng, đạn, vỏ đạn, rương), màu tô |
| `EffectComponent.java` | 19 | Thời gian sống còn lại, ánh sáng đi kèm, dừng khi chạm đất (vỏ đạn) |
| `ProjectileComponent.java` | 23 | Viên đạn đang bay: chủ, sát thương, tầm còn lại, phòng lúc bắn |
| `DoorComponent.java` | 30 | Một cửa: vùng kích hoạt, phòng đi, phòng đến, điểm xuất hiện |
| `PickupComponent.java` | 19 | Vật phẩm trên sàn: loại, đang bay / đã nảy, điểm rơi |
| `InteractComponent.java` | 10 | Yêu cầu tương tác (`E`) của player |
| `ChestComponent.java` | 26 | Rương: id ổn định, tier, phòng, trạng thái CLOSED / OPENING / OPENED, dải mở |

### 3.5 `input/` — điều khiển

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `Controller.java` | 38 | Interface ý định của player (đi, nhảy, leo, đánh, nạp, đổi vũ khí). Enemy không dùng controller | — |
| `KeyboardController.java` | 92 | Bàn phím + chuột cho player, mỗi hành động nhận nhiều phím | — |
| `InputSystem.java` | 274 | Ý định → di chuyển: đi, nhảy (coyote time, jump buffer), ngồi, xuống platform một chiều, leo thang, yêu cầu tấn công / bắn / nạp | Đọc Controller, Movement · Ghi Transform, Attack, Weapon |
| `CameraInput.java` | 108 | Camera tự do (F2 hoặc không có player) và zoom khi đi theo player | Ghi CameraController |

### 3.6 `ai/` — trí tuệ enemy

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `PatrolSystem.java` | 84 | Đi qua lại trong đoạn tuần tra, quay đầu ở mép, tường, vực | Đọc Patrol, Movement, Collider · Ghi Transform |
| `EnemyBrainSystem.java` | 51 | Gọi `Enemy.updateBehaviour` của từng quái (đa hình): mỗi lớp quái tự săn, đánh, bay | Đọc Health của player · mỗi Enemy ghi Transform, Attack |

`ai/tactic/` — cách đánh của quái (Strategy), Java thuần, không phụ thuộc package khác của game:

| File | Dòng | Vai trò |
|------|-----:|---------|
| `HuntTactic.java` | 19 | Interface: `decide(context, actions)`; hook `onHurt()`, `onTargetLost()` |
| `HuntContext.java` | 17 | Record: những gì tactic biết về một khung săn (dx, tầm đánh, khoảng dừng, trong tầm, đánh được, bước tới / lùi an toàn, delta) |
| `TacticActions.java` | 22 | Interface hành động mà `HunterEnemy` cài: `face`, `strike`, `approach`, `retreat`, `hold` |
| `DirectChaseTactic.java` | 18 | Áp sát, đánh khi sẵn sàng, đứng ở khoảng dừng (hành vi gốc) |
| `HitAndRunTactic.java` | 93 | Đánh rồi lùi: APPROACH → ATTACK → RETREAT → RECOVER |
| `TacticPolicy.java` | 30 | Tactic theo vùng của chế độ (`hitAndRunFromRegion`); mỗi lần một instance mới |

### 3.7 `physics/` — vật lý, va chạm, vùng nguy hiểm

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `PhysicsSystem.java` | 44 | Trọng lực (trừ lúc trên thang hoặc bay), rồi di chuyển theo vận tốc | Ghi Transform, Movement |
| `CollisionSystem.java` | 126 | Đẩy hộp va chạm qua lưới tile theo từng trục: tường chặn ngang, đụng đầu, đáp xuống SOLID / PLATFORM / đỉnh thang | Ghi Transform, Movement |
| `HazardSystem.java` | 107 | Checkpoint, bẫy SPIKE / TOXIC, rơi khỏi map, lối ra. Phát `CheckpointEvent` và `ExitReachedEvent` | Ghi điểm hồi sinh, vị trí player |
| `Hitboxes.java` | 133 | Tiện ích tính hộp thân và hộp tấn công trong toạ độ thế giới (đổi từ toạ độ sprite, lật khi quay trái), ghi vào Rectangle có sẵn | — |

### 3.8 `combat/` (+ `definition/`, `system/`) — chiến đấu, vũ khí

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `system/AttackSystem.java` | 109 | Đòn cận chiến theo animation ATTACK: khung có attack box gây sát thương phía bên kia, phá tile BREAKABLE (sửa trực tiếp TiledMap), mỗi cú vung trúng tối đa một lần | Đọc Animation, Attack · Ghi Health (qua Damage), TileGrid |
| `system/ContactDamageSystem.java` | 45 | `Enemy` chạm player thì gây `getContactDamage()` sát thương | Ghi Health của player |
| `system/ShootingSystem.java` | 178 | Súng: bóp cò, hồi chiêu, hết đạn → nạp từ dự trữ (dự trữ rỗng thì không nạp), R nạp sớm, sinh đạn ở nòng (gắn phòng của người bắn), lửa nòng, vỏ đạn văng | Ghi Weapon, thêm entity đạn và hiệu ứng |
| `system/ProjectileSystem.java` | 100 | Đạn bay theo bước ngắn (không xuyên tile), dừng ở tile cứng hoặc thân phe kia, hiệu ứng trúng; biến mất khi ra khỏi phòng lúc bắn | Ghi Health, xoá đạn |
| `system/HealthSystem.java` | 96 | Bộ đếm bị thương / bất tử, giảm đẩy lùi; enemy chết → animation DEATH → xoá; player chết → hồi sinh đủ máu; phát `EnemyKilledEvent` | Ghi Health, Transform |
| `system/PickupSystem.java` | 90 | Vật phẩm bay vòng cung, nảy một lần, nằm yên (bụi khi chạm đất); player chạm thì nhặt nếu có ích (lấp lánh, `PickupCollectedEvent`) | Ghi Pickup, Transform, Health / dự trữ (qua Pickups) |
| `system/WeaponSwitchSystem.java` | 59 | Đổi ô vũ khí và bộ animation theo tư thế; không đổi giữa cú vung, khi bị thương hay đã chết | Ghi Arsenal, Weapon, Animation |
| `Damage.java` | 67 | Áp một đòn: trừ máu, bị thương / chết, đẩy lùi, bất tử. Dùng chung cho mọi system gây sát thương | — |
| `Effects.java` | 51 | Sinh entity hiệu ứng ngắn (lửa nòng, trúng đạn, hiệu ứng enemy) và gắn ánh sáng | — |
| `Weapon.java` | 70 | Một vũ khí đã nạp: hình, kích thước, gốc xoay, anchor (mũi, nòng, cửa văng vỏ), dữ liệu bắn, hiệu ứng | — |
| `WeaponFactory.java` | 180 | Dựng vũ khí (sprite, hiệu ứng súng) và gắn vào tay entity: một vũ khí, hoặc một kho vũ khí để đổi qua lại. Áp profile attachment; súng ra đầy băng + dự trữ ban đầu (`AmmoType`); `createEffect` dùng chung cho hiệu ứng vật phẩm | — |
| `AmmoType.java` | 32 | Loại đạn theo tư thế cầm (PISTOL, RIFLE): dự trữ ban đầu / tối đa, lượng mỗi hộp đạn | — |
| `PickupType.java` | 18 | Loại vật phẩm: hộp cứu thương, hộp đạn súng lục, hộp đạn súng trường | — |
| `Pickups.java` | 47 | Luật nhặt: hồi 25 máu, cộng đạn dự trữ; trả 0 = vô dụng, để lại | — |
| `ChestTier.java` | 12 | Gỗ / vàng (`tier` của object CHEST) | — |
| `LootTable.java` | 39 | Trọng số vật phẩm theo tier; chọn tất định theo id rương (SplitMix64) | — |
| `LootDrop.java` | 98 | Chỗ rơi an toàn (sàn, không gai / độc / tường, trong phòng, cung bay thông) và vận tốc ném | — |
| `ItemArt.java` | 65 | Hình vật phẩm, dải mở rương, bụi, lấp lánh (từ `assets/sprites/items/items.json`); `NONE` = hình tạm | — |
| `definition/ItemArtDefinition.java` | 17 | Manifest hình loot | — |
| `definition/WeaponDefinition.java` | 34 | Nội dung file JSON vũ khí | — |
| `definition/FireDefinition.java` | 29 | Dữ liệu bắn của súng (tốc độ bắn, băng đạn, tầm, thời gian nạp…) | — |
| `definition/EffectDefinition.java` | 20 | Một dải hiệu ứng của súng trong JSON vũ khí | — |
| `WeaponEffect.java` | 21 | Một dải hiệu ứng đã nạp (animation + điểm gốc + thời lượng). Dùng cho cả súng lẫn hiệu ứng enemy | — |
| `definition/AttachmentProfile.java` | 35 | Profile "nhân vật X cầm vũ khí Y": ghi đè anchor tay, kích thước, gốc xoay | — |

### 3.9 `animation/` (+ `definition/`, `system/`) — animation và hiệu ứng

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `system/AnimationSystem.java` | 98 | Chọn animation theo ưu tiên DEATH > HIT > ATTACK > SHOOT > leo > CROUCH > JUMP > WALK > IDLE, tăng thời gian | Đọc Transform, Movement, Health · Ghi Animation |
| `AnimationFrames.java` | 51 | Tiện ích đọc khung hiện tại (khung vẽ, khung gốc, anchor tay, thời lượng, đã chạy xong chưa) | — |
| `AnimationSet.java` | 39 | Bộ animation của một tư thế (mặc định, PISTOL, RIFLE) | — |
| `AnimationType.java` | 20 | Enum trạng thái: IDLE, WALK, JUMP, ATTACK, DEFEND, HIT, DEATH, CROUCH, SHOOT | — |
| `definition/AnimationDefinition.java` | 43 | Một animation trong JSON player (sheet, dải tay, khung, anchor tay, hitbox) | — |
| `definition/FrameDefinition.java` | 16 | Một khung: thời lượng, sự kiện, attack box | — |
| `HandAnchor.java` | 12 | Vị trí và góc của bàn tay cầm vũ khí trong một khung | — |
| `system/EffectSystem.java` | 39 | Chạy sprite hiệu ứng và xoá khi hết giờ; vỏ đạn chạm đất thì dừng | Ghi Sprite, Effect, xoá entity |

### 3.10 `rendering/` — vẽ thế giới và lớp phủ

| File | Dòng | Vai trò |
|------|-----:|---------|
| `RenderingSystem.java` | 220 | Vẽ các layer tile của map và entity xen giữa (tường → decor sau → terrain → props → entity → decor trước). Mỗi nhân vật: thân → vũ khí → bàn tay trước |
| `CameraController.java` | 167 | Camera đi theo player bằng lò xo giảm chấn (SmoothDamp), bị giới hạn trong phòng hiện tại (nghe `RoomTransitionEvent`) |
| `LightingRenderer.java` | 173 | Lớp ánh sáng phủ lên cảnh: bóng tối nền + đèn của map, đèn của player, lửa nòng (L bật / tắt) |
| `HudRenderer.java` | 127 | Thanh máu, tên vũ khí và đạn (băng / dự trữ), điểm, gợi ý `E - Open Chest`, phím của camera tự do |
| `InteractionPrompt.java` | 54 | Tìm rương CLOSED gần player nhất trong tầm mở (cùng phép thử với `ChestSystem`) cho gợi ý trên HUD; chỉ đọc |
| `StatsPanel.java` | 448 | Bảng nhân vật (I / F1): cột player dạng thẻ (máu, di chuyển, thân & chiến đấu, vị trí); danh sách vũ khí (hình, phím, tên) và thẻ chi tiết chỉ cho vũ khí đang cầm (sát thương, tầm, `Mag / Res` cùng chữ với HUD, nạp / vung); kiêm chọn vũ khí |
| `DebugRenderer.java` | 166 | Lớp debug F3: tile va chạm, hộp, attack box, đoạn tuần tra, checkpoint, giới hạn camera, cửa |
| `FadeRenderer.java` | 40 | Lớp đen khi qua cửa |

### 3.11 `ui/` (+ `menu/`, `minimap/`) — menu, minimap, giao diện

| File | Dòng | Vai trò |
|------|-----:|---------|
| `menu/MenuScreen.java` | 197 | Màn hình chính: nền boong-ke, tên game, người lính chuyển động, khung menu. Dựng các trang Continue, New Game (chế độ → chơi tiếp hoặc chơi lại nếu đã có save), Settings, Quit từ file save |
| `menu/MenuView.java` | 448 | Vẽ ngăn xếp trang `Menu` thành khung terminal theo bản thiết kế; xử lý bàn phím và chuột. Dàn trang trong toạ độ thiết kế 1280×720 |
| `menu/Menu.java` | 142 | Model một trang menu (chip, dòng chữ, bảng phím, mục: chi tiết, công tắc, khoá, nguy hiểm), API dạng chuỗi lệnh |
| `UiTheme.java` | 155 | Bảng màu (chép từ bản thiết kế HTML), font FreeType theo tỉ lệ cửa sổ, texture sọc cảnh báo, pixel trắng |
| `menu/PauseMenu.java` | 85 | Menu tạm dừng (Esc) và màn "Mission complete" |
| `menu/SettingsMenu.java` | 52 | Trang Settings (công tắc toàn màn hình, VSync, FPS) và bảng phím, dùng chung cho cả 2 menu |
| `Toast.java` | 76 | Thông báo ngắn ở đáy màn hình ("SAVE  Checkpoint 3 of 7 - saved") |
| `FpsOverlay.java` | 44 | Hiện FPS ở góc phải trên |
| `minimap/MinimapState.java` | 61 | Phòng đã thăm / đã biết, chỉ cập nhật qua `RoomTransitionEvent`; khôi phục được từ save |
| `minimap/MinimapRenderer.java` | 224 | Vẽ minimap góc phải trên (M mở cả bản đồ) |

### 3.12 `save/` — lưu game và cài đặt

| File | Dòng | Vai trò |
|------|-----:|---------|
| `SaveData.java` | 84 | Cấu trúc JSON của một ván lưu (schema phiên bản 2, đọc được bản 1): chế độ, map + chữ ký map, vị trí, điểm hồi sinh, checkpoint, máu, vũ khí + đạn trong băng + dự trữ, phòng đã khám phá, thời gian chơi, hoàn thành, điểm + thống kê + id đã tính, rương đã mở, vật phẩm trên sàn |
| `SaveSnapshot.java` | 209 | Chụp `World` thành `SaveData` (`capture`) và đặt `SaveData` vào thế giới vừa nạp (`apply`, kể cả `RunStats`, rương đã mở, vật phẩm); kiểm tra chữ ký map. Không đọc ghi file, không cần LibGDX nên test được |
| `SaveManager.java` | 149 | Mỗi chế độ một save ở `~/.mygame/saves/<chế độ>.json`; nhập save từ 3 ô lưu cũ. Đọc không bao giờ ném lỗi: EMPTY / OK / CORRUPT / INCOMPATIBLE. Ghi qua file tạm. Constructor thứ hai nhận thư mục (dùng cho test) |
| `Settings.java` | 61 | Cài đặt ở `~/.mygame/settings.json`, áp chế độ hiển thị |
| `GameSession.java` | 27 | Ván đang chơi: chế độ, save đang tiếp tục, thời gian chơi, đã hoàn thành, `RunStats` |

### 3.12b `score/` — điểm của ván

| File | Dòng | Vai trò |
|------|-----:|---------|
| `ScoreRules.java` | 13 | Bảng điểm (hạ quái 100, rương gỗ 150 / vàng 300, dọn phòng 250, hoàn thành 1000) |
| `RunStats.java` | 88 | Điểm, bộ đếm, id đã tính điểm (quái, rương, phòng); mỗi id một lần; `restore` cho save |
| `ScoreKeeper.java` | 60 | Nghe `EnemyKilledEvent`, `ChestOpenedEvent`, `ExitReachedEvent`; tính dọn phòng, phát `RoomClearedEvent` |

### 3.13 `util/` — tiện ích

| File | Dòng | Vai trò |
|------|-----:|---------|
| `Constants.java` | 57 | Hằng số toàn cục: map khởi đầu, player và vũ khí mặc định, danh sách enemy thử nghiệm, đường dẫn profile, khung hình tối đa |
| `ResourceLoader.java` | 89 | Nạp và cache texture, TiledMap (qua AssetManager), JSON (nghiêm ngặt cho file của game, bỏ qua trường lạ cho file của tool); `unloadMap` |
| `SmoothDamp.java` | 56 | Lò xo giảm chấn tới hạn cho camera |

### 3.14 `lwjgl3/` — launcher desktop

| File | Dòng | Vai trò |
|------|-----:|---------|
| `Lwjgl3Launcher.java` | 48 | Cấu hình cửa sổ (tiêu đề "MyGame", 1280×720, VSync, FPS) và chạy `MyGame` |
| `StartupHelper.java` | 227 | Mã bên thứ ba đi kèm template LibGDX (damios, giấy phép Apache 2.0): khởi động lại JVM khi cần (macOS, một số trường hợp trên Windows). Không nên sửa |

---

## 4. Dữ liệu ngoài code

| Thư mục | Nội dung | Ai tạo ra | Ai đọc |
|---------|----------|-----------|--------|
| `assets/maps/<name>/` | `map_easy.tmx`, `map_medium.tmx`, `map_hard.tmx`, tileset | tool map (`build_map`) | `WorldLoader`, `RoomLoader` |
| `assets/sprites/players/<id>/` | `<id>.json` + dải animation thân và tay | tool player (`build_character`) | `PlayerFactory` |
| `assets/sprites/weapons/<id>/` | `<id>.json`, hình, `fx/` | tool vũ khí (`build_weapon`) | `WeaponFactory` |
| `assets/attachments/<character>/` | cách nhân vật cầm từng vũ khí | tool vũ khí | `WeaponFactory` |
| `assets/sprites/enemies/<id>/` | `character.json`, sprite sheet | tool enemy (`build_enemy`, `tools/sync-game.mjs`) | `EnemyFactory` |
| `assets/ui/` | font, sọc cảnh báo, nền màn hình chính | tool UI (`build_ui`) | `UiTheme`, `MenuScreen` |
| `~/.mygame/` | save và cài đặt của người chơi | game | `SaveManager`, `Settings` |

---

## 5. Các luồng chính

**Tạo player:** `WorldLoader` → `PlayerFactory.createPlayer` (JSON player, animation nhiều tư thế, bàn tay) → `WeaponFactory` (kho 3 vũ khí, profile attachment) → `World.setPlayer`.

**Đòn đánh của enemy:** `EnemyBrainSystem` gọi `updateBehaviour`, `HunterEnemy.think` hỏi `HuntTactic`, tactic gọi `strike()` → `AnimationSystem` chạy → `AttackSystem` lấy attack box của khung hiện tại (qua `Hitboxes.fromSprite`) → `Damage.apply` lên player → `HealthSystem`.

**Qua cửa:** `DoorSystem` thấy player chạm cửa → tối dần (thế giới dừng) → dời player → phát `RoomTransitionEvent` → `CameraController` giới hạn theo phòng mới, `MinimapState` đánh dấu phòng → sáng lại.

**Lưu game:** `HazardSystem` phát `CheckpointEvent` → `GameScreen.save(CHECKPOINT)` → `SaveSnapshot.capture` → `SaveManager.write` → `Toast`. Khi về menu, thoát hoặc đóng cửa sổ (`pause()`), `save(QUIT)` ghi vị trí đang đứng; nếu lúc đó không an toàn (đang chết, đang qua cửa) thì ghi vị trí checkpoint. Continue: `MyGame.startRun` → `GameScreen.applySave` → `SaveSnapshot.apply` đặt lại vị trí, điểm hồi sinh, máu, đạn, vũ khí, minimap (bỏ vị trí và minimap nếu chữ ký map khác).

---

## 6. Điểm cần lưu ý khi đánh giá

**Điểm mạnh**
- Cây kế thừa cho nhân vật: `GameCharacter` → `Player` / `Enemy` → `HunterEnemy` → `MeleeEnemy`, `CrawlerEnemy`, `FlyingEnemy`. Mỗi loại quái tự giữ con số và phần hành vi khác biệt (override), `EnemyBrainSystem` gọi đa hình, không còn chuỗi if/else theo loại ([02 → mục 12](02-ARCHITECTURE.md#12-bốn-tính-chất-oop)).
- Lưu / khôi phục tách khỏi `GameScreen` (`save/SaveSnapshot`), test được không cần LibGDX.
- Component có bất biến được đóng gói: máu và trạng thái chết (`HealthComponent`), tư thế animation (`AnimationComponent.useStance`).
- Logic dùng chung gom một chỗ: sát thương (`Damage.apply`), dò mép vực (`TileGrid.hasFloorAhead`), toạ độ hitbox / vũ khí (`Hitboxes`).
- Phòng, camera, minimap, lưu game nói chuyện qua sự kiện (`EventBus`), không gọi thẳng nhau.

**Hạn chế đã biết** (chi tiết: [02-ARCHITECTURE.md → mục 11](02-ARCHITECTURE.md#11-nợ-kỹ-thuật-đã-biết))
- Class lớn: `MenuView` (518 dòng), `StatsPanel` (448), `HunterEnemy` (378), `MenuScreen` (337), `WorldLoader` (310).
- Phụ thuộc vòng giữa package: 2 nhóm, 7 cặp phụ thuộc 2 chiều. Nhóm gameplay: `component` ↔ `combat`, `component` ↔ `entity`, `component` ↔ `animation`, `entity` ↔ `input`, `physics` ↔ `world.system`. Nhóm màn hình: `core` ↔ `save`, `core` ↔ `ui.menu`.
- Hiệu ứng nằm ở 2 package (`animation`, `combat`).
- `Entity` không có id; mỗi loại component là một field cố định (chủ ý, đơn giản và nhanh).
- `AttackSystem` phá tường bằng cách sửa TiledMap đang cache, được bù bằng `ResourceLoader.unloadMap` khi kết thúc ván. Tường đã phá và quái đã giết chưa được lưu.
- Enemy không có `Controller`; AI ghi thẳng vào component. Chuyển sang AI controller sẽ đổi thứ tự update nên chưa làm.
- Số liệu gameplay của enemy (máu, sát thương, tầm phát hiện, hồi chiêu) vẫn là hằng số trong `EnemyFactory` và class quái; mức theo chế độ ở `EnemySkill`; cách đánh theo vùng ở `GameMode.tacticPolicy`.
- Bảng màu UI có 2 bản (file HTML thiết kế và `UiTheme.java`), đổi phải chép tay.

**Kiểm thử**
- JUnit 5 (`core/src/test`, chạy `gradlew core:test`): `HealthComponent`, `Damage`, `HealthSystem`, `AnimationComponent`, `SaveSnapshot`, `SaveManager` (kèm file save thật làm mẫu tương thích ngược), `TileGrid`, `PatrolSystem`, `EventBus`, `MinimapState`, cây nhân vật (`EnemyHierarchyTest`).
- Chưa có test cho phần vẽ, input thật, `HazardSystem` (dùng `Gdx.app` để log); phần bay của `FlyingEnemy` (lái độ cao, xác rơi) chưa có test riêng.
