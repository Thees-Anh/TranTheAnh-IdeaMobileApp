# PawQuest 1.0.0 — Remediation Status

Ngày: 2026‑11‑20 · Nhánh `release/1.0.0`. Theo dõi tiến độ của `RELEASE_ACTION_PLAN.md`.
Ký hiệu: ✅ đã làm & kiểm chứng trong repo · 🟡 đã chuẩn bị, **cần bạn thực hiện** (cần quyền Supabase / Play Console / hosting / thiết bị) · ⏳ chưa làm.

## Trạng thái hiện tại: **NOT READY** — chỉ còn thao tác ngoài repository
Toàn bộ thay đổi mã nguồn cho các P0/P1 đã xong. Các blocker còn lại đều cần thao tác ngoài repository: đăng trang pháp lý, áp migration lên Supabase production và kiểm thử trên staging/thiết bị thật.

## Theo issue

| Issue | Trạng thái | Đã làm | Còn lại (của bạn) |
|---|---|---|---|
| REL‑P0‑01 Privacy policy | ✅/🟡 | Đã đăng: https://thees-anh.github.io/TranTheAnh-IdeaMobileApp/privacy-policy.html (vi/en, HTTPS, công khai); link trong màn hình đăng nhập và Settings; `bundleRelease` bắt buộc URL | Nhập URL vào Play Console (App content → Privacy policy) và vào cấu hình build release |
| REL‑P0‑02 Web xoá tài khoản | ✅/🟡 | Đã đăng: https://thees-anh.github.io/TranTheAnh-IdeaMobileApp/account-deletion.html; quy trình `delete_account_by_email.sql`; `RUNBOOK.md` §4.6 | Khai báo URL trong Data safety; theo dõi hộp thư deahn.developer@gmail.com để xử lý yêu cầu trong 30 ngày |
| REL‑P0‑03 Migration parity / hosted | 🟡 | `docs/operations/SUPABASE_PRODUCTION_CHECKLIST.md`; migration mới 035 đã chạy trong suite local | Dựng staging, backup production, áp migration (≥ 003300, 003400, 003500), chạy `verification.sql`, smoke test xoá tài khoản & OTP |
| REL‑P1‑01 Build truy vết được | ✅/🟡 | Commit phần việc dở; nhánh `release/1.0.0`; `verifyPawQuestRelease` chặn `bundleRelease` thiếu key/versionCode/URL; quy ước versionCode trong `RELEASE_PROCESS.md` | Tạo upload keystore, bật Play App Signing, tag `v1.0.0` khi phát hành |
| REL‑P1‑02 Supply chain | ✅ | Chế độ `PAWQUEST_OFFICIAL_REPOSITORIES_ONLY`; Aliyun chỉ còn là fallback cuối cho dev; `gradle/verification-metadata.xml` (SHA‑256; lenient local, strict CI); 45 file `manual-maven` + 590 file `local-maven` khớp SHA‑1 với Google Maven/Maven Central | — |
| REL‑P1‑03 Xoá tên pet | ✅ | Migration `20261120003500_account_deletion_pet_names.sql` + assertion trong `account_deletion.sql` | Áp lên production (cùng REL‑P0‑03) |
| REL‑P1‑04 Crash reporting/monitoring | ✅/🟡 | Crashlytics chạy trên project Firebase thật `pawquest-dbb7f`: crash thử đã gửi thành công, tắt thì không gửi (emulator, 2026‑11‑26); secret CI đã có; `min_supported_version_code` + màn hình cập nhật | Xem crash thử trong Firebase Console; cập nhật Data safety; áp migration 036; bật cảnh báo Supabase |
| REL‑P1‑05 Backup/rollback | 🟡 | `docs/operations/RUNBOOK.md` (kill switch, bút toán bù, roll‑forward, khôi phục backup) | Xác nhận gói backup/PITR, diễn tập khôi phục |
| REL‑P1‑06 Thiết bị thật / a11y | ⏳ | — | `QA_TEST_PLAN.md` §3.3–3.4 trên thiết bị qua internal testing |
| REL‑P1‑07 Ngôn ngữ trong AAB | ✅ | `bundle { language { enableSplit = false } }` | Xác nhận QA‑Q04 trên bản cài từ Play |
| SEC‑04 dataExtractionRules | ✅ | `res/xml/data_extraction_rules.xml` (Android 12+) và `backup_rules.xml` (≤ 11); lint `DataExtractionRules` đã hết | — |
| BE‑05 Request ID Shop | ✅ | `PendingPurchaseStore` + `AndroidPendingPurchaseStore`; 4 unit test mới | — |
| BE‑06 Kill switch gacha | ✅ | `disable_item_case.sql`, `disable_lucky_wheel.sql` (đã thử trên DB local) | Thử trên staging |
| QA‑01 CI | ✅ | `.github/workflows/ci.yml` chạy xanh trên GitHub (run 44684485452, commit `d297a97`): validators, 232 unit test, lint release, R8, 16 KB, SQL suite trên PostgreSQL 18; job tay `refresh_verification_metadata` | Thêm secrets/vars cho job `bundle` khi phát hành |
| SEC‑06 Log release | ✅ | R8 `-assumenosideeffects` cho `Log.v/d/i` | — |
| 3.4 Bật gacha ở 1.0.0? | 🟡 | — | Quyết định sản phẩm |

## Kết quả kiểm tra (2026‑11‑20, máy dev Windows)

| Kiểm tra | Kết quả |
|---|---|
| `gradlew --dependency-verification=strict :shared:testAndroidHostTest :androidApp:lintRelease :androidApp:bundleRelease :androidApp:assembleRelease` với `PAWQUEST_OFFICIAL_REPOSITORIES_ONLY=true`, `--rerun-tasks` | **BUILD SUCCESSFUL**; 232 test, 0 fail/error/skip (228 cũ + 4 `ShopPendingPurchaseTest`); dependency verification strict không lỗi |
| Lint release | 0 error; `DataExtractionRules` và `AppBundleLocaleChanges` đã hết. Cảnh báo còn lại là style/phiên bản thư viện |
| Bundle ký bằng **keystore thử tạm thời** (đã xoá cùng output) | `jarsigner -verify`: jar verified; `apksigner verify` OK; `versionCode=10000`, `versionName=1.0.0`; locale `vi` có trong base APK |
| 16 KB | `zipalign -c -P 16 -v 4`: Verification successful |
| `bundleRelease` thiếu cấu hình | Fail đúng với danh sách: keystore, versionCode, privacy URL, deletion URL |
| Build dev mặc định (`assembleDebug`, `lintRelease`, lenient) | BUILD SUCCESSFUL |
| `tools/testing/test_database.py` (PostgreSQL 18 local, DB dùng một lần) | Exit 0, 24/24 PASS — gồm migration 035 và `account_deletion.sql` kiểm tra tên pet |
| `delete_account_by_email.sql`, `disable_*/enable_*.sql` trên DB dùng một lần | Identity bị xoá, tên pet về “Mochi”, profile “Deleted user”; banner tắt/bật đúng |
| Validators (`check_project_structure`, `audit_localization`, 4 generator `--check`) | PASS (978 cặp song ngữ) |
| Checksum artifact cục bộ | 45/45 `manual-maven`, 590/590 `local-maven` khớp SHA‑1 của Google Maven/Maven Central |

**CI GitHub Actions:** xanh từ commit `d297a97` (232 test, 0 lỗi). **Chưa kiểm chứng:** không thử trên Supabase hosted, Play Console hay thiết bị vật lý; UI link privacy chỉ kiểm tra bằng build, chưa xem trên thiết bị (link bị ẩn khi chưa cấu hình URL).
