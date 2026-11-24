# 00 - Tổng quan

[← Mục lục](README.md)

## Thông tin nhanh

| | |
|---|---|
| Thể loại | Platformer 2D góc nhìn ngang, bắn súng, khám phá boong-ke nhiều phòng |
| Công nghệ | Java 21, LibGDX 1.14.2 (+ gdx-freetype), Gradle wrapper |
| Nền tảng | Desktop (module `lwjgl3`, cửa sổ 1280×720) |
| Kiến trúc | Component-based kiểu ECS: entity chứa component dữ liệu, system xử lý ([02-ARCHITECTURE.md](02-ARCHITECTURE.md)) |
| Quy mô code | 109 file Java (~9.100 dòng) trong `core`, 63 unit test |

## Tính năng hiện có

| Nhóm | Có gì |
|------|-------|
| Màn hình | Màn hình chính (Continue, New Game, Settings, Quit), menu tạm dừng, màn "Mission complete" |
| Chế độ chơi | Easy, Medium, Hard: ba map boong-ke riêng, dài dần, quái đông dần và mạnh dần (`EnemySkill`). Hard đang tạm khoá |
| Lưu game | Mỗi chế độ một file lưu; tự lưu ở checkpoint và khi rời ván (về menu, thoát, đóng cửa sổ) |
| Di chuyển | Đi, nhảy (coyote time, jump buffer), ngồi, leo thang, xuống bệ một chiều |
| Chiến đấu | Dao (cận chiến theo khung animation), súng lục bán tự động, súng trường liên thanh, nạp đạn, đạn bay có va chạm |
| Quái | 3 loại melee, crawler, quái bay: biết tuần tra, phát hiện, nhớ, đuổi, tấn công; từ Medium còn gọi bầy, nhảy, đón đầu |
| Thế giới | 13 / 26 / 42 phòng (Easy / Medium / Hard) nối bằng cửa, thang, hố rơi; camera bám phòng; minimap; checkpoint; bẫy gai, chất độc; tường phá được |
| Hình ảnh | Ánh sáng theo đèn của map, hiệu ứng súng, bảng nhân vật, lớp debug |

Chưa có: âm thanh, vật phẩm nhặt được, chuyển màn khi tới lối ra, ngắm bằng chuột.

## Cấu trúc dự án

```text
MyGame/
├── core/          Toàn bộ logic game (package com.hung.*), unit test trong core/src/test
├── lwjgl3/        Launcher desktop: chỉ cấu hình cửa sổ và chạy MyGame
├── assets/        Dữ liệu game do các tool sinh ra
│   ├── maps/<tên>/            map_easy / map_medium / map_hard.tmx + tileset
│   ├── sprites/players/<id>/  JSON + dải animation của người chơi
│   ├── sprites/enemies/<id>/  character.json + sprite sheet (+ effects/)
│   ├── sprites/weapons/<id>/  JSON + hình vũ khí (+ fx/)
│   ├── attachments/<nhân vật>/ cách nhân vật cầm từng vũ khí
│   └── ui/                    font, dải sọc cảnh báo, nền màn hình chính
├── docs/          Tài liệu này
└── build_asset/   Các tool tạo asset (HTML/JS/Python), không nằm trong git
```

## Asset đến từ đâu

| Tool (`build_asset/…`) | Sinh ra | Lệnh xuất vào game |
|------------------------|---------|--------------------|
| `build_map/platformer-map-generator` | Map (địa hình, phòng, vị trí quái) | `node tools/export-game.mjs --mode easy\|medium\|hard` |
| `build_enemy/enemy-character-generator` | Quái (sprite, animation) | `node tools/sync-game.mjs` (chép 5 quái của game) |
| `build_character/player-character-generator` | Người chơi | `node tools/export-game.mjs --id <id>` |
| `build_weapon/pixel-weapon-generator` | Vũ khí + profile cầm | `node tools/generate.mjs` rồi `node tools/export-game.mjs` |
| `build_ui` | Giao diện menu | `py export-game.py` |

Mỗi tool có `PROGRESS.md` ghi trạng thái. Game không chứa số liệu nghệ thuật: kích thước, anchor, khung hình đều
đọc từ JSON; con số gameplay (máu, sát thương) nằm trong code game.

## Nguyên tắc chung

1. Khả năng dùng chung là component + system; nhân vật và các loại quái là một cây kế thừa (`GameCharacter`).
2. Mỗi system một việc; system không gọi system khác.
3. Dữ liệu ở file ngoài (JSON, TMX); giữ ổn định schema dữ liệu và schema file lưu.
4. Luôn có bản chơi được: mọi thay đổi phải biên dịch và qua test.
5. Không logic game trong `lwjgl3`.
