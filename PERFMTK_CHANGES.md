# perfmtk root mode – ghi chú thay đổi

GameSpace bản này không còn dùng `android.app.GameManager#setGameMode()` (Android
Game Mode API mặc định, cần vá thêm SystemUI/Settings mới có tác dụng thật) để đổi
hiệu năng khi vào game. Thay vào đó, mọi thay đổi chế độ hiệu năng được gửi thẳng
xuống thiết bị bằng lệnh root:

    su -c "perfmtk <mode>"

## Mã chế độ (`<mode>`)

| Giá trị | Tên hiển thị   | Ý nghĩa                 |
|---------|----------------|--------------------------|
| 1       | Performance    | Hiệu năng cao nhất       |
| 2       | Balanced       | Cân bằng (mặc định)      |
| 3       | Power save     | Tiết kiệm pin            |
| 4       | Power save+    | Tiết kiệm pin sâu nhất   |

## File chính đã sửa / thêm mới

- `utils/PerfmtkController.kt` **(mới)** – gọi `su -c "perfmtk <mode>"`, chờ tối đa
  300ms để nhận exit code/phản hồi rồi trả kết quả (SUCCESS/FAILED/TIMEOUT/NO_ROOT)
  về main thread qua callback. Không chặn UI kể cả khi perfmtk treo hoặc không tồn
  tại (thiết bị chưa root vẫn không crash app, chỉ log lỗi).
- `utils/GameModeUtils.kt` – bỏ hoàn toàn phần dùng `GameManager` để set mode, gọi
  `PerfmtkController` thay thế. `defaultPreferredMode` đổi thành Balanced (2).
- `widget/tiles/GameModeTile.kt` – tile trong game bar giờ đi qua 4 mode
  (Performance → Balanced → Power save → Power save+ → quay lại), mỗi mode có icon
  riêng (`ic_speed`, `ic_mode_balanced`, `ic_mode_powersave`,
  `ic_mode_powersave_plus`), rung nhẹ (haptic) khi bấm, và làm mờ icon trong lúc
  chờ perfmtk phản hồi (tối đa ~300ms) để tránh bấm dồn khi đổi mode liên tục.
- `gamebar/SessionService.kt` – khi vào một game đã đăng ký, tự áp lại mode đã lưu
  cho game đó bằng perfmtk (trước đây gọi `GameManager.setGameMode`).
- `data/UserGame.kt`, `settings/PerAppSettingsFragment.kt`, `res/values*/strings.xml`,
  `res/values/arrays.xml`, `res/xml/per_app_preferences.xml` – cập nhật theo thang
  4 mode mới (bản dịch DE/ID/RU/ZH-CN cũng đã cập nhật mảng tên mode để tránh lỗi
  do mảng dịch cũ chỉ có 3-4 phần tử trong khi mode mới cần index tới 4).

## Không đổi (cố ý)

`data/GameConfig.kt` vẫn dùng hằng số `GameManager.GAME_MODE_PERFORMANCE` /
`GAME_MODE_BATTERY` – đây là một tính năng khác (gợi ý *downscale* độ phân giải /
dùng renderer ANGLE qua `DeviceConfig`, namespace `NAMESPACE_GAME_OVERLAY`), không
liên quan tới CPU governor nên được giữ nguyên để không phá tính năng đang hoạt động.
Muốn gộp luôn phần này vào perfmtk thì có thể yêu cầu chỉnh tiếp.

## Rebrand: Game Booster (com.android.gamebooster.homemake)

- Tên app (`settings_title`) đổi từ "Game Space" → **"Game Booster"**.
- `applicationId` trong `app/build.gradle` đổi thành **`com.gamebooster.homemake`**
  (ban đầu định để `com.android.gamebooster.homemake` nhưng đổi lại vì prefix
  `com.android.` là namespace AOSP/Google, dễ bị Play Store từ chối hoặc phần mềm
  bảo mật nghi ngờ giả mạo app hệ thống) — đây là package/ID cài đặt thật (hiện
  trong Settings > Apps, `pm list packages`, v.v.). Namespace Kotlin nội bộ
  (`package io.chaldeaprjkt.gamespace.*`, R class) **cố tình giữ nguyên** để không
  phải sửa import ở hàng chục file — AGP 7.4.2 vẫn cho phép applicationId khác
  namespace nguồn, không ảnh hưởng gì tới build hay hoạt động của app.
- Thêm icon adaptive (`res/mipmap-anydpi-v26/ic_launcher.xml`), lớp `background`
  là ảnh núi băng phát sáng người dùng gửi (crop vuông theo cạnh dưới ảnh gốc,
  đủ 5 mật độ mdpi→xxxhdpi), lớp `foreground` để trong suốt hoàn toàn cho ảnh
  hiện trọn khung. Vì `minSdk 33` nên không cần icon legacy (không hỗ trợ < API 26
  nên không cần).
- Thêm `android:icon`/`android:roundIcon` vào `<application>`, và thêm
  intent-filter `MAIN`/`LAUNCHER` vào `SettingsActivity` — trước đây app không có
  icon nào và không xuất hiện ở màn hình chính/app drawer (chỉ vào được qua
  Settings đã vá sẵn theo README). Giờ icon Game Booster sẽ hiện như một app bình
  thường, bấm vào mở thẳng màn hình danh sách game/preferences.

## Yêu cầu khi build/dùng

- Thiết bị đã root, có binary `su` hoạt động (Magisk hoặc su tích hợp sẵn của ROM).
- Có sẵn binary/script tên `perfmtk` mà `su -c` gọi tới được (nằm trong PATH của
  shell root), nhận đúng 1 tham số nguyên 1-4 như bảng trên. GameSpace chỉ gọi
  lệnh này, không tự cài đặt hay đóng gói perfmtk.
- App vẫn build theo cách cũ (`sharedUserId="android.uid.system"`, cần ký bằng
  platform key và đặt vào `packages/apps/GameSpace` trong cây nguồn ROM/AOSP).
