# 01 - Bản đồ mã nguồn

[← Index](README.md)

Tài liệu này mô tả **toàn bộ mã nguồn hiện có** của game, từng package và từng file, để phân tích và đánh giá.
Số liệu lấy từ code ngày 2026-11-21 (sau đợt refactor OOP): **102 file Java, khoảng 9.160 dòng** trong `core/src/main`, cộng 2 file launcher trong `lwjgl3/` và 11 file test trong `core/src/test`.
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

**Toạ độ:** đơn vị thế giới = pixel của map, trục **y hướng lên** (quy ước LibGDX). Dữ liệu trong JSON của tool (sprite, anchor, hitbox) tính theo pixel khung hình, gốc trên-trái, y hướng xuống. Các class `Hitboxes`, `RenderingSystem` và `EnemyEffectSystem` lo việc đổi qua lại.

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
| 1 | `InputSystem` | Ý định từ bàn phím → vận tốc, nhảy, ngồi, thang, yêu cầu tấn công |
| 2 | `WeaponSwitchSystem` | Đổi vũ khí đang cầm (và bộ animation theo tư thế) |
| 3 | `PatrolSystem` | Enemy đi tuần |
| 4 | `EnemyBrainSystem` | Enemy có "não" phát hiện, đuổi, tấn công (ghi đè tuần tra) |
| 5 | `PhysicsSystem` (+ `CollisionSystem`) | Trọng lực, di chuyển, va chạm tile |
| 6 | `HazardSystem` | Checkpoint (→ `CheckpointEvent`), bẫy, rơi khỏi map, lối ra (→ `ExitReachedEvent`) |
| 7 | `ContactDamageSystem` | Chạm enemy thì mất máu |
| 8 | `AttackSystem` | Đòn cận chiến theo khung hình có attack box, phá tường BREAKABLE |
| 9 | `ShootingSystem` | Bóp cò, nạp đạn, sinh đạn và hiệu ứng nòng |
| 10 | `ProjectileSystem` | Đạn bay, trúng tile hoặc cơ thể |
| 11 | `HealthSystem` | Bộ đếm bị thương / bất tử, chết, hồi sinh player |
| 12 | `AnimationSystem` | Chọn animation và tăng thời gian |
| 13 | `EnemyEffectSystem` | Sinh hiệu ứng của enemy đúng khung hình (gầm, sóng chấn động…) |
| 14 | `EffectSystem` | Chạy và xoá entity hiệu ứng |
| 15 | `DoorSystem.update` | Chạy cả khi thế giới dừng (lúc màn hình tối dần khi qua cửa) |
| 16 | Vẽ | Map + entity → ánh sáng → debug (F3) → fade cửa → HUD → minimap → bảng nhân vật → toast → menu tạm dừng → FPS |

Bước 1–14 bỏ qua khi bảng nhân vật hoặc menu tạm dừng đang mở, hoặc khi màn hình đang tối dần qua cửa.

---

## 3. Từng package, từng file

### 3.1 `core/` — điểm vào, màn hình chơi, sự kiện

| File | Dòng | Vai trò |
|------|-----:|---------|
| `MyGame.java` | 105 | Lớp `Game` của LibGDX, đồng thời là `Navigator`. Tạo tài nguyên dùng chung (batch, ResourceLoader, SaveManager, Settings, UiTheme), mở MenuScreen, bắt đầu / kết thúc một ván (`startRun`, `endRun`). Kết thúc ván sẽ gỡ map để lần sau nạp lại bản gốc |
| `GameScreen.java` | 372 | Màn hình chơi. Tạo và chạy mọi system theo thứ tự cố định, camera, các renderer, PauseMenu, Toast. Lắng nghe sự kiện checkpoint và lối ra để lưu game; việc chụp và khôi phục trạng thái giao cho `SaveSnapshot` |
| `Navigator.java` | 20 | Interface chuyển màn hình: `startNewGame`, `continueGame`, `returnToMainMenu`, `quit` |
| `GameMode.java` | 36 | Enum chế độ chơi: EASY, NORMAL (khoá, chưa có map), HARD (= map bunker hiện tại) |
| `EventBus.java` | 44 | Trung tâm observer: publisher phát đối tượng sự kiện, listener đăng ký theo class sự kiện. Danh sách listener kiểu copy-on-write, publish không cấp phát bộ nhớ |
| `EventListener.java` | 13 | Interface 1 hàm `onEvent(T)` (dùng được lambda) |

### 3.2 `world/` — level, phòng, cửa, nạp map

| File | Dòng | Vai trò |
|------|-----:|---------|
| `World.java` | 48 | Chứa Level, danh sách entity, player, điểm hồi sinh (đổi qua `setRespawnPoint`), trạng thái chuyển phòng. Không có logic |
| `Level.java` | 69 | Một level đã nạp: TiledMap để vẽ, lưới va chạm, thông số vật lý, điểm xuất phát, lối ra, checkpoint, đèn, phòng |
| `WorldLoader.java` | 306 | Dựng `World` từ file `.tmx`: lưới va chạm từ layer `terrain`, spawn và lối ra, enemy, đèn, phòng và cửa, enemy thử nghiệm, player kèm vũ khí. Xếp hàng nạp mọi texture trước rồi mới tạo entity |
| `RoomLoader.java` | 113 | Đọc layer `rooms` và `doors` của map: phòng (vùng giới hạn camera) và entity cửa |
| `Room.java` | 38 | Một phòng: id, index, loại, vùng, phòng kề, cửa ra vào |
| `DoorSystem.java` | 120 | Chuyển phòng: chạm cửa → màn hình tối dần (thế giới dừng) → dời player → phát `RoomTransitionEvent` → sáng lại. Không biết gì về camera hay minimap |
| `RoomTransition.java` | 23 | Trạng thái chuyển phòng (NONE / FADING_OUT / FADING_IN, độ tối, phòng hiện tại) |
| `RoomTransitionEvent.java` | 46 | Sự kiện đổi phòng (lý do: LEVEL_START, DOOR, RELOCATED) |
| `CheckpointEvent.java` | 24 | Sự kiện chạm checkpoint mới (index, tổng số, điểm hồi sinh) → GameScreen lưu game |
| `ExitReachedEvent.java` | 16 | Sự kiện tới lối ra (một lần) → đánh dấu ván hoàn thành |
| `TileGrid.java` | 65 | Lưới tile va chạm (hàng 0 ở đáy). Ngoài map: hai bên là tường, trên dưới là khoảng trống. `hasFloorAhead` là phép dò mép vực dùng chung cho PatrolSystem và EnemyBrainSystem |
| `TileType.java` | 33 | Loại tile: SOLID, PLATFORM, LADDER, SPIKE, TOXIC, BREAKABLE… |
| `PhysicsSettings.java` | 44 | Thông số di chuyển dùng chung với tool tạo map (tốc độ, độ cao và khoảng cách nhảy), đổi từ tile sang đơn vị thế giới |
| `EnemySpawn.java` | 16 | Một vị trí đặt enemy (toạ độ, hướng, đoạn tuần tra, file định nghĩa) |
| `TrialEnemies.java` | 122 | Đặt thử 4 enemy thế hệ 2 (crawler, 2 flyer, Ravager) vào các phòng sau phòng xuất phát, cho tới khi tool tạo map tự đặt |
| `PlayerRespawn.java` | 42 | Đưa player về điểm hồi sinh, đứng yên, bất tử một lúc |
| `LightSource.java` | 61 | Một nguồn sáng của map (chỉ để vẽ) |

### 3.3 `entity/` — entity và các factory

| File | Dòng | Vai trò |
|------|-----:|---------|
| `Entity.java` | 76 | Túi component: luôn có Transform; Movement, Collider, Animation, Weapon, Arsenal, Patrol, Attack, Health, Sprite, Effect, Projectile, Door, Brain, EnemyEffects và Controller là tuỳ chọn (null nếu không có) |
| `EntityFactory.java` | 237 | Dựng player (animation nhiều tư thế, bàn tay, hitbox) và cửa từ định nghĩa JSON |
| `EnemyFactory.java` | 290 | Dựng enemy từ file của tool enemy: enemy thế hệ 1 (cận chiến đơn giản) và thế hệ 2 (crawler, flyer, mini boss: hitbox theo khung, não AI, hiệu ứng). Đổi các lớp định tính (LOW / MEDIUM / HIGH) thành con số máu và sát thương |
| `WeaponFactory.java` | 176 | Dựng vũ khí (sprite, hiệu ứng súng) và gắn vào tay entity: một vũ khí, hoặc một kho vũ khí để đổi qua lại. Áp profile attachment |
| `CharacterDefinition.java` | 29 | Phần của file JSON player mà game dùng (khung hình, drawScale, collider, `weaponLayer`, animations) |
| `EnemyDefinition.java` | 150 | Phần của `character.json` enemy mà game dùng: anchor, sprite sheet, animation (frame, hitbox, event), khối gameplay, khối `effects` |

### 3.4 `component/` — dữ liệu thuần

| File | Dòng | Chứa gì |
|------|-----:|---------|
| `TransformComponent.java` | 18 | Vị trí (chân), vận tốc, hướng mặt |
| `MovementComponent.java` | 36 | Tốc độ, lực nhảy, trạng thái chạm đất / bị chặn / trên thang / ngồi / bay |
| `ColliderComponent.java` | 17 | Hộp va chạm quanh chân (rộng, cao, cao khi ngồi) |
| `AnimationComponent.java` | 96 | Các bộ animation theo tư thế, animation đang chạy, thời gian, drawScale, footOffset. 5 map của tư thế đang dùng (animation, tay, khung gốc, lớp tay trước, attack box) là private, chỉ đổi cùng lúc qua `useStance(name)` |
| `HealthComponent.java` | 60 | Máu và trạng thái chết được đóng gói: chỉ đổi qua `applyDamage`, `restoreFull`, `setHealthClamped` (luôn 0 ≤ máu ≤ max, chết ⇔ máu = 0). Bộ đếm bị thương / bất tử, kháng đẩy lùi để public |
| `AttackComponent.java` | 24 | Trạng thái đòn cận chiến, sát thương |
| `WeaponComponent.java` | 47 | Vũ khí đang cầm và cách cầm (kích thước, gốc xoay, anchor tay); với súng có thêm băng đạn, hồi chiêu, đang nạp |
| `ArsenalComponent.java` | 21 | Danh sách vũ khí mang theo, ô đang cầm, yêu cầu đổi |
| `PatrolComponent.java` | 16 | Đoạn tuần tra (minX, maxX, hướng) |
| `EnemyBrainComponent.java` | 41 | Thông số và trạng thái AI săn đuổi (tầm phát hiện, tầm đánh, hồi chiêu, độ cao bay…) |
| `EnemyEffectsComponent.java` | 39 | Hiệu ứng enemy theo khung hình (cue) và khung đã xử lý gần nhất |
| `SpriteComponent.java` | 25 | Sprite đặt theo điểm gốc (hiệu ứng, đạn, vỏ đạn) |
| `EffectComponent.java` | 19 | Thời gian sống còn lại, ánh sáng đi kèm, dừng khi chạm đất (vỏ đạn) |
| `ProjectileComponent.java` | 19 | Viên đạn đang bay: chủ, sát thương, tầm còn lại |
| `DoorComponent.java` | 30 | Một cửa: vùng kích hoạt, phòng đi, phòng đến, điểm xuất hiện |

### 3.5 `input/` — điều khiển

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `Controller.java` | 35 | Interface ý định của player (đi, nhảy, leo, đánh, nạp, đổi vũ khí). Enemy không dùng controller | — |
| `KeyboardController.java` | 86 | Bàn phím + chuột cho player, mỗi hành động nhận nhiều phím | — |
| `InputSystem.java` | 271 | Ý định → di chuyển: đi, nhảy (coyote time, jump buffer), ngồi, xuống platform một chiều, leo thang, yêu cầu tấn công / bắn / nạp | Đọc Controller, Movement · Ghi Transform, Attack, Weapon |
| `CameraInput.java` | 108 | Camera tự do (F2 hoặc không có player) và zoom khi đi theo player | Ghi CameraController |

### 3.6 `ai/` — trí tuệ enemy

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `PatrolSystem.java` | 59 | Đi qua lại trong đoạn tuần tra, quay đầu ở mép, tường, vực | Đọc Patrol, Movement, Collider · Ghi Transform |
| `EnemyBrainSystem.java` | 157 | Phát hiện player trong tầm → quay mặt, đuổi; vào tầm đánh và hết hồi chiêu → tấn công (mini boss xen kẽ 2 đòn); flyer bay lượn và lao xuống | Đọc Brain, Animation · Ghi Transform, Animation |

### 3.7 `physics/` — vật lý, va chạm, vùng nguy hiểm

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `PhysicsSystem.java` | 44 | Trọng lực (trừ lúc trên thang hoặc bay), rồi di chuyển theo vận tốc | Ghi Transform, Movement |
| `CollisionSystem.java` | 126 | Đẩy hộp va chạm qua lưới tile theo từng trục: tường chặn ngang, đụng đầu, đáp xuống SOLID / PLATFORM / đỉnh thang | Ghi Transform, Movement |
| `HazardSystem.java` | 107 | Checkpoint, bẫy SPIKE / TOXIC, rơi khỏi map, lối ra. Phát `CheckpointEvent` và `ExitReachedEvent` | Ghi điểm hồi sinh, vị trí player |
| `Hitboxes.java` | 133 | Tiện ích tính hộp thân và hộp tấn công trong toạ độ thế giới (đổi từ toạ độ sprite, lật khi quay trái), ghi vào Rectangle có sẵn | — |

### 3.8 `combat/` — chiến đấu, vũ khí

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `AttackSystem.java` | 108 | Đòn cận chiến theo animation ATTACK: khung có attack box gây sát thương phía bên kia, phá tile BREAKABLE (sửa trực tiếp TiledMap), mỗi cú vung trúng tối đa một lần | Đọc Animation, Attack · Ghi Health (qua Damage), TileGrid |
| `ContactDamageSystem.java` | 46 | Enemy chạm player thì gây sát thương | Ghi Health của player |
| `ShootingSystem.java` | 153 | Súng: bóp cò, hồi chiêu, hết đạn → nạp, R nạp sớm, sinh đạn ở nòng, lửa nòng, vỏ đạn văng | Ghi Weapon, thêm entity đạn và hiệu ứng |
| `ProjectileSystem.java` | 97 | Đạn bay theo bước ngắn (không xuyên tile), dừng ở tile cứng hoặc thân phe kia, hiệu ứng trúng | Ghi Health, xoá đạn |
| `HealthSystem.java` | 82 | Bộ đếm bị thương / bất tử, giảm đẩy lùi; enemy chết → animation DEATH → xoá; player chết → hồi sinh đủ máu | Ghi Health, Transform |
| `WeaponSwitchSystem.java` | 59 | Đổi ô vũ khí và bộ animation theo tư thế; không đổi giữa cú vung, khi bị thương hay đã chết | Ghi Arsenal, Weapon, Animation |
| `Damage.java` | 67 | Áp một đòn: trừ máu, bị thương / chết, đẩy lùi, bất tử. Dùng chung cho mọi system gây sát thương | — |
| `Effects.java` | 51 | Sinh entity hiệu ứng ngắn (lửa nòng, trúng đạn, hiệu ứng enemy) và gắn ánh sáng | — |
| `Weapon.java` | 68 | Một vũ khí đã nạp: hình, kích thước, gốc xoay, anchor (mũi, nòng, cửa văng vỏ), dữ liệu bắn, hiệu ứng | — |
| `WeaponDefinition.java` | 36 | Nội dung file JSON vũ khí | — |
| `FireDefinition.java` | 31 | Dữ liệu bắn của súng (tốc độ bắn, băng đạn, tầm, thời gian nạp…) | — |
| `EffectDefinition.java` | 20 | Một dải hiệu ứng của súng trong JSON vũ khí | — |
| `WeaponEffect.java` | 21 | Một dải hiệu ứng đã nạp (animation + điểm gốc + thời lượng). Dùng cho cả súng lẫn hiệu ứng enemy | — |
| `AttachmentProfile.java` | 35 | Profile "nhân vật X cầm vũ khí Y": ghi đè anchor tay, kích thước, gốc xoay | — |

### 3.9 `animation/` — animation và hiệu ứng

| File | Dòng | Vai trò | Đọc / Ghi |
|------|-----:|---------|-----------|
| `AnimationSystem.java` | 96 | Chọn animation theo ưu tiên DEATH > HIT > ATTACK > SHOOT > leo > CROUCH > JUMP > WALK > IDLE, tăng thời gian | Đọc Transform, Movement, Health · Ghi Animation |
| `AnimationFrames.java` | 51 | Tiện ích đọc khung hiện tại (khung vẽ, khung gốc, anchor tay, thời lượng, đã chạy xong chưa) | — |
| `AnimationSet.java` | 39 | Bộ animation của một tư thế (mặc định, PISTOL, RIFLE) | — |
| `AnimationType.java` | 22 | Enum trạng thái: IDLE, WALK, JUMP, ATTACK, ATTACK_2, DEFEND, HIT, DEATH, CROUCH, SHOOT | — |
| `AnimationDefinition.java` | 41 | Một animation trong JSON player (sheet, dải tay, khung, anchor tay, hitbox) | — |
| `FrameDefinition.java` | 16 | Một khung: thời lượng, sự kiện, attack box | — |
| `HandAnchor.java` | 12 | Vị trí và góc của bàn tay cầm vũ khí trong một khung | — |
| `EffectSystem.java` | 39 | Chạy sprite hiệu ứng và xoá khi hết giờ; vỏ đạn chạm đất thì dừng | Ghi Sprite, Effect, xoá entity |
| `EnemyEffectSystem.java` | 66 | Khi animation enemy tới khung có cue thì sinh hiệu ứng đúng chỗ (lật theo hướng), mỗi cue một lần mỗi lượt chạy | Đọc EnemyEffects, Animation · thêm entity hiệu ứng |

### 3.10 `rendering/` — vẽ thế giới và lớp phủ

| File | Dòng | Vai trò |
|------|-----:|---------|
| `RenderingSystem.java` | 176 | Vẽ các layer tile của map và entity xen giữa (tường → decor sau → terrain → props → entity → decor trước). Mỗi nhân vật: thân → vũ khí → bàn tay trước |
| `CameraController.java` | 170 | Camera đi theo player bằng lò xo giảm chấn (SmoothDamp), bị giới hạn trong phòng hiện tại (nghe `RoomTransitionEvent`) |
| `LightingRenderer.java` | 173 | Lớp ánh sáng phủ lên cảnh: bóng tối nền + đèn của map, đèn của player, lửa nòng (L bật / tắt) |
| `HudRenderer.java` | 97 | Thanh máu, tên vũ khí và đạn, dòng gợi ý phím |
| `StatsPanel.java` | 340 | Bảng nhân vật (I / F1): chỉ số player và thẻ từng vũ khí, kiêm chọn vũ khí |
| `DebugRenderer.java` | 166 | Lớp debug F3: tile va chạm, hộp, attack box, đoạn tuần tra, checkpoint, giới hạn camera, cửa |
| `FadeRenderer.java` | 40 | Lớp đen khi qua cửa |

### 3.11 `ui/` — menu, minimap, giao diện

| File | Dòng | Vai trò |
|------|-----:|---------|
| `MenuScreen.java` | 335 | Màn hình chính: nền boong-ke, tên game, người lính và Ravager chuyển động, khung menu. Dựng các trang Continue, New Game (chế độ → ô lưu → xác nhận ghi đè), Load, Manage Saves, Settings, Quit từ file save |
| `MenuView.java` | 517 | Vẽ ngăn xếp trang `Menu` thành khung terminal theo bản thiết kế; xử lý bàn phím và chuột. Dàn trang trong toạ độ thiết kế 1280×720 |
| `Menu.java` | 187 | Model một trang menu (crumb, chip, dòng chữ, bảng phím, mục: chi tiết, công tắc, khoá, nguy hiểm, thẻ ô lưu), API dạng chuỗi lệnh |
| `UiTheme.java` | 160 | Bảng màu (chép từ bản thiết kế HTML), font FreeType theo tỉ lệ cửa sổ, texture sọc cảnh báo, pixel trắng |
| `PauseMenu.java` | 74 | Menu tạm dừng (Esc) và màn "Mission complete" |
| `SettingsMenu.java` | 52 | Trang Settings (công tắc toàn màn hình, VSync, FPS) và bảng phím, dùng chung cho cả 2 menu |
| `Toast.java` | 76 | Thông báo ngắn ở đáy màn hình ("SAVE  Checkpoint 3 of 7 - saved") |
| `FpsOverlay.java` | 44 | Hiện FPS ở góc phải trên |
| `MinimapState.java` | 61 | Phòng đã thăm / đã biết, chỉ cập nhật qua `RoomTransitionEvent`; khôi phục được từ save |
| `MinimapRenderer.java` | 229 | Vẽ minimap góc phải trên (M mở cả bản đồ) |

### 3.12 `save/` — lưu game và cài đặt

| File | Dòng | Vai trò |
|------|-----:|---------|
| `SaveData.java` | 57 | Cấu trúc JSON của một ván lưu (schema phiên bản 1) (phiên bản, chế độ, map + chữ ký map, vị trí, điểm hồi sinh, checkpoint, máu, vũ khí + đạn, phòng đã khám phá, thời gian chơi, hoàn thành) |
| `SaveSnapshot.java` | 122 | Chụp `World` thành `SaveData` (`capture`) và đặt `SaveData` vào thế giới vừa nạp (`apply`); kiểm tra chữ ký map. Không đọc ghi file, không cần LibGDX nên test được |
| `SaveManager.java` | 156 | 3 ô lưu ở `~/.mygame/saves/slot_N.json`. Đọc không bao giờ ném lỗi: EMPTY / OK / CORRUPT / INCOMPATIBLE. Ghi qua file tạm. Constructor thứ hai nhận thư mục và hàm kiểm tra map (dùng cho test) |
| `Settings.java` | 61 | Cài đặt ở `~/.mygame/settings.json`, áp chế độ hiển thị |
| `GameSession.java` | 27 | Ván đang chơi: ô lưu, chế độ, save đang tiếp tục, thời gian chơi, đã hoàn thành |

### 3.13 `util/` — tiện ích

| File | Dòng | Vai trò |
|------|-----:|---------|
| `Constants.java` | 64 | Hằng số toàn cục: map khởi đầu, player và vũ khí mặc định, danh sách enemy thử nghiệm, đường dẫn profile, khung hình tối đa |
| `ResourceLoader.java` | 94 | Nạp và cache texture, TiledMap (qua AssetManager), JSON (nghiêm ngặt cho file của game, bỏ qua trường lạ cho file của tool); `unloadMap` |
| `SmoothDamp.java` | 56 | Lò xo giảm chấn tới hạn cho camera |

### 3.14 `lwjgl3/` — launcher desktop

| File | Dòng | Vai trò |
|------|-----:|---------|
| `Lwjgl3Launcher.java` | 47 | Cấu hình cửa sổ (tiêu đề "MyGame", 1280×720, VSync, FPS) và chạy `MyGame` |
| `StartupHelper.java` | 226 | Mã bên thứ ba đi kèm template LibGDX (damios, giấy phép Apache 2.0): khởi động lại JVM khi cần (macOS, một số trường hợp trên Windows). Không nên sửa |

---

## 4. Dữ liệu ngoài code

| Thư mục | Nội dung | Ai tạo ra | Ai đọc |
|---------|----------|-----------|--------|
| `assets/maps/<name>/` | `map.tmx`, tileset, `level.json` | tool map (`build_map`) | `WorldLoader`, `RoomLoader` |
| `assets/sprites/players/<id>/` | `<id>.json` + dải animation thân và tay | tool player (`build_character`) | `EntityFactory` |
| `assets/sprites/weapons/<id>/` | `<id>.json`, hình, `fx/` | tool vũ khí (`build_weapon`) | `WeaponFactory` |
| `assets/attachments/<character>/` | cách nhân vật cầm từng vũ khí | tool vũ khí | `WeaponFactory` |
| `assets/sprites/enemies/<id>/` | `character.json`, sprite sheet, `effects/` | tool enemy (`build_enemy`), chép qua tool map | `EnemyFactory` |
| `assets/ui/` | font, sọc cảnh báo, nền màn hình chính | tool UI (`build_ui`) | `UiTheme`, `MenuScreen` |
| `~/.mygame/` | save và cài đặt của người chơi | game | `SaveManager`, `Settings` |

---

## 5. Các luồng chính

**Tạo player:** `WorldLoader` → `EntityFactory.createPlayer` (JSON player, animation nhiều tư thế, bàn tay) → `WeaponFactory` (kho 3 vũ khí, profile attachment) → `World.setPlayer`.

**Đòn đánh của enemy:** `EnemyBrainSystem` chọn ATTACK / ATTACK_2 → `AnimationSystem` chạy → `AttackSystem` lấy attack box của khung hiện tại (qua `Hitboxes.fromSprite`) → `Damage.apply` lên player → `HealthSystem`. Song song, `EnemyEffectSystem` sinh hiệu ứng (gầm, sóng chấn động) qua `Effects.spawn`.

**Qua cửa:** `DoorSystem` thấy player chạm cửa → tối dần (thế giới dừng) → dời player → phát `RoomTransitionEvent` → `CameraController` giới hạn theo phòng mới, `MinimapState` đánh dấu phòng → sáng lại.

**Lưu game:** `HazardSystem` phát `CheckpointEvent` → `GameScreen.save(CHECKPOINT)` → `SaveSnapshot.capture` → `SaveManager.write` → `Toast`. Khi về menu, thoát hoặc đóng cửa sổ (`pause()`), `save(QUIT)` ghi vị trí đang đứng; nếu lúc đó không an toàn (đang chết, đang qua cửa) thì ghi vị trí checkpoint. Continue: `MyGame.startRun` → `GameScreen.applySave` → `SaveSnapshot.apply` đặt lại vị trí, điểm hồi sinh, máu, đạn, vũ khí, minimap (bỏ vị trí và minimap nếu chữ ký map khác).

---

## 6. Điểm cần lưu ý khi đánh giá

Trạng thái sau đợt refactor OOP ngày 2026-11-21 (báo cáo kiểm tra F1–F14).

**Đã xử lý**
- F1: logic lưu và khôi phục tách khỏi `GameScreen` sang `save/SaveSnapshot` (`GameScreen` từ 458 xuống 372 dòng).
- F2: `HealthComponent` đóng gói máu và trạng thái chết; mọi nơi ghi đều đi qua API.
- F3: đổi tư thế animation chỉ ở `AnimationComponent.useStance`.
- F4: dò mép vực gom về `TileGrid.hasFloorAhead`.
- F5: bảng nhân vật đọc phím đổi vũ khí qua `Controller` thay vì tự đọc bàn phím.
- F6: `World.setRespawnPoint`.
- F11, F12, F13: javadoc của `Controller`, `ContactDamageSystem` và `02-ARCHITECTURE.md` khớp code.

**Còn lại (đã cân nhắc, chưa làm)**
- `MenuView` (517 dòng), `StatsPanel` (340), `MenuScreen` (335), `WorldLoader` (306), `EnemyFactory` (290) vẫn lớn.
- Phụ thuộc vòng giữa package: `world` ↔ `physics` (`DoorSystem` dùng `Hitboxes`; `HazardSystem`, `HealthSystem` dùng `PlayerRespawn`), `core` ↔ `world` / `physics` (EventBus và các sự kiện). Chấp nhận, chưa dời package (F7).
- Hiệu ứng nằm ở 2 package (`animation`, `combat`); hiệu ứng enemy dùng class tên `WeaponEffect` (F8, chưa đổi tên).
- `Entity` không có id; mỗi loại component là một field cố định (F9, F10, giữ nguyên có chủ ý).
- `AttackSystem` phá tường bằng cách sửa TiledMap đang cache, được bù bằng `ResourceLoader.unloadMap` (F14). Tường đã phá và quái đã giết chưa được lưu.
- Enemy không có `Controller`; AI ghi thẳng vào component. Chuyển sang AI controller sẽ đổi thứ tự update nên chưa làm.
- Số liệu gameplay của enemy (máu, sát thương, tầm phát hiện, hồi chiêu) vẫn là hằng số trong `EnemyFactory`.
- Bảng màu UI có 2 bản (file HTML thiết kế và `UiTheme.java`), đổi phải chép tay.

**Kiểm thử**
- JUnit 5 (`core/src/test`, chạy `gradlew core:test`): `HealthComponent`, `Damage`, `HealthSystem`, `AnimationComponent`, `SaveSnapshot`, `SaveManager` (kèm file save thật làm mẫu tương thích ngược), `TileGrid`, `PatrolSystem`, `EventBus`, `MinimapState`.
- Chưa có test cho phần vẽ, input thật, `HazardSystem` (dùng `Gdx.app` để log) và AI săn đuổi.
