# Nhân vật và vũ khí

[← Mục lục](README.md)

Cách nạp, gắn và vẽ vũ khí lên tay nhân vật. Toạ độ chung và định dạng JSON của player / vũ khí:
[05-SYSTEMS-GUIDE.md → mục 0](05-SYSTEMS-GUIDE.md#0-toạ-độ-và-file-dữ-liệu).

| Mục | Nội dung |
|-----|----------|
| [1](#1-ý-tưởng) | Ý tưởng |
| [2](#2-asset-hiện-có) | Asset hiện có |
| [3](#3-các-mảnh-trong-code) | Các mảnh trong code |
| [4](#4-anchor-tay) | Anchor tay |
| [5](#5-từ-anchor-ra-thế-giới) | Từ anchor ra thế giới (vẽ, lật, xoay) |
| [6](#6-profile-cầm-vũ-khí) | Profile cầm vũ khí (nhân vật × vũ khí) |
| [7](#7-điểm-trên-vũ-khí-mũi-dao-đầu-nòng) | Điểm trên vũ khí: mũi dao, đầu nòng |
| [8](#8-thêm-vũ-khí) | Thêm vũ khí |

## 1. Ý tưởng

Thân nhân vật và vũ khí là **hai asset riêng**. Dải animation của nhân vật chỉ có thân; vũ khí là một hình riêng. Mỗi
khung hình, renderer vẽ thân, rồi vẽ vũ khí tại bàn tay của khung đó, cùng hướng quay.

- Nhân vật nào có anchor tay đều cầm được vũ khí nào.
- Không cần sprite sheet "nhân vật cầm vũ khí".
- Thêm vũ khí không đụng code animation, và ngược lại.
- Mọi con số riêng của asset (kích thước, điểm cầm, anchor) nằm trong JSON cạnh asset, không nằm trong code vẽ.

## 2. Asset hiện có

### Nhân vật `sprites/players/player_soldier/`

| File | Nội dung |
|------|----------|
| `<anim>.png` | Một hàng khung 64 × 64: thân **không** có vũ khí. Bộ mặc định: `idle`, `walk`, `attack_knife`, `hit`, `death`, `crouch`, `jump`; bộ súng lục `*_pistol` và súng trường `*_rifle`: `idle`, `walk`, `crouch`, `jump`, `shoot` |
| `<anim>_hand.png` | Cùng khung, chỉ có bàn tay trước (vẽ đè lên báng / tay cầm của vũ khí) |
| `player_soldier.json` | `weaponLayer: "SEPARATE"`, `drawScale 0.5`, mỗi animation: `sheet`, `handSheet`, `frames[]`, `handFrames` (anchor tay của từng khung) |

Thân thấy được cao ≈ 53 px → ≈ 26,5 đơn vị thế giới (≈ 1,7 tile).

### Vũ khí `sprites/weapons/<id>/`

| Vũ khí | Hình | `pivot` (điểm cầm) | Điểm khác | Tư thế |
|--------|------|--------------------|-----------|--------|
| `combat_knife` | 24 × 9 | (5, 4) | `tipAnchor` (22, 3) | mặc định |
| `service_pistol` | 16 × 14 | (4, 7) | `muzzleAnchor` (15, 3), `ejectionAnchor` (7, 2), `supportAnchor` (3, 8) | `PISTOL` |
| `assault_rifle` | 31 × 14 | (18, 6), tay trước dưới ốp lót | `muzzleAnchor` (30, 5), `ejectionAnchor` (11, 4), `supportAnchor` (9, 7) = tay cầm (tay sau) | `RIFLE` |

- Cả ba có `drawScale 1`, `rotation 0`. Toạ độ là pixel của hình vũ khí, gốc trên trái.
- Hình luôn **quay mũi sang phải**; quay trái là lật khi vẽ, không có asset thứ hai.
- Súng có thêm `fire` và `effects` (`fx/muzzle_flash.png`, `fx/tracer.png`, `fx/casing.png`, `fx/impact.png`).
- Súng cầm hai tay: animation `*_PISTOL` / `*_RIFLE` (do character generator vẽ) đã đặt tay sau lên vũ khí; game chỉ đặt
  `pivot` của vũ khí lên anchor tay trước và vẽ dải bàn tay trước lên trên.

### Đơn vị và pixel art

- **1 đơn vị thế giới = 1 pixel map.** Pixel của texture đổi sang đơn vị thế giới bằng `drawScale` của từng asset.
- Texture nạp với lọc **nearest** (`ResourceLoader`).
- Điểm chân được làm tròn tới đơn vị nguyên trước khi vẽ, nên thân và vũ khí không lệch nhau và không rung khi di chuyển.
- Nhân vật và vũ khí cùng mật độ pixel nhờ profile `weaponScale 1` (mục 6).

## 3. Các mảnh trong code

```text
Entity (player)
 ├── TransformComponent    position (điểm chân), isFacingRight   ← một hướng chung cho thân và vũ khí
 ├── AnimationComponent    animation theo tư thế, currentType, stateTime, anchor tay, dải bàn tay trước
 ├── ArsenalComponent      các ô vũ khí, ô đang cầm
 ├── WeaponComponent       vũ khí đang cầm + kích thước / điểm cầm / anchor đã tính cho nhân vật này
 │     └── Weapon          TextureRegion, drawScale, pivot, rotation, các anchor, fire, hiệu ứng (dùng chung)
 └── RenderingSystem       drawCharacter → drawWeapon → drawCharacter(dải bàn tay trước)
```

| Mảnh | Package | Việc |
|------|---------|------|
| `HandAnchor` | `animation` | Vị trí bàn tay trong một khung (`x`, `y`, `rotation`) |
| `AnimationDefinition` | `animation.definition` | `hand` / `handFrames`, `handSheet` của một animation trong JSON nhân vật |
| `AnimationComponent` | `component` | Animation, anchor tay và dải bàn tay trước (`getOverlays`) theo `AnimationType`, theo tư thế |
| `AnimationFrames.handAnchor` | `animation` | Anchor tay của khung hiện tại: của profile nếu có, không thì của nhân vật |
| `WeaponDefinition` | `combat.definition` | Dữ liệu JSON của một vũ khí |
| `Weapon` | `combat` | Vũ khí lúc chạy, dùng chung cho mọi người cầm |
| `AttachmentProfile` | `combat.definition` | JSON: một nhân vật cầm một vũ khí thế nào (mục 6) |
| `WeaponComponent` | `component` | `equipped`, `aimRotation`, `width` / `height` / `originX` / `originY`, `handAnchors` đã tính cho người cầm; trạng thái súng (`ammo`, `cooldown`…) |
| `WeaponFactory` | `combat` | `createWeapon` (texture + hiệu ứng), `createWeaponSlot` (áp profile, tính kích thước và anchor), `equipArsenal` |
| `WorldLoader` | `world.loader` | Nạp JSON vũ khí, tìm profile, trang bị cho player |
| `RenderingSystem` | `rendering` | Vẽ thân, vũ khí, bàn tay trước của từng entity trong một lượt batch |
| `Hitboxes` | `physics` | Vị trí thế giới của một điểm trên vũ khí (mục 7) |

Vẽ vũ khí ngay sau thân của chính nhân vật đó, nên nhân vật vẽ sau che được cả thân lẫn vũ khí của nhân vật trước.
Code animation không biết gì về vũ khí; anchor tay chỉ là dữ liệu của nhân vật.

## 4. Anchor tay

Trong JSON nhân vật, mỗi animation:

```json
{ "type": "WALK", "hand": { "x": 46, "y": 18, "rotation": 0 } }
{ "type": "JUMP", "handFrames": [ { "x": 39, "y": 35, "rotation": -75 }, "... mỗi khung một cái" ] }
```

| Trường | Ý nghĩa |
|--------|---------|
| `hand` | Một anchor cho mọi khung của animation |
| `handFrames` | Mỗi khung một anchor (phải đúng `frameCount` phần tử); ưu tiên hơn `hand` |
| `x`, `y` | Vị trí bàn tay, **pixel khung, gốc trên trái** (đọc thẳng từ trình sửa ảnh) |
| `rotation` | Góc vũ khí ở tư thế đó, độ, ngược chiều kim đồng hồ, nhìn khi quay phải (+ = mũi chếch lên) |

- Đây là anchor **mặc định** của nhân vật với mọi vũ khí; profile (mục 6) thay được cho riêng một vũ khí.
- Animation không có anchor (cả profile lẫn nhân vật) → không vẽ vũ khí ở trạng thái đó.
- Khung lặp do thời lượng riêng từng khung được đổi về khung gốc (`keyFrameSources`) trước khi chọn anchor.
- Toạ độ liên tục: pixel `(i, j)` phủ `[i, i+1) × [j, j+1)`, tâm `(i + 0.5, j + 0.5)`.

## 5. Từ anchor ra thế giới

```text
chân    = round(position.x), round(position.y)                ← đơn vị nguyên
offsetX = (hand.x − frameWidth / 2)            × drawScale nhân vật
offsetY = (frameHeight − hand.y − footOffset)  × drawScale nhân vật
tay     = chân + (offsetX × hướng, offsetY)                    ← hướng = +1 phải, −1 trái
góc     = hand.rotation + weapon.rotation + aimRotation
```

`RenderingSystem.drawWeapon` gọi một lần:
`batch.draw(region, tay − origin, origin, width, height, scaleX = hướng, scaleY = 1, góc × hướng)`, tức là đặt `pivot`
của vũ khí lên bàn tay, xoay quanh `pivot`, lật khi quay trái.

| Giá trị | Nguồn |
|---------|-------|
| `offsetX`, `offsetY` | Anchor tay của khung hiện tại × `drawScale` nhân vật |
| `width`, `height`, `originX`, `originY` | `WeaponComponent` (tính một lần khi trang bị, mục 6) |
| Góc | Anchor + `rotation` của vũ khí + `aimRotation` (hiện luôn 0) |

**Quay trái:** khung thân vẽ với bề rộng âm (lật quanh điểm chân); `offsetX` đổi dấu; vũ khí vẽ với `scaleX = −1`
và góc đổi dấu, nên vũ khí chếch lên khi quay phải vẫn chếch lên khi quay trái. Hướng lưu một chỗ:
`TransformComponent.isFacingRight` (do `InputSystem` / AI đặt).

**Thứ tự vẽ một nhân vật:** thân → vũ khí (chỉ khi có anchor) → dải bàn tay trước (nếu animation có `handSheet`), để
ngón tay khép lên tay cầm. Toàn bộ nằm giữa các layer tile `props` và `decor_front`.

## 6. Profile cầm vũ khí

Profile tuỳ chỉnh cách **một** nhân vật cầm **một** vũ khí: kích thước vũ khí, điểm cầm, và anchor tay theo từng
khung. Không có profile thì dùng anchor của nhân vật và `drawScale` / `pivot` của vũ khí.

### Vị trí

- `assets/attachments/<characterId>/<weaponId>.json`, với `characterId` = `id` trong JSON nhân vật, `weaponId` = `id`
  trong JSON vũ khí (đường dẫn mẫu: `Constants.ATTACHMENT_PROFILE_PATH`).
- `WorldLoader` tìm khi trang bị vũ khí; log ghi rõ có dùng profile hay không.
- Trường `character` và `weapon` trong file phải trùng hai id đó (bắt lỗi chép nhầm file).

Hiện có 3 profile cho `player_soldier` (`combat_knife`, `service_pistol`, `assault_rifle`), đều chỉ có `weaponScale: 1`;
anchor tay lấy từ `handFrames` trong JSON nhân vật.

### Định dạng

```json
{
  "character": "player_soldier",
  "weapon": "assault_rifle",
  "weaponScale": 1,
  "pivot": { "x": 18, "y": 6 },
  "animations": {
    "WALK": {
      "auto":   [ { "x": 49, "y": 18, "rotation": 0 } ],
      "adjust": [ { "dx": 0, "dy": 0, "rotation": 0 } ]
    }
  }
}
```

| Trường | Bắt buộc | Ý nghĩa |
|--------|----------|---------|
| `character`, `weapon` | có | Id của cặp |
| `weaponScale` | không | Kích thước một pixel vũ khí tính bằng **pixel nhân vật** (1 = bằng nhau). Tỉ lệ thế giới = `weaponScale × drawScale nhân vật`. Không có → `drawScale` của vũ khí |
| `pivot` | không | Điểm cầm cho cặp này, pixel vũ khí. Không có → `pivot` của vũ khí |
| `animations` | không | Khoá là tên `AnimationType`; animation không liệt kê dùng anchor của nhân vật |
| `animations.<T>.auto` | không | Anchor tay (`x`, `y`, `rotation`) thay cho anchor của nhân vật |
| `animations.<T>.adjust` | không | Chỉnh tay cộng thêm: `dx`, `dy` (pixel khung), `rotation` (độ) |

### Cách tính (một lần, trong `WeaponFactory.createWeaponSlot`)

```text
scale    = weaponScale × drawScale nhân vật      (hoặc drawScale của vũ khí)
width    = chiều rộng hình × scale,   height = chiều cao hình × scale
originX  = pivot.x × scale,           originY = (chiều cao hình − pivot.y) × scale   ← gốc trên trái → dưới trái
base[i]  = auto[i] nếu có, không thì anchor mặc định của nhân vật
final[i] = base[i] + adjust[i]                   (x + dx, y + dy, rotation + rotation)
```

- `auto` và `adjust` mỗi cái có **1 phần tử** (dùng cho mọi khung) hoặc **N phần tử** với N = số khung của animation.
- Lỗi báo rõ khi: tên animation lạ, nhân vật không có animation đó, không có anchor nào để làm base, số phần tử sai.
- Kết quả lưu trong `WeaponComponent.handAnchors`; renderer không đọc profile.

### Thứ tự ưu tiên

| Giá trị | Từ profile | Nếu không có |
|---------|-----------|--------------|
| Anchor tay | `animations.<T>` đã tính | `handFrames` / `hand` của nhân vật → không có: ẩn vũ khí |
| Tỉ lệ thế giới | `weaponScale × drawScale nhân vật` | `drawScale` của vũ khí |
| Điểm cầm | `pivot` | `pivot` của vũ khí |
| Góc gốc | — | `rotation` của vũ khí |

## 7. Điểm trên vũ khí: mũi dao, đầu nòng

`Hitboxes.weaponPoint(entity, điểm, out)` tính vị trí thế giới của một điểm trên vũ khí (pixel vũ khí, gốc trên trái) ở
khung hiện tại, **theo đúng phép đặt của renderer**: `pivot` lên bàn tay, xoay theo góc của tay, lật khi quay trái.

| Dùng cho | Điểm | Nơi gọi |
|----------|------|---------|
| Tầm dao | `tipAnchor` (`Hitboxes.weaponTip`) | `Hitboxes.attack`: attack box của khung ATTACK được kéo dài tới mũi dao |
| Đạn và lửa nòng | `muzzleAnchor` | `ShootingSystem` |
| Vỏ đạn | `ejectionAnchor` | `ShootingSystem` |

`Hitboxes.weaponAngle` cho góc vũ khí trong thế giới (độ từ +x; quay trái = 180° − góc), dùng làm hướng bắn.

## 8. Thêm vũ khí

1. Tạo bằng weapon generator rồi `node tools/export-game.mjs`: hình (quay phải, nền trong suốt) + JSON vào
   `assets/sprites/weapons/<id>/`.
2. Tuỳ chọn: `assets/attachments/<nhân vật>/<id>.json` (`weaponScale: 1` để cùng mật độ pixel với nhân vật).
3. Đưa vào tay player: thuộc tính map `playerWeapon`, hoặc thêm đường dẫn vào `Constants.DEFAULT_PLAYER_ARSENAL`.
4. Súng cần nhân vật có bộ animation đúng `stance` (`PISTOL` / `RIFLE`); thiếu thì dùng bộ mặc định.

Không cần sửa code vẽ hay code animation.
