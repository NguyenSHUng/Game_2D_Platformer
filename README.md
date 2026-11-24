# MyGame

Platformer 2D góc nhìn ngang viết bằng Java 21 và [libGDX](https://libgdx.com/): một người lính đi qua boong-ke nhiều
phòng, đánh quái bằng dao, súng lục và súng trường.

```powershell
.\gradlew.bat lwjgl3:run     # chạy game
.\gradlew.bat core:test      # chạy unit test
.\gradlew.bat lwjgl3:jar     # đóng gói jar vào lwjgl3/build/libs
```

Cần JDK 21. Module: `core` (toàn bộ logic game), `lwjgl3` (launcher desktop). Dữ liệu game nằm trong `assets/`, do các
tool trong `build_asset/` (không nằm trong git) tạo ra.

Tài liệu: [docs/README.md](docs/README.md).
