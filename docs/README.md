# Tài liệu MyGame

Game platformer 2D góc nhìn ngang, viết bằng **Java 21 + LibGDX**: người lính đi qua một boong-ke nhiều phòng,
đánh quái bằng dao, súng lục, súng trường. Map, quái, nhân vật, vũ khí và giao diện đều do các tool trong
`build_asset/` sinh ra; game chỉ đọc dữ liệu đó.

## Chạy game

```powershell
cd E:\MyGame
.\gradlew.bat lwjgl3:run        # chạy game
.\gradlew.bat core:test         # chạy unit test (JUnit 5)
```

Cần JDK 21 (`JAVA_HOME`). Không build lại khi cửa sổ game đang mở.

## Đọc gì, theo thứ tự nào

| File | Trả lời câu hỏi | Đọc khi |
|------|-----------------|---------|
| [00-OVERVIEW.md](00-OVERVIEW.md) | Game là gì, có những tính năng nào, asset từ đâu ra | Mới vào dự án |
| [04-GAME-DESIGN.md](04-GAME-DESIGN.md) | Người chơi làm được gì, các con số gameplay | Muốn biết luật chơi |
| [02-ARCHITECTURE.md](02-ARCHITECTURE.md) | Code tổ chức ra sao, thứ tự một khung hình, các phần nói chuyện với nhau thế nào | Trước khi sửa code |
| [01-SOURCE-MAP.md](01-SOURCE-MAP.md) | Từng package, từng file làm gì | Tìm code, phân tích, đánh giá |
| [05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md) | Định dạng file dữ liệu, chi tiết từng system | Sửa một system hoặc thêm nội dung |
| [character-weapon-system.md](character-weapon-system.md) | Nhân vật cầm vũ khí thế nào (anchor tay, profile) | Thêm hoặc chỉnh vũ khí |
| [03-CODE-STANDARDS.md](03-CODE-STANDARDS.md) | Quy tắc viết code, mẫu code, checklist | Trước khi viết code (bắt buộc với AI) |

## Phím điều khiển (tóm tắt)

| Hành động | Phím |
|-----------|------|
| Đi trái / phải | `A` / `D` hoặc `←` / `→` |
| Nhảy (`S` + `Space`: xuống khỏi bệ một chiều) | `Space` |
| Leo thang / ngồi | `W` / `S` hoặc `↑` / `↓` |
| Đánh dao / bắn (giữ: bắn liên thanh) | `J` hoặc chuột trái |
| Nạp đạn | `R` |
| Chọn vũ khí / vũ khí kế tiếp | `1` `2` `3` / `Q` |
| Bảng nhân vật (tạm dừng) | `I` hoặc `F1` |
| Bản đồ toàn màn hình | `M` |
| Menu tạm dừng | `Esc` |

Bảng đầy đủ (camera, debug, camera tự do): [04-GAME-DESIGN.md → Điều khiển](04-GAME-DESIGN.md#2-điều-khiển).
