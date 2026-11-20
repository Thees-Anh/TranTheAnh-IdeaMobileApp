# PawQuest 1.0.0 — Release Readiness Report

| | |
|---|---|
| Ngày | 2026‑11‑20 |
| Phiên bản mục tiêu | 1.0.0 (Google Play, Android) |
| Commit | `2facef9` trên `ytuong/tran-the-anh` + 17 thay đổi chưa commit (working tree tại thời điểm audit) |
| Phạm vi | Mã nguồn, Gradle, Manifest, 34 migration Supabase, tài liệu, test. Chỉ đọc; không sửa implementation; không truy cập production |
| Vai trò | Principal Android Engineer · Mobile QA Lead · Backend Security Reviewer · Release Manager |

## KẾT LUẬN: **NOT READY**

Nền tảng kỹ thuật **tốt hơn mức trung bình** của một bản 1.0: kinh tế game do server quyết định, idempotent và atomic, có test đồng thời; RLS và quyền RPC chặt; target API 37 và 16 KB page size đạt; 228 unit test, toàn bộ suite SQL/concurrency đều pass trong buổi audit.

Tuy nhiên app **chưa thể nộp lên Google Play** vì thiếu các hạng mục tuân thủ bắt buộc và chưa chứng minh được backend production khớp với mã nguồn:

### Release blockers (P0)
1. **REL‑P0‑01** — Không có privacy policy (Play Console + trong app).
2. **REL‑P0‑02** — Không có web link yêu cầu xoá tài khoản (bắt buộc cho Data safety).
3. **REL‑P0‑03** — Production Supabase chưa được xác minh khớp 34 migration; tài liệu dự án ghi hosted đang thiếu migration; xoá tài khoản và reset mật khẩu chưa từng chạy trên hosted.

Sau khi đóng 3 mục P0 và REL‑P1‑01 (build truy vết được, đã ký), trạng thái có thể nâng lên **CONDITIONALLY READY** cho internal/closed testing; mở production rollout khi các P1 còn lại được xử lý. Chi tiết: `CRITICAL_ISSUES.md`, thứ tự: `RELEASE_ACTION_PLAN.md`.

---

## 1. Kết quả chạy lệnh trong buổi audit (đã kiểm chứng)

| Lệnh | Kết quả |
|---|---|
| `gradlew --offline :shared:testAndroidHostTest :androidApp:lintRelease :androidApp:assembleRelease` | **BUILD SUCCESSFUL** (3 phút 49 giây). 228 test, 0 fail/error/skip. Lint release: 0 error, 48 warning. APK `androidApp-release-unsigned.apk` 29,0 MB (**không ký** vì không có cấu hình ký trên máy) |
| `zipalign -c -P 16 -v 4` (build‑tools 36.0.0) | “Verification successful”, 16/16 `.so` OK |
| Phân tích ELF `PT_LOAD` (arm64‑v8a, x86_64) | 8/8 thư viện align 16384 |
| `python tools/testing/test_database.py` (PostgreSQL 18 local, cổng 55439, DB dùng một lần) | Exit 0. PASS: replay migration từ đầu ×2 + suite SQL/RLS; 22 kịch bản đồng thời (claim trùng/khác, mua vượt số dư, gacha, exchange, pet care, feed, equip, khởi tạo tài khoản) |

Lưu ý: kết quả trên áp dụng cho **working tree chưa commit**, không phải một commit/tag có thể tái tạo. Test SQL dùng shim Auth, không phải Supabase hosted.

## 2. Đánh giá theo phạm vi

| Lĩnh vực | Đánh giá | Phát hiện chính |
|---|---|---|
| **Android Architecture** | 🟢 Tốt | KMP + Compose, Clean Architecture (domain/usecase/repository/ViewModel + `ArchitectureTest`), Koin DI, StateFlow. AGP 9.1.1, Kotlin 2.4.21, Gradle 9.5.1, compile/target 37, minSdk 24. R8 + shrinkResources bật. Focus dựa trên thời điểm server, không cần foreground service; alarm có fallback inexact và khôi phục sau boot. Widget, 4 kênh thông báo. ⚠️ Khoá portrait bị bỏ qua trên màn hình lớn (target ≥ 36); ⚠️ language split AAB (REL‑P1‑07); P3: Filament/SceneView (~15 MB native/4 ABI) vẫn đóng gói dù renderer “inactive”; `compose.uiTooling` là `implementation` của `shared` nên vào release |
| **Release config & signing** | 🔴 | REL‑P1‑01: không tag, working tree bẩn, versionCode mặc định 1, release âm thầm không ký; chưa có lệnh `bundleRelease` |
| **Security** | 🟢/🟠 | Không lộ secret; Gradle chặn service key; session mã hoá Keystore; WebView khoá chặt; RLS 36/36 bảng; RPC 40/40 revoke anon; 97/97 `SECURITY DEFINER` có `search_path=''`. 🟠 Supply chain (REL‑P1‑02), xoá dữ liệu chưa đủ (REL‑P1‑03), D2D transfer (SEC‑04), cấu hình Auth hosted chưa xác minh (SEC‑05) |
| **Game Economy** | 🟢 | Server‑authoritative, idempotent theo request ID, khoá ví, ledger bất biến, RNG mật mã, rate limit, kiểm tra sức chứa trước khi quay, công bố tỷ lệ. P2: request ID Shop không bền qua process death (BE‑05); feature flag gacha chỉ bằng SQL thủ công (BE‑06); ngày thưởng UTC (BE‑07) |
| **Quality** | 🟠 | Unit/SQL test mạnh; device test có nhưng không chạy lại; **không có CI**; chưa có TalkBack, thiết bị vật lý, Doze/reboot thật, hiệu năng release (REL‑P1‑06). Localization vi/en có audit tự động (`tools/validation/audit_localization.py`) |
| **Store Compliance** | 🔴 | Thiếu privacy policy, web deletion link (P0). Target API, 16 KB, quyền, công bố tỷ lệ: đạt. Store listing, IARC, tài khoản demo cho reviewer: chưa xác minh. Xem `GOOGLE_PLAY_CHECKLIST.md` |
| **Production Readiness** | 🔴 | Migration parity chưa xác minh (P0); không crash reporting (P1); không runbook backup/rollback (P1); không CI/CD; feature flag gacha qua SQL thủ công |

## 3. Đã kiểm chứng / Rủi ro / Chưa thể xác minh

**Đã kiểm chứng (bằng đọc mã + chạy lệnh):** RLS & grants trong migration; idempotency & concurrency trên PostgreSQL local; unit test; build R8; 16 KB; target SDK; không có secret trong git; WebView hardening; quyền manifest; công bố tỷ lệ trong UI (bằng mã, chưa xem trên thiết bị).

**Có dấu hiệu rủi ro:** supply chain Gradle; language split; D2D; Shop request ID; khoá orientation trên màn hình lớn; xoá tên pet.

**Chưa thể xác minh:** trạng thái Supabase production (migration, grants, Auth settings, backup/PITR, region); Play Console (App Signing, Data safety, IARC, store listing, yêu cầu closed testing của tài khoản cá nhân); xoá tài khoản/reset mật khẩu trên hosted; hành vi trên thiết bị vật lý/OEM; accessibility.

## 4. Danh mục P2/P3 (tham chiếu)

| ID | Mức | Mô tả | Tài liệu |
|---|---|---|---|
| SEC‑04 | P2 | Thiếu `dataExtractionRules` (lint `DataExtractionRules`) | SECURITY_AUDIT |
| SEC‑05 | P2 | Cấu hình Auth hosted ngoài repo | SECURITY_AUDIT |
| BE‑05 | P2 | Request ID Shop chỉ trong bộ nhớ | BACKEND_AUDIT |
| BE‑06 | P2 | Không có kill switch gacha được tài liệu hoá | BACKEND_AUDIT |
| BE‑07 | P2 | Ngày thưởng/streak theo UTC (07:00 giờ VN) | BACKEND_AUDIT |
| ARC‑01 | P2 | Khoá portrait bị hệ thống bỏ qua trên tablet/foldable (target ≥ 36); lint `LockedOrientationActivity` | GOOGLE_PLAY_CHECKLIST T6 |
| QA‑01 | P2 | Không có CI | QA_TEST_PLAN §5 |
| ARC‑02 | P3 | Filament/SceneView native (~15 MB) đóng gói dù không dùng; tăng kích thước tải | — |
| ARC‑03 | P3 | `compose.uiTooling` ở `implementation` trong `shared/build.gradle.kts` (đi vào release) | — |
| SEC‑06 | P3 | `Log.d/e` không điều kiện trong `PetSceneView.kt`/`PetScene.kt` | SECURITY_AUDIT |
| LNT‑01 | P3 | 48 warning lint (UseKtx 16, UnusedResources 9, ApplySharedPref 7, SmallSp 4, MonochromeLauncherIcon…) | lint‑results‑release.html |
| BE‑08 | P3 | Không có `supabase/config.toml` | BACKEND_AUDIT |

## 5. Danh sách tài liệu audit
1. `RELEASE_READINESS_REPORT.md` — tài liệu này
2. `CRITICAL_ISSUES.md` — P0/P1 chi tiết
3. `SECURITY_AUDIT.md`
4. `BACKEND_AUDIT.md`
5. `QA_TEST_PLAN.md`
6. `GOOGLE_PLAY_CHECKLIST.md` — kèm nguồn chính sách và ngày tra cứu
7. `RELEASE_ACTION_PLAN.md` — thứ tự khắc phục
