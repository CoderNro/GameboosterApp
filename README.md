# Game Booster & Fixed Zone

App Android (Kotlin, không cần root) gồm 2 chức năng chính:

1. **Vùng chạm cố định (Fixed Touch Zone)** — một vòng tròn nổi trên màn hình, vị trí được
   lưu theo **tỉ lệ % so với chiều rộng/cao màn hình** nên vẫn đứng đúng vị trí tương đối
   dù bạn xoay ngang/dọc khi chơi game. Kéo để định vị lúc chưa khóa, bật "Khóa vị trí" để
   cố định hẳn.
2. **Game Booster** — dừng app nền giải phóng RAM, bật Không làm phiền, giữ màn hình sáng,
   và gọi `GameManager` (Android 12+) để yêu cầu hệ thống ưu tiên hiệu năng.

## Giới hạn kỹ thuật cần biết trước

Đây là giới hạn của nền tảng Android, **không phải giới hạn của code**:

- Ứng dụng bên thứ 3 (không root) **không thể** ép tăng tần số quét màn hình (60Hz→120Hz...)
  hay overclock CPU/GPU trực tiếp. `GameManager.GAME_MODE_PERFORMANCE` chỉ là một *gợi ý*
  gửi lên hệ thống — có tác dụng thật hay không phụ thuộc vào việc nhà sản xuất máy
  (Samsung, Xiaomi, OPPO...) có triển khai hỗ trợ Game Mode API hay không.
- "Vùng chạm cố định" hiện tại chỉ phát rung phản hồi khi chạm (placeholder). Nếu bạn muốn
  nó thực hiện hành động cụ thể khi chạm (auto-tap liên tục, gửi phím tắt, mở macro...),
  cần mô tả rõ hành động đó — mình sẽ bổ sung logic trong `OverlayService.handleTouch()`.
- `killBackgroundProcesses()` từ Android 8+ bị hệ điều hành giới hạn khá chặt vì lý do bảo
  mật/pin — hành vi này giống hệt các app Booster khác trên Play Store, không có "cách lách"
  nào khác khi không root.

## Cách build APK

### Có máy tính: Android Studio
1. Cài **Android Studio** (Hedgehog trở lên).
2. Mở thư mục `GameBoosterApp` này bằng "Open" trong Android Studio (không phải "Import").
3. Đợi Gradle sync xong (cần mạng để tải dependency lần đầu).
4. Chạy trực tiếp lên máy/emulator: nút ▶ (Run), hoặc build file cài đặt:
   `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
5. APK debug sẽ nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

### Không có máy tính: build bằng GitHub Actions (chỉ cần điện thoại)

Project này đã có sẵn file `.github/workflows/build.yml` để build APK tự động trên
cloud của GitHub, hoàn toàn miễn phí. Các bước thực hiện trên điện thoại:

1. **Tạo tài khoản GitHub** (nếu chưa có) tại github.com bằng trình duyệt điện thoại.
2. **Tạo repository mới**: bấm nút "+" → "New repository" → đặt tên (ví dụ
   `GameBoosterApp`) → chọn Public hoặc Private → Create.
3. **Đưa toàn bộ code lên repo**. Cách dễ nhất trên điện thoại là dùng app **Termux**
   (tải từ F-Droid, không có trên Play Store bản mới nhất):
   ```
   pkg install git
   cd storage/downloads      # hoặc nơi bạn giải nén file zip đã tải
   unzip GameBoosterApp.zip
   cd GameBoosterApp
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<username>/<ten-repo>.git
   git push -u origin main
   ```
   Khi hệ thống hỏi mật khẩu, dùng **Personal Access Token** (tạo tại
   github.com → Settings → Developer settings → Personal access tokens → Generate new
   token, tick quyền `repo`), KHÔNG dùng mật khẩu tài khoản thường.
4. Sau khi push xong, vào repo trên GitHub → tab **Actions** → sẽ thấy job "Build APK"
   tự động chạy (mất khoảng 2-4 phút).
5. Khi job chạy xong (dấu ✔ xanh), bấm vào job đó → kéo xuống phần **Artifacts** →
   tải file `app-debug-apk` về (là file .zip chứa APK bên trong) → giải nén → cài đặt.

> Lưu ý: đây là bản **debug APK** (chưa ký release, chỉ cài được trên máy cho phép
> "cài từ nguồn không xác định"). Nếu cần bản release để đăng Play Store, cần thêm bước
> tạo keystore ký ứng dụng - nói mình biết nếu bạn cần, mình sẽ hướng dẫn thêm.

> Icon app hiện là icon vector đơn giản tự tạo (vòng tròn xanh + tay cầm game) — bạn có thể
> thay bằng icon riêng qua `File → New → Image Asset` trong Android Studio.

## Cấp quyền khi chạy lần đầu

App sẽ yêu cầu lần lượt:
- **Hiển thị đè lên ứng dụng khác** (bắt buộc cho vùng cố định) — Android đưa thẳng ra màn
  hình Settings, bấm cho phép rồi quay lại app.
- **Thông báo** (Android 13+) — cần để Foreground Service chạy ổn định nền.
- **Do Not Disturb access** (nếu bật tính năng chặn thông báo trong lúc chơi game).

## Cấu trúc code

```
app/src/main/java/com/mrt/gamebooster/
├── GameBoosterApp.kt      # Application, tạo Notification Channel
├── MainActivity.kt        # UI chính, xin quyền, điều khiển bật/tắt
├── OverlayService.kt      # Foreground Service: vẽ overlay + xử lý kéo-thả/khóa
├── BoosterManager.kt      # Logic tối ưu hiệu năng
└── PrefsManager.kt        # Lưu cấu hình (SharedPreferences)
```
