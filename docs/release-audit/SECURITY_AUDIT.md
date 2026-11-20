# PawQuest 1.0.0 — Security Audit

Ngày audit: 2026-11-20 · Commit gốc: `2facef9` (branch `ytuong/tran-the-anh`, working tree có 17 file chưa commit)
Phạm vi: mã nguồn, cấu hình Gradle/Manifest, migration Supabase trong repository. **Không** truy cập project Supabase hosted, Play Console hay bất kỳ môi trường production nào. Không đọc/tiết lộ giá trị secret (`local.properties` chỉ được liệt kê tên khoá).

Ký hiệu trạng thái: **[ĐÃ KIỂM CHỨNG]** = đã đọc code/chạy lệnh và xác nhận; **[RỦI RO]** = có dấu hiệu, cần xác nhận thêm; **[CHƯA XÁC MINH]** = không thể kiểm tra từ repository.

---

## 1. Tóm tắt

Mô hình bảo mật backend ở mức tốt: mọi bảng `public` bật RLS, client không có quyền ghi trực tiếp vào bảng kinh tế, mọi RPC `SECURITY DEFINER` đặt `search_path=''`, quyền EXECUTE được revoke khỏi `public/anon` rồi chỉ cấp cho `authenticated`, RNG gacha dùng `pgcrypto.gen_random_bytes` với rejection sampling. Phía client: session mã hoá AES-GCM bằng Android Keystore trong `noBackupFilesDir`, `allowBackup=false`, Gradle chặn secret key, WebView bị khoá chặt.

Rủi ro bảo mật còn lại tập trung ở **chuỗi cung ứng build** (mirror bên thứ ba + artifact tải tay, không có dependency verification), **xoá dữ liệu chưa đầy đủ** khi xoá tài khoản, và **trạng thái thực tế của backend hosted chưa được xác minh**.

| ID | Mức | Tiêu đề | Trạng thái |
|---|---|---|---|
| SEC-01 | P1 | Repository Maven bên thứ ba (Aliyun) và artifact tải tay, không có dependency verification | RỦI RO |
| SEC-02 | P1 | `delete_my_account()` không xoá tên thú cưng (dữ liệu do người dùng nhập) | ĐÃ KIỂM CHỨNG |
| SEC-03 | P1 | Grants/RLS trên project hosted chưa được đối chiếu với migration | CHƯA XÁC MINH |
| SEC-04 | P2 | Không có cấu hình `dataExtractionRules`; SharedPreferences chứa tiêu đề quest có thể bị chuyển D2D | RỦI RO |
| SEC-05 | P2 | Cấu hình bảo vệ Auth trên Supabase (rate limit, CAPTCHA, leaked-password, độ dài mật khẩu) không nằm trong repo | CHƯA XÁC MINH |
| SEC-06 | P3 | `Log.d/Log.e` không điều kiện trong renderer SceneView (release) | ĐÃ KIỂM CHỨNG |
| SEC-07 | P3 | Activity `exported=true` nhận extra `pawquest_destination` từ app khác | ĐÃ KIỂM CHỨNG (đã whitelist — rủi ro thấp) |
| SEC-08 | P3 | Logout chỉ xoá local khi revoke remote thất bại; token cũ còn hiệu lực tới khi hết hạn | ĐÃ KIỂM CHỨNG (đã được tài liệu hoá) |

---

## 2. Những điểm đã kiểm chứng là tốt

| Hạng mục | Bằng chứng |
|---|---|
| Không commit secret | `.gitignore` loại `local.properties`, `*.jks`, `*.keystore`, `*.pem`, `.env*`, `/local-dev/`. `git ls-files` không có file khoá/keystore. `local.properties` chỉ chứa `sdk.dir`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `PAWQUEST_DEBUG_CA_CERT`. |
| Chặn secret key lọt vào APK | `androidApp/build.gradle.kts:70-77`: build fail nếu key bắt đầu bằng `sb_secret_` hoặc JWT có `role != anon`. |
| Network security | Release không có `networkSecurityConfig` override (dùng mặc định: cấm cleartext trên API 28+). Cleartext/CA dev chỉ sinh cho biến thể debug (`build.gradle.kts:60-64`, `src/debug/AndroidManifest.xml`). |
| Lưu session | `shared/src/androidMain/.../EncryptedSessionManager.kt`; tài liệu `docs/features/AUTHENTICATION.md:90-96`: AES-GCM, key trong Android Keystore, ghi vào `noBackupFilesDir`. |
| Backup | `AndroidManifest.xml:12` `android:allowBackup="false"`. |
| WebView | `BistroRenderer.android.kt:42-46`, `LuckyWheelPreview.kt:58-63`: `allowFileAccess=false`, `allowContentAccess=false`, `allowUniversalAccessFromFileURLs=false`, `blockNetworkLoads=true`, nạp asset qua `WebViewAssetLoader`; không có `addJavascriptInterface`, không bật `setWebContentsDebuggingEnabled`. |
| RLS | Toàn bộ 36 bảng `public` đều bật RLS (đối chiếu `create table public.*` với `enable row level security` trong `supabase/migrations/*.sql`; các bảng gacha bật qua vòng lặp ở `20261110002400_item_case_shards.sql:426-434`, `20261110002800_lucky_wheel.sql:487-503`). |
| Quyền bảng | Client chỉ có `SELECT` + một số cột `UPDATE` (tên pet, display name, nội dung quest) — `20261105000100_initial_schema.sql:130-135`. Không có INSERT/UPDATE/DELETE trên wallet, ledger, inventory. |
| View | 3 view `public` đều `security_invoker=true` (`20261105001000_shop_inventory.sql:46`, `20261110002400_item_case_shards.sql:193,199`). |
| RPC | 97 hàm `SECURITY DEFINER`, tất cả đặt `set search_path=''` (kiểm tra tự động không tìm thấy ngoại lệ). Toàn bộ 40 hàm `public.*` có `revoke all ... from public, anon, authenticated, service_role` rồi `grant execute ... to authenticated`. Actor luôn lấy từ `auth.uid()`; không RPC nào nhận `user_id`, số tiền hay timestamp từ client (`docs/backend/DATABASE.md:96-102`). |
| RNG gacha | `20261110002600_secure_item_case_open.sql:5-30`: `gen_random_bytes` + loại bỏ đuôi lệch modulo; Lucky Wheel dùng cùng nguồn (`20261110002800_lucky_wheel.sql:325`). Không dùng `random()`. |
| Rate limit gacha | `pawquest_private.item_case_api_settings` (30 lần / 60 s) và `wheel_allow_request`. |
| Mật khẩu | Không lưu mật khẩu; xoá tài khoản yêu cầu nhập lại mật khẩu (`SupabaseAuthenticationRepository.kt:224-236`). Reset mật khẩu bằng OTP không tiết lộ email có tồn tại hay không. |
| Logging | Log chẩn đoán auth/pet chỉ khi `debug` (`AndroidAuthentication.kt:77,82`); SDK logging tắt. |

---

## 3. Chi tiết phát hiện

### SEC-01 — Repository Maven bên thứ ba và artifact tải tay (P1)

- **Mô tả:** `settings.gradle.kts` khai báo `https://maven.aliyun.com/repository/public` cho mọi dependency, và hai repository cục bộ `.gradle/local-maven`, `.gradle/manual-maven` (“Browser-downloaded artifacts”) cung cấp các bản `androidx.lifecycle`, `kotlin-compiler-embeddable`, `kotlin-build-tools-impl`, `kotlin-reflect`… Không có `gradle/verification-metadata.xml`.
- **Bằng chứng:** `settings.gradle.kts` (khối `dependencyResolutionManagement`: `manualRepository` và `maven.aliyun.com`); `git ls-files | grep verification-metadata` → rỗng.
- **Tác động:** Build release có thể chứa artifact không xác minh được nguồn gốc (kể cả compiler Kotlin). Đây là rủi ro supply-chain trực tiếp vào APK ký bằng upload key.
- **Tái hiện:** `./gradlew :androidApp:dependencies --configuration releaseRuntimeClasspath` rồi so nguồn tải trong `--info`/build scan.
- **Đề xuất:** Build release trên máy/CI sạch, chỉ dùng `google()` + `mavenCentral()`; xoá `.gradle/manual-maven` khỏi máy build; bật `./gradlew --write-verification-metadata sha256` và commit `verification-metadata.xml`.
- **Tiêu chí xác nhận:** `settings.gradle.kts` không còn Aliyun/manual repo cho cấu hình release; build release thành công trên runner sạch với dependency verification `strict`.

### SEC-02 — Xoá tài khoản không xoá tên thú cưng (P1)

- **Mô tả:** `delete_my_account()` xoá `auth.users`, thay tiêu đề/mô tả quest và đổi `profiles.display_name`, nhưng **không** đụng tới `public.pets.name` — trường người dùng tự nhập (`grant update (name) on public.pets`).
- **Bằng chứng:** `supabase/migrations/20261112003100_account_deletion.sql:34-56` (không có `update public.pets`); `supabase/tests/account_deletion.sql` không có assertion nào về `pets`.
- **Tác động:** Dữ liệu do người dùng tạo vẫn còn sau khi xoá; mâu thuẫn với khai báo “dữ liệu bị xoá” trong Data safety và chính sách xoá tài khoản của Google Play. Tên pet có thể chứa thông tin cá nhân (tên người, biệt danh).
- **Tái hiện:** Trên DB local: đổi tên pet thành “Nguyen Van A” → gọi `delete_my_account()` → `select name from public.pets where user_id=<id>` vẫn trả “Nguyen Van A”.
- **Đề xuất:** Trong `delete_my_account()` đặt lại tên mọi pet của actor về giá trị mặc định theo loài; rà soát mọi cột text khác do người dùng nhập (hiện tại: `profiles.display_name`, `pets.name`, `quests.title/description`, `quest_sessions.title_snapshot`). Thêm assertion vào `account_deletion.sql`.
- **Tiêu chí xác nhận:** Test SQL mới khẳng định sau khi xoá không còn chuỗi do người dùng nhập ở bất kỳ bảng nào; chính sách quyền riêng tư liệt kê chính xác dữ liệu được giữ lại (ledger ẩn danh).

### SEC-03 — Trạng thái bảo mật của project hosted chưa được xác minh (P1)

- **Mô tả:** Mọi kết luận RLS/grant ở trên dựa trên migration và DB PostgreSQL local. `docs/TEST_REPORT.md:25` ghi rõ hosted **chưa** áp dụng `20261112003300` và `20261112003400`; `TEST_REPORT.md:60-62` ghi chưa chạy xoá tài khoản thật trên hosted. Repo không có `supabase/config.toml` hay lịch sử migration hosted.
- **Tác động:** Nếu hosted lệch (thiếu migration, có grant tạo tay qua Dashboard, bảng tạo tay chưa bật RLS) thì bảo đảm bảo mật không còn đúng.
- **Đề xuất:** Chạy `supabase/tests/verification.sql` (chỉ đọc) trên project production với quyền phù hợp; chạy Supabase Security Advisor; so sánh `supabase migration list` với thư mục `supabase/migrations/`.
- **Tiêu chí xác nhận:** Danh sách migration hosted = 34 file local; các truy vấn vi phạm trong `verification.sql` trả 0 dòng; Security Advisor không còn cảnh báo ERROR.

### SEC-04 — Thiếu `dataExtractionRules` (P2)

- **Mô tả:** Target API 37 nhưng manifest chỉ có `allowBackup="false"`. Với app target ≥ 31, cờ này tắt cloud backup nhưng **không tắt chuyển dữ liệu thiết bị‑sang‑thiết bị (D2D)**. `CompanionAlarms` lưu tiêu đề quest + user ID vào SharedPreferences `pawquest_alarms` (`CompanionAlarms.kt:51-56`); widget cũng có store riêng.
- **Tác động:** Tiêu đề quest/ID người dùng có thể được sao sang máy mới mà không qua đăng nhập. Session đã nằm trong `noBackupFilesDir` nên không bị ảnh hưởng.
- **Đề xuất:** Thêm `android:dataExtractionRules` loại trừ `sharedpref` và `database` cho cả `cloud-backup` và `device-transfer`.
- **Tiêu chí xác nhận:** Manifest merged có `dataExtractionRules`; thử `adb shell bmgr` / chuyển D2D không mang theo `pawquest_alarms.xml`.

### SEC-05 — Cấu hình Supabase Auth ngoài repo (P2, CHƯA XÁC MINH)

- **Mô tả:** Không có tệp cấu hình cho: xác nhận email, độ dài/độ mạnh mật khẩu tối thiểu, rate limit đăng ký/OTP, CAPTCHA, chống mật khẩu rò rỉ, thời hạn JWT, template email reset có `{{ .Token }}` (`AUTHENTICATION.md:31-35`).
- **Đề xuất:** Ghi lại cấu hình Auth production vào một tài liệu vận hành (không chứa secret) và đối chiếu trước phát hành.
- **Tiêu chí xác nhận:** Checklist cấu hình Auth được ký duyệt; thử đăng ký/OTP liên tục bị rate‑limit.

### SEC-06 — Log không điều kiện trong renderer cũ (P3)

- **Bằng chứng:** `shared/src/androidMain/.../scene/PetSceneView.kt:98,106,159,198,243,279`, `PetScene.kt:116-162`. R8 không loại `android.util.Log` mặc định.
- **Tác động:** Thấp — chỉ thông tin kỹ thuật, và renderer SceneView đang “inactive”. Nhưng là code chết vẫn được đóng gói.
- **Đề xuất:** Bọc trong `BuildConfig.DEBUG` hoặc thêm `-assumenosideeffects class android.util.Log { d(...); v(...); }`.
- **Tiêu chí xác nhận:** `adb logcat -s PawQuestScene` trống trên bản release.

### SEC-07 — Activity exported nhận extra điều hướng (P3)

- **Bằng chứng:** `MainActivity.kt:33,43` đọc `pawquest_destination`; `Destination.fromRoute` dùng `when` cố định (`presentation/navigation/Destination.kt:12`). Route chỉ được xử lý sau khi đăng nhập (`App.kt:79,133`).
- **Tác động:** App khác chỉ có thể mở một tab hợp lệ. Không phát hiện leo thang quyền.
- **Đề xuất:** Giữ nguyên; cân nhắc chỉ chấp nhận extra từ `PendingIntent` nội bộ.

### SEC-08 — Logout khi mất mạng (P3)

- Đã tài liệu hoá ở `AUTHENTICATION.md:94-96`. Đề xuất cấu hình JWT expiry ngắn (≤ 1h) trên hosted.

---

## 4. Ngoài phạm vi / không thể kiểm chứng

- Chữ ký upload key, Play App Signing enrollment (không có quyền vào Play Console).
- Penetration test thực tế đối với PostgREST hosted.
- iOS: `iosApp` chỉ là scaffold, không được phát hành ở 1.0.0.
