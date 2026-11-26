# Firebase Crashlytics — thiết lập

Code đã sẵn sàng; chỉ thiếu cấu hình của project Firebase (cần tài khoản Google của bạn).

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
1. Cài app, mở *Cài đặt*: thấy **Gửi báo cáo sự cố** (bật). Không có file cấu hình thì mục này bị ẩn.
2. Tạo một crash thử bằng cách tạm thêm `throw RuntimeException("Crashlytics test")` vào một nút (không commit), mở lại app để report được gửi. Crash xuất hiện trong Console sau vài phút.
3. Tắt công tắc, crash lại: không có report mới.

## 4. Play Console — Data safety (cập nhật cùng bản có Crashlytics)
| Data type | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|
| App info and performance → Crash logs | Yes | No (Google là service provider) | Yes (tắt được) | Analytics |
| App info and performance → Diagnostics | Yes | No | Yes | Analytics |
| Device or other IDs | Yes (Firebase installation ID) | No | Yes | Analytics |

Dữ liệu mã hoá khi truyền: **Yes**. Đối chiếu lại với privacy policy đã đăng.

## 5. Dữ liệu được gửi / không bao giờ gửi
- **Gửi:** stack trace khi crash, tên RPC + mã HTTP 5xx (`ServerErrorReport`), custom key `screen`, model máy, phiên bản Android/app, Firebase installation ID.
- **Không bao giờ:** user ID, email, token, tiêu đề quest, tên thú cưng, nội dung response, query string (lọc bởi `supabaseOperation()`, có unit test).

## 6. Buộc cập nhật
Migration `20261126003600_app_config.sql` thêm `public.app_status(version_code)`. Quy trình nằm ở `RUNBOOK.md` §4.1a, dùng `supabase/operations/set_min_supported_version.sql`.
