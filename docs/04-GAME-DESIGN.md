# 04 - Gameplay

[← Mục lục](README.md)

Người chơi làm được gì và các con số đang dùng. Con số lấy từ code (hằng số trong các class được nêu) và từ JSON của
asset; cách các system cài đặt: [05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md).

## 1. Vòng chơi

```text
Màn hình chính ─► chọn chế độ ─► vào boong-ke
     ▲                                     │ đi qua các phòng, đánh quái, chạm checkpoint (tự lưu)
     │                                     │ chết → hồi sinh ở checkpoint cuối
     └── về menu (tự lưu) ◄── Esc ─────────┤
                                           ▼
                              tới lối ra ─► "Mission complete" (ván được đánh dấu hoàn thành)
```

- **Chế độ:** mỗi chế độ một map boong-ke riêng, xuất bằng `node tools/export-game.mjs --mode <chế độ>` của tool map
  (seed và kích thước ở bảng `MODES` trong file đó):

  | Chế độ | File | Kích thước (tile) | Phòng | Checkpoint | Melee | Quái khác |
  |---|---|---|---|---|---|---|
  | Easy | `bunker_easy/map_easy.tmx` | 290 × 71 | 13 | 4 | 12 | 5 crawler, 4 wasp |
  | Medium | `bunker_medium/map_medium.tmx` | 470 × 71 | 25 | 5 | 31 | 10 crawler, 8 wasp |
  | Hard | `bunker_hard/map_hard.tmx` | 780 × 93 | 43 | 8 | 45 | 16 crawler, 12 wasp |
- **Hard đang tạm khoá:** menu hiện LOCKED, không bắt đầu hay chơi tiếp được; save Hard vẫn giữ. Mở lại: đổi `true` thành `false` ở dòng HARD của
  `GameMode`.
- **Cỡ phòng:** ô phòng rộng 22–29 tile, cao khoảng 22 (`ROOMS` trong `export-game.mjs`; trước là 28–38 × 25).
- **Chướng ngoại vật và quái:** cũng ở `ROOMS`: hố (`gapFrequency`), gai và chất độc (`hazardDensity`), bệ (`platformDensity`), khoảng cách
  giữa các vật (`featureSpacing`); quái đứng cả trên bệ cao (`enemiesOnTiers`), nhiều con chung một đoạn sàn (`enemiesPerRun`).
- **Thua:** không có Game Over; chết là hồi sinh ở checkpoint cuối với đầy máu.

## 2. Điều khiển

**Khi chơi** (`KeyboardController`, `GameScreen`):

| Hành động | Phím |
|-----------|------|
| Đi trái / phải | `A` / `D` hoặc `←` / `→` |
| Nhảy | `Space` |
| Leo thang (bám, leo, đứng lên bậc trên cùng) | `W` / `S` hoặc `↑` / `↓` |
| Ngồi | `S` / `↓` khi đứng trên mặt đất |
| Xuống khỏi bệ một chiều | `S` + `Space` |
| Đánh dao / bắn | `J` hoặc chuột trái |
| Bắn liên thanh (súng trường) | Giữ `J` / chuột trái |
| Nạp đạn | `R` (tự nạp khi hết đạn trong băng, nếu còn đạn dự trữ) |
| Mở rương (đứng sát rương) | `E` |
| Chọn dao / súng lục / súng trường; vũ khí kế tiếp | `1` / `2` / `3`; `Q` |
| Bảng nhân vật (tạm dừng; `1` `2` `3` vẫn chọn vũ khí) | `I` hoặc `F1` |
| Bản đồ toàn màn hình | `M` |
| Bật / tắt ánh sáng | `L` |
| Zoom camera / về mặc định | `=` / `-` hoặc lăn chuột; `0` |
| Menu tạm dừng | `Esc` (nếu bảng nhân vật đang mở thì Esc đóng bảng trước) |
| Camera tự do | `F2` |
| Lớp debug (tile va chạm, hộp, attack box, đoạn tuần tra, checkpoint, giới hạn phòng, cửa) | `F3` |

**Camera tự do** (`F2`, hoặc map không có player):

| Hành động | Phím |
|-----------|------|
| Di chuyển | `W` `A` `S` `D` hoặc mũi tên (`Shift` = nhanh) |
| Zoom | `Q` / `E` hoặc lăn chuột |
| Về điểm xuất phát | `Home` |
| Tới quái kế tiếp | `Tab` |

**Menu:** mũi tên lên / xuống (hoặc `W` / `S`), `Enter` / `Space` chọn, `Esc` / `Backspace` quay lại, chuột di và bấm.

> Windows + bộ gõ tiếng Việt (Telex): bộ gõ có thể nuốt các phím chữ (WASD) trước khi tới game. Chuyển sang ENG
> (`Win + Space`) hoặc dùng phím mũi tên.

## 3. Di chuyển

Tốc độ đi và nhảy lấy từ **thuộc tính `physics.*` của map** (map generator chỉ tạo những khoảng nhảy được với 80% các
con số này), không cứng trong code.

| Thông số | Giá trị (map hiện tại) | Nguồn |
|----------|------------------------|-------|
| Tốc độ đi | 6 tile/s = 96 px/s | `physics.moveSpeed` |
| Nhảy cao / xa / thời gian trên không | 3,5 tile / 6 tile / 1 s | `physics.jumpHeight`, `physics.jumpDistance` |
| Tốc độ leo thang | 4 tile/s | `PhysicsSettings` |
| Coyote time (vẫn nhảy được sau khi rời mép) | 0,08 s | `InputSystem` |
| Jump buffer (bấm sớm trước khi chạm đất) | 0,1 s | `InputSystem` |
| Hộp va chạm của người lính | 7 × 26,5 đơn vị (ngồi: cao 21,5) | JSON player × `drawScale` 0,5 |

- Điều khiển trái / phải vẫn có tác dụng khi đang trên không.
- Ngồi dừng bước đi và hạ thấp hộp va chạm; chỉ đứng dậy khi đủ chỗ.
- **Thang:** lên / xuống để bám và căn vào giữa thang; trái / phải để rời; `Space` nhảy khỏi; leo quá bậc trên cùng là
  đứng lên trên; chạm sàn là buông. Khi leo, animation IDLE được dùng.
- **Bệ một chiều:** đứng lên từ trên, nhảy xuyên qua từ dưới.
- Animation WALK chạy nhanh hơn khi đi nhanh (tối đa 2,5 lần); JUMP chạy theo vận tốc dọc (bật lên → đỉnh → rơi).

## 4. Chiến đấu

**Vũ khí mang theo:** dao (ô 1), súng lục (ô 2), súng trường (ô 3). Đổi vũ khí đổi luôn tư thế cầm của người lính;
không đổi được khi đang vung dao, đang bị thương hay đã chết; đổi khi đang nạp thì huỷ lượt nạp.

| | Dao | Súng lục | Súng trường |
|---|---|---|---|
| Kiểu | Cận chiến | Bán tự động (mỗi lần bấm 1 viên) | Liên thanh (giữ để bắn) |
| Sát thương | 10 | 12 | 11 |
| Tốc độ bắn | — | 5 viên/s | 9 viên/s |
| Băng đạn / nạp | — | 12 viên / 1,1 s | 30 viên / 1,8 s |
| Đạn dự trữ: lúc đầu / tối đa | — | 36 / 72 | 60 / 150 |
| Một hộp đạn nhặt được | — | +24 | +30 |
| Tốc độ đạn / tầm | — | 30 tile/s / 22 tile | 40 tile/s / 32 tile |
| Độ lệch | — | ±1,5° | ±2,5° |
| Tư thế cầm | Một tay | Hai tay (`*_PISTOL`) | Báng tì vai (`*_RIFLE`) |

Nguồn: dao trong `PlayerFactory` (`PLAYER_KNIFE_DAMAGE`), súng trong khối `fire` của JSON vũ khí; đạn dự trữ trong
`AmmoType` (theo tư thế cầm `PISTOL` / `RIFLE`; JSON vũ khí chỉ biết cỡ băng).

- **Đạn dự trữ:** nạp đạn chuyển đạn từ dự trữ vào băng (tối đa cỡ băng, thời gian nạp không đổi). Hết dự trữ thì không
  nạp được: bắn hết băng là hết đạn, phải nhặt hộp đạn hoặc dùng dao. Mỗi súng giữ dự trữ riêng, đổi súng không mất.

- **Dao:** khung "HIT" của animation ATTACK có attack box (kéo dài tới mũi dao). Trong khung đó hộp gây sát thương mọi
  quái chạm vào, tối đa một lần mỗi cú vung, và phá tile BREAKABLE. Đứng trên đất thì dừng lại khi vung; không đánh
  được khi đang leo.
- **Súng:** đạn ra từ đầu nòng đúng chỗ súng đang vẽ, kèm lửa nòng và vỏ đạn văng ra; đứng yên thì hiện tư thế bắn.
  Đạn được kiểm tra mỗi 2 đơn vị: dừng ở tường hoặc tile BREAKABLE (xuyên qua bệ một chiều và thang), hoặc ở thân sống
  đầu tiên của phe kia; không bao giờ trúng người bắn; hết tầm thì biến mất; không bay sang phòng khác (qua khe cửa hay giếng thang thì biến mất ở biên phòng).
- **HUD:** thanh máu, tên vũ khí, đạn trong băng / đạn dự trữ (`Service Pistol 9 / 36`), chữ `RELOADING` khi nạp,
  `NO AMMO` khi cả hai đều hết; điểm ở góc trên phải (`SCORE 1250`); dòng `E - Open Chest` giữa phía dưới khi đứng
  trong tầm mở một rương còn đóng (ẩn khi rời tầm, khi rương đã mở, khi chết hoặc ở camera tự do).
- **Bảng nhân vật (I / F1):** trái là chỉ số player chia thẻ (HEALTH có thanh máu, MOVEMENT, BODY & COMBAT, LOCATION); phải là danh sách
  3 vũ khí (hình, phím, tên; vũ khí đang cầm được tô sáng, ghi `IN HAND`) và bên dưới là thẻ chi tiết **chỉ của vũ khí
  đang cầm**: sát thương, tầm (dao: tầm với), `Mag / Res` (đạn trong băng / dự trữ, cùng số với HUD, kèm `max 12 / 72`),
  thời gian nạp (dao: thời gian vung). Phím 1 / 2 / 3 đổi vũ khí như trong game, thẻ chi tiết đổi theo.

## 5. Máu, sát thương, hồi sinh

| | Giá trị | Nguồn |
|---|---|---|
| Máu player | 100 | `PlayerFactory.PLAYER_MAX_HEALTH` |
| Bất tử sau mỗi đòn trúng (player) | 1 s, sprite nhấp nháy | `PlayerFactory.PLAYER_INVULNERABILITY` |
| Chạm thân quái | −10 máu × hệ số chế độ | `Enemy.CONTACT_DAMAGE` |
| Hồi sinh sau khi chết | animation DEATH + 1 s, đầy máu, bất tử 1,5 s | `HealthSystem`, `PlayerRespawn` |
| Xác quái biến mất | sau animation DEATH + 1,5 s | `HealthSystem.CORPSE_LINGER` |

- Bị trúng: mất máu, chạy animation HIT (bị khoá điều khiển trong lúc đó), bị đẩy lùi theo hướng ngược nguồn đòn.
- **Gai, chất độc** (chạm vào 80% phía dưới của tile) và **rơi khỏi map** (64 đơn vị dưới đáy): đưa player về
  checkpoint cuối, không trừ máu.
- **Checkpoint:** chạm vào là thành điểm hồi sinh và game tự lưu (hiện thông báo "SAVE Checkpoint n of 7 - saved").
- **Hộp cứu thương** (nhặt từ rương): hồi 25 máu, không quá 100; đầy máu hoặc đã chết thì không nhặt (hộp nằm lại).

## 6. Quái

**Quái melee** (`MeleeEnemy`; đặt trong file map, `small_melee_ghoul` / `_prison` / `_toxic` theo loại phòng): 30 máu, đi qua lại
trong đoạn tuần tra, quay đầu ở hai đầu đoạn, ở tường và trước mép vực; thấy player trong tầm thì đuổi và vung vuốt (bảng dưới).
Ba con cùng họ hình với crawler (đứng hai chân): ghoul nâu, prison xám tím có tấm sắt và còng, toxic xanh ô liu có sừng.

Mọi quái phát hiện player trong tầm, quay mặt, đuổi theo và tấn công bằng hitbox của animation. Số gốc, trước khi nhân theo chế độ:

| Loại | Class | Máu | Sát thương đòn đánh | Tầm phát hiện | Hồi chiêu | Tốc độ đuổi |
|------|-------|-----|---------------------|---------------|-----------|-------------|
| Melee (`small_melee_*`) | `MeleeEnemy` | 30 | 8 | 120 | 1,2 s | ×2,2 |
| Crawler (`crawler_01`) | `CrawlerEnemy` | 20 | 8 | 160 | 0,9 s | ×1,6 |
| Quái bay (`flyer_wasp_01`) | `FlyingEnemy` | 20 | 12 | 180 | 1,4 s | ×1,4 |

Nguồn: máu và sát thương là hằng số theo mức LOW / MEDIUM trong `EnemyFactory` (file của quái chỉ ghi mức);
tầm phát hiện, hồi chiêu, tốc độ đuổi là hằng số trong từng class (`com.hung.entity.enemy`). Mọi quái
cũng gây −10 máu khi chạm (`Enemy.CONTACT_DAMAGE`). Quái to hơn file 1,2 lần (`EnemyFactory.SIZE_SCALE`: hình, thân, tầm đánh, tốc độ).

**Độ khó theo chế độ** (`EnemySkill`, gắn với `GameMode.enemySkill`; nhân với con số ở bảng trên):

| | Easy | Medium | Hard |
|---|---|---|---|
| Máu | ×1 | ×1,25 | ×1,6 |
| Sát thương (đòn và chạm) | ×1 | ×1,25 | ×1,5 |
| Tầm phát hiện | ×1,15 | ×1,3 | ×1,5 |
| Tốc độ đuổi (tối đa 84, chậm hơn player 96) | ×1,1 | ×1,3 | ×1,5 |
| Hồi chiêu | ×0,95 | ×0,8 | ×0,65 |
| Kháng đẩy lùi | 0 | 20% | 40% |
| Nhớ player sau khi mất dấu | 1,5 s | 3 s | 5 s |
| Gọi đồng đội khi phát hiện (bán kính) | – | 96 | 160 |
| Đón đầu player đang lao tới | – | 0,12 s | 0,2 s |
| Nhảy qua hố, gai, bậc và lên bệ thấp | không | có | có |

Ở mọi chế độ: bị bắn từ xa (trong cùng phòng) thì lao tới kẻ bắn; không vung đòn khi player ở quá cao hoặc quá thấp; không đuổi qua gai hay
chất độc nếu không nhảy được; bỏ cuộc khi player xa hơn 2,5 lần tầm phát hiện. Quái chỉ săn trong **phòng của nó**: không thấy
player ở phòng khác, không đuổi qua cửa, chỉ gọi đồng đội cùng phòng. Crawler dừng ngoài vùng chạm thân thay vì đứng đè lên player.

**Cách đánh theo vùng** (`GameMode.tacticPolicy`; vùng = `region_1`…`region_4` của map, từ đầu tới lối ra). Tách hẳn với con số ở
bảng trên: đổi cách đánh không đổi máu, sát thương, tốc độ hay hồi chiêu.

| | Vùng 1 | Vùng 2 | Vùng 3 | Vùng 4 |
|---|---|---|---|---|
| Easy | Đuổi thẳng | Đuổi thẳng | Đuổi thẳng | Đuổi thẳng |
| Medium | Đuổi thẳng | Đuổi thẳng | Đánh rồi lùi | Đánh rồi lùi |
| Hard (tạm giữ như cũ) | Đuổi thẳng | Đuổi thẳng | Đuổi thẳng | Đuổi thẳng |

- **Đuổi thẳng** (`DirectChaseTactic`): áp sát, đánh khi hết hồi chiêu, đứng chờ ở khoảng dừng.
- **Đánh rồi lùi** (`HitAndRunTactic`): đánh xong lùi lại (mặt vẫn hướng player, 0,7 tốc độ đuổi) tới khi cách 1,8 tầm đánh hoặc tối đa 1 s,
  đứng chờ hồi chiêu, phản đòn nếu player lao vào tầm, rồi áp sát lại. Bị đánh trúng cũng lùi. Không lùi xuống vực, lên gai hay ra khỏi phòng.
  Map Medium: 30 / 49 quái (vùng 3: 12, vùng 4: 18). Playtest tự động (`MediumMapPlaytestTest`, vùng 3–4 so với đuổi thẳng): số đòn quái vung
  −27%, thời gian chạm thân −32%, thời gian quái đứng trong tầm dao của player −34%.

- Quái mặt đất dừng ở mép vực và tường; từ Medium thì nhảy nếu có chỗ đáp an toàn (hố tới 4 tile, cao 2, sâu 2).
- Quái bay không chịu trọng lực: tuần tra cao 40 đơn vị trên sàn, khi săn thì bay ngay trên đầu player và lao xuống
  khi tấn công; chết thì xác rơi xuống sàn.

## 7. Thế giới

- **Phòng và cửa:** mỗi lối đi, giếng thang, hố rơi giữa 2 phòng là một cửa. Đi vào cửa: màn hình tối dần (thế giới
  dừng), player xuất hiện ngay trong phòng kế, rồi sáng lại; đang leo thang hay đang rơi thì tiếp tục. Hố rơi là một
  chiều; lối đi và thang đi hai chiều.
- **Camera:** bám theo chân player (lệch lên 24 đơn vị), không bao giờ lộ ra ngoài tường phòng hiện tại, chuyển ngay
  sang phòng mới khi qua cửa. Zoom 0,5–1 (mặc định 0,8) và không lớn hơn phòng. Camera tự do zoom 0,25–6.
- **Minimap** (góc phải trên): phòng đã vào được vẽ (phòng hiện tại xanh lá, phòng đặc biệt xanh dương, phòng phụ
  vàng, có dấu cửa); phòng sau một cửa của phòng đã khám phá hiện viền tối; còn lại ẩn. Chấm đỏ nhấp nháy là player.
  `M` mở bản đồ toàn bộ.
- **Ánh sáng:** boong-ke tối; mọi đèn trong map được cộng vào (đèn ấm, màn hình lạnh, lửa, bể độc) với kiểu nhấp nháy
  của tool tạo map; player có đèn nhỏ bán kính 56 đơn vị (3,5 tile); lửa nòng và tia lửa đạn sáng lên chốc lát.
- **Tường phá được** (tile BREAKABLE): dao phá được; đạn dừng lại ở đó.
- **Lối ra:** tới lối ra thì mở màn "Mission complete" và đánh dấu ván hoàn thành; có thể chơi tiếp hoặc về menu.

## 8. Rương, vật phẩm và điểm

**Rương** (object `CHEST` của map, vẽ bằng tile rương có sẵn): Easy 4, Medium 10, Hard 10; gỗ (`wood`) hoặc vàng (`gold`).
Đứng sát rương (12 đơn vị mỗi bên, cùng tầng) và bấm `E`: rương mở trong 0,4 s rồi ném ra **một** vật phẩm. Mỗi rương mở một
lần, kể cả sau Continue.

| | Hộp cứu thương | Hộp đạn súng lục | Hộp đạn súng trường |
|---|---|---|---|
| Rương gỗ | 40% | 40% | 20% |
| Rương vàng | 30% | 20% | 50% |

- Vật phẩm của một rương chỉ phụ thuộc id của rương (`map#objectId`), nên lần nào chơi cũng như nhau (`LootTable`).
- Vật phẩm bay vòng cung ra khỏi nắp, chạm sàn (bụi), nảy nhẹ một lần rồi nằm yên; chỉ nhặt được khi đã nằm yên. Chỗ rơi
  luôn trên sàn, không có gai / chất độc / tường, trong phòng của rương (`LootDrop`).
- **Nhặt:** chạm vào là nhặt (lấp lánh và thông báo "+25 HP", "+24 pistol rounds"…), trừ khi vô dụng lúc đó (đầy máu, đầy
  dự trữ): khi ấy vật phẩm nằm lại.
- Playtest tự động (`LootPlaytestTest`): Easy cho 1 hộp đạn súng lục, 3 hộp đạn súng trường, không hộp cứu thương nào;
  Medium 3 / 4 / 3 (cứu thương / súng lục / súng trường); Hard 2 / 4 / 4.

**Điểm** (`ScoreRules`, mỗi sự kiện chỉ tính một lần, kể cả sau Continue):

| Sự kiện | Điểm |
|---|---|
| Hạ một quái (mỗi quái của map một lần) | 100 |
| Mở rương gỗ / vàng | 150 / 300 |
| Dọn sạch quái một phòng | 250 |
| Tới lối ra | 1000 |

Không có điểm theo sát thương. Điểm tối đa: Easy 6 500, Medium 13 400, Hard 19 000. Màn "Mission complete" hiện điểm, số
quái đã hạ, số rương đã mở / tổng số, số phòng đã dọn.

## 9. Màn hình, lưu game, cài đặt

| Màn | Nội dung |
|-----|----------|
| Màn hình chính | Continue (ván dở dang lưu gần nhất), New Game (chọn chế độ; đã có save thì chọn chơi tiếp hoặc chơi lại từ đầu), Settings, Quit |
| Menu tạm dừng | Resume, Settings, Save and exit to menu, Save and quit |
| Settings | Toàn màn hình, VSync, hiện FPS (lưu và áp dụng ngay), bảng phím |

- Mỗi chế độ một save: `~/.mygame/saves/easy.json`, `medium.json`, `hard.json`. Save từ 3 ô lưu cũ được nhập một lần (bản mới nhất của mỗi chế độ),
  file cũ chuyển vào `old_slots/`; cài đặt trong `~/.mygame/settings.json` (ngoài repo).
- Tự lưu khi chạm checkpoint mới, khi tới lối ra, và khi rời ván (về menu, thoát, đóng hoặc thu nhỏ cửa sổ). Nếu lúc
  rời ván đang chết hoặc đang qua cửa thì lưu ở checkpoint thay vì chỗ đứng.
- **Lưu những gì** (phiên bản 2): vị trí, điểm hồi sinh, checkpoint cuối, máu, vũ khí đang cầm, đạn trong băng và đạn dự
  trữ của từng súng, phòng đã khám phá, thời gian chơi, trạng thái hoàn thành, điểm và thống kê, quái / rương / phòng đã
  tính điểm, rương đã mở, vật phẩm còn nằm trên sàn. **Không lưu:** tường đã phá; quái hồi lại khi Continue (quái đã tính
  điểm thì hạ lại không có điểm). Save phiên bản 1 vẫn đọc được (đạn dự trữ lấy mức ban đầu, điểm 0).
- File lưu hỏng hoặc của phiên bản mới hơn: game không crash, trang của chế độ ghi lý do và cho chơi lại từ đầu. Map đã xuất lại khác kích thước: Continue chạy lại từ điểm xuất phát nhưng giữ máu, vũ khí và tổng điểm (rương và vật phẩm về như mới).

## 10. Chưa có

Âm thanh, ngắm bằng chuột, chuyển sang màn tiếp theo, mini boss,
sát thương của bẫy.
