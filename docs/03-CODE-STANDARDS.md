# 03 - Quy tắc viết code

[← Mục lục](README.md)

Quy tắc bắt buộc cho người và AI khi viết code trong `core/`. Kiến trúc đứng sau các quy tắc này:
[02-ARCHITECTURE.md](02-ARCHITECTURE.md).

## 1. Quy tắc bắt buộc

| # | Quy tắc | Lý do |
|---|---------|-------|
| 1 | Code nằm trong `com.hung.<domain>` thuộc `core/`; `lwjgl3/` chỉ cấu hình cửa sổ | Logic game không phụ thuộc nền tảng |
| 2 | Khả năng dùng chung là component + system; điểm khác giữa các loại nhân vật là override trong cây `GameCharacter` ([mục 6](#6-entity-nhân-vật-và-factory)) | Dùng lại logic chung, mỗi loại tự nói mình khác ở đâu |
| 3 | Component là dữ liệu; hành vi nằm trong system | Mỗi phần một trách nhiệm |
| 4 | Mọi chuyển động nhân với `delta` | Không phụ thuộc tốc độ khung hình |
| 5 | Không số "ma thuật": hằng số `static final` có tên, đặt đầu class | Đọc được, chỉnh được |
| 6 | Nạp tài nguyên một lần (constructor hoặc lúc nạp map), không trong `update()` / `render()` | Hiệu năng |
| 7 | Không cấp phát đối tượng mới trong code chạy mỗi khung hình; dùng lại field | Tránh giật do GC |
| 8 | Không đổi thứ tự system trong `GameScreen` khi chưa có test chứng minh | Nhiều system dựa vào thứ tự ([02 → mục 3](02-ARCHITECTURE.md#3-thứ-tự-một-khung-hình)) |
| 9 | Giữ ổn định schema JSON của asset và của file lưu | Tool và file lưu cũ vẫn đọc được |
| 10 | Comment chỉ ở chỗ cần ([mục 10](#10-comment)) | Comment thừa nhanh cũ, gây nhiễu |

## 2. Package

```text
com.hung.core        MyGame, GameScreen, GameMode, Navigator        (+ core.event)
com.hung.world       World, Level, tile, phòng                      (+ world.event, world.loader, world.system)
com.hung.entity      Entity và các factory
com.hung.component   Component dữ liệu
com.hung.input       Controller, KeyboardController, InputSystem, CameraInput
com.hung.ai          System AI của quái
com.hung.physics     Vật lý, va chạm tile, vùng nguy hiểm, hitbox
com.hung.combat      Damage, Effects, Weapon, WeaponEffect          (+ combat.definition, combat.system)
com.hung.animation   Kiểu animation dùng chung                      (+ animation.definition, animation.system)
com.hung.rendering   Vẽ map và entity, camera, ánh sáng, HUD, debug
com.hung.ui          UiTheme, Toast, FpsOverlay                     (+ ui.menu, ui.minimap)
com.hung.save        Dữ liệu lưu, snapshot, file lưu, settings
com.hung.util        Constants, GameLog, ResourceLoader, SmoothDamp
```

- Đặt class mới vào domain của nó; không tạo package `models/`, `services/`, `helpers/`, `managers/`.
- Package con chỉ khi nhóm class có trách nhiệm riêng rõ ràng và không chỉ có một class.
- Package con được phụ thuộc package gốc của domain; package gốc không phụ thuộc package con.
- Không tạo thêm vòng phụ thuộc giữa package.

## 3. Đặt tên

| Loại | Quy ước | Ví dụ |
|------|---------|-------|
| Class | PascalCase; component kết thúc bằng `Component`, system bằng `System` | `HealthComponent`, `CollisionSystem`, `EnemyFactory` |
| Interface | Danh từ chỉ vai trò, không tiền tố `I` | `Controller`, `EventListener<T>`, `Navigator` |
| Method | camelCase, bắt đầu bằng động từ | `update()`, `applyDamage()`, `getHealth()` |
| Biến | camelCase | `position`, `checkpointIndex` |
| Hằng số | SCREAMING_SNAKE_CASE | `COYOTE_TIME`, `PLAYER_MAX_HEALTH` |
| Boolean | Tiền tố `is` / `has` / `can` / `should` | `isGrounded`, `hasHit`, `isFacingRight` |

## 4. Cấu trúc class

Thứ tự thành viên: hằng số → field → constructor → method public → method private. Getter / setter một dòng có thể
đặt liền nhau.

```java
package com.hung.ai;

/**
 * Simple enemy AI: walk back and forth inside the patrol range, turning at its ends,
 * at walls and before stepping off a ledge.
 * Reads: PatrolComponent, MovementComponent, ColliderComponent. Writes: TransformComponent, PatrolComponent.
 */
public class PatrolSystem {

    private final World world;

    public PatrolSystem(World world) {
        this.world = world;
    }

    public void update() {
        TileGrid grid = world.getLevel().getGrid();
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            PatrolComponent patrol = entity.getPatrol();
            MovementComponent movement = entity.getMovement();
            ColliderComponent collider = entity.getCollider();
            if (patrol == null || movement == null || collider == null) continue;
            // ... quay đầu ở hai đầu đoạn, ở tường và mép vực, rồi đặt vận tốc đi bộ
        }
    }
}
```

Javadoc của system ghi nó **đọc** và **ghi** component nào. File nên dưới khoảng 400 dòng; lớn hơn thì xem có tách
được trách nhiệm không.

## 5. Component

```java
/** Where an entity is and how it is moving. World units are map pixels; y points up. */
public class TransformComponent {
    public final Vector2 position = new Vector2();   // điểm chân
    public final Vector2 velocity = new Vector2();
    public boolean isFacingRight = true;
}
```

- Component chỉ chứa dữ liệu, mặc định field public.
- **Được** có method nhỏ giữ dữ liệu của chính nó luôn hợp lệ khi một quy tắc trải trên nhiều field. Khi đó field
  chuyển private và mọi thay đổi đi qua method: `HealthComponent.applyDamage / restoreFull / setHealthClamped`,
  `AnimationComponent.useStance`.
- Luật game liên quan tới component khác (sát thương, knockback, hồi sinh…) nằm trong system hoặc tiện ích tĩnh
  (`Damage.apply`), không trong component.
- Component không phụ thuộc system, rendering hay UI.

## 6. Entity, nhân vật và factory

```java
Player player = PlayerFactory.createPlayer(definition, definitionPath, resources, x, y,
    new KeyboardController(), physics);
Enemy enemy = EnemyFactory.createEnemy(enemyDefinition, enemyPath, resources, spawn);
```

- `Entity` là túi component. Cửa, đạn, hiệu ứng là `Entity` thường; người chơi và quái là lớp con của
  `GameCharacter` ([02 → mục 4](02-ARCHITECTURE.md#4-entity-component-và-cây-nhân-vật)).
- Factory lắp component từ JSON; nơi gọi không tự lắp. `EnemyFactory.newEnemy` là nơi duy nhất chọn lớp quái.
- Thêm một loại component: thêm field + getter/setter vào `Entity`, rồi system đọc nó.
- Thêm một loại quái: tạo lớp con của `HunterEnemy` (hoặc `Enemy`), để con số là hằng số private trong lớp đó, chỉ
  override bước khác với lớp cha, thêm một nhánh vào `EnemyFactory.newEnemy`, thêm test vào `EnemyHierarchyTest`.
- Lớp con gọi lại hành vi chung của lớp cha, không chép lại; method nào lớp con không được đổi thì để `final`
  (`Enemy.updateBehaviour`, `HunterEnemy.think`, `GameCharacter.isHostileTo`).

## 7. System

- Một system một việc; duyệt `world.getEntities()` bằng vòng lặp chỉ số, bỏ qua entity thiếu component.
- System không gọi system khác. Dùng chung qua dữ liệu component hoặc tiện ích tĩnh (`Damage`, `Effects`,
  `Hitboxes`, `PlayerRespawn`, `AnimationFrames`).
- Thêm system: tạo trong constructor `GameScreen`, gọi trong `GameScreen.update()` ở đúng vị trí theo dữ liệu nó cần
  đọc; ghi vị trí đó vào [02 → mục 3](02-ARCHITECTURE.md#3-thứ-tự-một-khung-hình).
- Xoá entity trong lúc duyệt: duyệt ngược (`for (int i = size - 1; i >= 0; i--)`); thêm entity trong lúc duyệt: gom
  vào danh sách riêng rồi xử lý sau.

## 8. Interface và đa hình

```java
public interface Controller {
    Vector2 getMovementDirection();   // mỗi trục -1..1: đi (x), leo / ngồi (y)
    boolean isJumpPressed();
    boolean isAttackPressed();
    boolean isAttackHeld();           // súng liên thanh bắn tiếp khi giữ
    boolean isReloadPressed();
    int getWeaponSlotPressed();       // ô 0-based, hoặc -1
    boolean isNextWeaponPressed();
}
```

- Chỉ thêm interface khi có hợp đồng thật: nhiều cài đặt, hoặc một ranh giới cần bảo vệ (`Navigator`,
  `PauseMenu.Actions`, `EventListener<T>`).
- Code gọi làm việc qua interface (`InputSystem` chỉ biết `Controller`), không `instanceof` hay ép kiểu xuống class cụ thể.
- Trạng thái (animation, AI, chuyển phòng) là dữ liệu trong component, system quyết định chuyển trạng thái. Không có
  class state machine chung; chỉ thêm khi có hành vi thật sự cần.

## 9. Giao tiếp và sự kiện

| Cần | Làm |
|-----|-----|
| System sau xử lý kết quả của system trước | Ghi vào component (`attack.isRequested`, `movement.isGrounded`) |
| Quy tắc dùng chung nhiều system | Tiện ích tĩnh (`Damage.apply(target, amount, sourceX)`) |
| Một nơi phát, nhiều nơi nghe không nên biết nhau | Sự kiện trên `EventBus` |

```java
// Sự kiện: class dữ liệu trong package của domain (world.event), nơi phát dùng lại một đối tượng
events.publish(checkpointEvent.set(i, checkpoints.size, respawn));

// Nơi nghe: đăng ký trong constructor GameScreen; chép dữ liệu cần giữ vì đối tượng sự kiện bị dùng lại
events.subscribe(CheckpointEvent.class, event -> {
    checkpointIndex = event.index;
    save(SaveData.REASON_CHECKPOINT);
});
```

## 10. Comment

Comment để nói điều code không nói được. Viết ít, viết ngắn.

**Nên comment:**
- **Lý do** khi không nhìn thấy được: cách xử lý vòng, ràng buộc thứ tự, đánh đổi có chủ ý.
  `// Backwards, so removing an entity doesn't skip the next one`
- **Quy tắc hoặc hợp đồng** người đọc phải tôn trọng: đơn vị và hệ toạ độ, bất biến, "gọi sau X".
- **Công thức hoặc giá trị khó hiểu** mà tên không nói lên (nguồn gốc hoặc cách tính).
- **Việc của class**: Javadoc ngắn cho class public và method public mà tên chưa nói hết (giá trị trả về ở trường
  hợp biên, thứ nó thay đổi).

**Không comment:**
- Lặp lại code (`// set the health`, `/** Returns the width. */` trên `getWidth()`).
- Dán nhãn khối hiển nhiên, kể lại các bước mà tên method đã nói.
- Lịch sử thay đổi, người sửa, code bị comment out, ghi chú về đợt sửa hay yêu cầu review.

Comment sai còn tệ hơn không có: sửa hoặc xoá trong cùng lần đổi code. Ưu tiên đặt tên rõ hơn hoặc tách method nhỏ
thay vì viết comment giải thích một đoạn rối.

## 11. Tài nguyên và hiệu năng

- Texture và map nạp qua `ResourceLoader` (AssetManager): xếp hàng trước, `finishLoading()` một lần, rồi mới tạo entity.
- Pixel art dùng filter `Nearest`; điểm chân nhân vật làm tròn về đơn vị thế giới nguyên trước khi vẽ.
- Hộp va chạm trả về qua `Rectangle` do nơi gọi sở hữu (`Hitboxes.body(entity, out)`), không tạo mới mỗi khung.
- JSON của game đọc nghiêm ngặt (`loadOptionalDefinition`); JSON do tool sinh ra đọc bỏ qua trường lạ (`loadExternalDefinition`).

## 12. Kiểm thử

- JUnit 5 trong `core/src/test/java`, cùng package với class được test. Chạy: `.\gradlew.bat core:test`.
- Test phần logic thuần, không cần LibGDX runtime: component, `Damage`, `SaveSnapshot`, `SaveManager` (truyền thư mục
  tạm), `TileGrid`, `PatrolSystem`, `EventBus`, `MinimapState`. Dựng `World` / `Level` với map `null`.
- Code gọi `Gdx.app` / `Gdx.files` trực tiếp không chạy được trong test; cho phép truyền phụ thuộc vào (như constructor
  thứ hai của `SaveManager`) khi cần test.
- Phần vẽ, input thật và AI săn đuổi kiểm tra bằng cách chạy game.

## 13. Quy trình cho AI khi viết code

1. Đọc [02-ARCHITECTURE.md](02-ARCHITECTURE.md) và phần liên quan trong [05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md).
2. Tìm file liên quan trong [01-SOURCE-MAP.md](01-SOURCE-MAP.md) và đọc code thật trước khi sửa.
3. Xác định dữ liệu nằm ở component nào, hành vi ở system nào, system đứng ở đâu trong thứ tự khung hình.
4. Viết code theo các mục trên; viết hoặc cập nhật test cho phần logic.
5. Chạy `.\gradlew.bat core:test core:compileJava lwjgl3:compileJava` (không chạy khi cửa sổ game đang mở).
6. Cập nhật docs liên quan (`01-SOURCE-MAP.md` khi thêm / dời / xoá file).

**Checklist trước khi giao code:**
- [ ] Đúng package, không logic trong `lwjgl3`
- [ ] Lớp con chỉ trong cây `GameCharacter`; component chỉ là dữ liệu (trừ method giữ bất biến của chính nó)
- [ ] Chuyển động nhân `delta`; không số ma thuật
- [ ] Không nạp tài nguyên hay cấp phát đối tượng trong code mỗi khung hình
- [ ] Không đổi thứ tự system, schema JSON, schema file lưu, phím điều khiển nếu không được yêu cầu
- [ ] Import sạch; comment đúng chỗ, ngắn, còn đúng
- [ ] Biên dịch được và test qua
