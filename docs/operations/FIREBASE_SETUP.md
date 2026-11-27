# Firebase Crashlytics — thiết lập

**Trạng thái (2026‑11‑26):** project `pawquest-dbb7f` đã tạo, `google-services.json` đặt local, secret CI đã thêm, crash thử đã gửi thành công từ emulator. Còn lại: mục 4 (Data safety).

## 1. Tạo project và app
1. <https://console.firebase.google.com> → *Add project* (ví dụ `pawquest`). Google Analytics: **tắt** (app không dùng analytics hành vi; bật sẽ phải khai báo thêm trong privacy policy và Data safety).
2. *Add app → Android*: package name **`com.pawquest`**, nickname tuỳ ý. Bỏ qua bước SHA‑1 (Crashlytics không cần).
3. Tải **`google-services.json`** và đặt vào `androidApp/google-services.json`. File đã có trong `.gitignore`, **không commit** (repo công khai).
4. *Build → Crashlytics → Enable*.

## 2. CI
- Secret `GOOGLE_SERVICES_JSON_BASE64` (repo fork → *Settings → Secrets and variables → Actions*):
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("androidApp\google-services.json")) | Set-Clipboard
  ```
- Job `bundle` (chạy khi có tag `v*`) tự khôi phục file, bật `PAWQUEST_CRASHLYTICS_UPLOAD_MAPPING=true` để upload R8 mapping. Build local/PR không bao giờ upload.
- `bundleRelease` **fail** nếu thiếu `google-services.json`, vì privacy policy đã công bố việc dùng Crashlytics.

## 3. Kiểm tra trên thiết bị (bản debug có `google-services.json`)
Báo cáo sự cố **luôn bật**, không có công tắc trong app (từ 2026‑11‑27; đã công bố trong privacy policy).
1. Cài app, mở app một lần.
2. Gây crash thử không cần sửa code: `adb shell am crash com.pawquest`, rồi mở lại app để report được gửi. Crash xuất hiện trong Console sau vài phút (`CrashedByAdbException: shell-induced crash`).

## 4. Play Console — Data safety (cập nhật cùng bản có Crashlytics)
| Data type | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|
| App info and performance → Crash logs | Yes | No (Google là service provider) | **No — Required** (luôn thu thập) | Analytics |
| App info and performance → Diagnostics | Yes | No | **No — Required** | Analytics |
| Device or other IDs | Yes (Firebase installation ID) | No | **No — Required** | Analytics |

Dữ liệu mã hoá khi truyền: **Yes**. Đối chiếu lại với privacy policy đã đăng.

## 5. Dữ liệu được gửi / không bao giờ gửi
- **Gửi:** stack trace khi crash, tên RPC + mã HTTP 5xx (`ServerErrorReport`), custom key `screen`, model máy, phiên bản Android/app, Firebase installation ID.
- **Không bao giờ:** user ID, email, token, tiêu đề quest, tên thú cưng, nội dung response, query string (lọc bởi `supabaseOperation()`, có unit test).

## 6. Buộc cập nhật
Migration `20261126003600_app_config.sql` thêm `public.app_status(version_code)`. Quy trình nằm ở `RUNBOOK.md` §4.1a, dùng `supabase/operations/set_min_supported_version.sql`.
