# PawQuest 1.0.0 — Critical Issues (P0 / P1)

Ngày audit: 2026‑11‑20 · Commit gốc `2facef9` + 17 file chưa commit.
Định nghĩa mức độ:
- **P0** — Chặn phát hành: vi phạm chính sách Play bắt buộc, mất dữ liệu/tiền ảo, hoặc tính năng bắt buộc không hoạt động.
- **P1** — Phải sửa trước khi mở rộng rollout (có thể phát hành internal/closed testing trong khi xử lý).
- **P2** — Nên sửa trong 1.0.x. **P3** — Cải thiện.

Danh sách P2/P3 nằm trong `SECURITY_AUDIT.md`, `BACKEND_AUDIT.md` và `RELEASE_READINESS_REPORT.md` §4.

---

## P0 — Release blockers

### REL-P0-01 — Không có Privacy Policy (cả trong app lẫn tài liệu)
- **Mô tả:** Google Play yêu cầu URL privacy policy trong Play Console **và** link/văn bản trong app. Repository không có văn bản chính sách và app không có link.
- **Bằng chứng:** Tìm `privacy|policy|terms` trong `shared/src/commonMain/kotlin/com/pawquest/localization/Translations.kt` → không có kết quả; tìm `privacy` trong `*.kt`/`*.xml` → không có. Chỉ tài liệu nội bộ nhắc tới (`docs/features/AUTHENTICATION.md:68`).
- **Tác động:** App bị từ chối ở bước review hoặc bị gỡ. Nguồn: [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) (tra cứu 2026‑11‑20).
- **Tái hiện:** Mở app → Profile/Settings → không có mục Chính sách quyền riêng tư.
- **Đề xuất:** Viết chính sách (vi + en) nêu: danh tính & liên hệ nhà phát triển, dữ liệu thu thập (email, tên hiển thị, tên pet, nội dung quest, lịch sử giao dịch trong game), bộ xử lý (Supabase, region), bảo mật, **thời hạn lưu và việc giữ ledger ẩn danh sau khi xoá**, quyền người dùng. Lưu trên trang web công khai, không cần đăng nhập, không phải PDF. Thêm link trong màn hình đăng nhập và Settings.
- **Tiêu chí xác nhận:** URL truy cập được từ mạng ngoài; link trong app mở đúng trang ở cả vi/en; nội dung khớp Data safety.

### REL-P0-02 — Không có web link yêu cầu xoá tài khoản
- **Mô tả:** App cho phép tạo tài khoản → phải có cả xoá trong app **và** một trang web để yêu cầu xoá, khai báo trong Data safety.
- **Bằng chứng:** Không có trang/form/tài liệu nào trong repo; `AUTHENTICATION.md:59-69` chỉ mô tả luồng trong app.
- **Tác động:** Không hoàn thành được Data safety → không thể nộp app. Nguồn: [Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).
- **Đề xuất:** Trang web (có thể cùng domain privacy policy) nêu tên app/nhà phát triển, hướng dẫn xoá trong app, và form/email yêu cầu xoá; quy trình nội bộ để thực hiện xoá theo yêu cầu (dùng cùng logic `delete_my_account` dưới quyền admin).
- **Tiêu chí xác nhận:** URL hoạt động; thử gửi yêu cầu và tài khoản thử bị xoá trong SLA đã công bố.

### REL-P0-03 — Backend production chưa được chứng minh khớp migration; xoá tài khoản & reset mật khẩu chưa chạy trên hosted
- **Mô tả:** Tài liệu của chính dự án ghi hosted **thiếu** migration `20261112003300`, `20261112003400` và chưa áp/kiểm tra migration xoá tài khoản cùng email template OTP.
- **Bằng chứng:** `docs/TEST_REPORT.md:25` (“hosted migrations … not yet applied”), `:60-62` (“NOT TESTED: a real password-reset … and a real account deletion against the hosted project (migration and email template not applied)”), `:13`. Repo không có `supabase/config.toml` hay bản ghi migration hosted.
- **Tác động:** (1) Xoá tài khoản — tính năng **bắt buộc** — có thể lỗi trên production. (2) Quên mật khẩu không dùng được nếu template thiếu `{{ .Token }}` (`AUTHENTICATION.md:31-35`) → người dùng bị khoá ngoài tài khoản. (3) Cổng chăm sóc pet chỉ còn ở client. (4) `delete from auth.users` trong hàm `SECURITY DEFINER` mới chỉ được thử trên shim local.
- **Tái hiện:** `supabase migration list --linked` (chỉ đọc) so với 34 file trong `supabase/migrations/`.
- **Đề xuất:** Dựng staging, áp 34 migration, chạy suite SQL + QA‑A01…A04 (`QA_TEST_PLAN.md`). Sau đó backup production → áp migration còn thiếu → chạy `supabase/tests/verification.sql` → cấu hình email template.
- **Tiêu chí xác nhận:** 34/34 migration trên production; `verification.sql` 0 vi phạm; QA‑A02, QA‑A03 PASS trên staging và smoke test trên production bằng tài khoản thử.

---

## P1 — Phải xử lý trước rollout rộng

### REL-P1-01 — Build phát hành không truy vết được (working tree bẩn, không tag, versionCode mặc định 1, ký không bắt buộc)
- **Bằng chứng:** `git status`: 16 file sửa + 1 file xoá (`ShardRewardMotion.kt`) + 4 đường dẫn mới chưa commit; `git tag` rỗng. `androidApp/build.gradle.kts:91-92`: `versionCode` mặc định `"1"`, `versionName` mặc định `"1.0.0"`; `:96-109`: nếu thiếu `PAWQUEST_RELEASE_STORE_FILE` thì build release vẫn thành công nhưng **không ký** (audit tạo ra `androidApp-release-unsigned.apk`).
- **Tác động:** Không tái tạo được bản đã phát hành để điều tra sự cố; nguy cơ phát hành code chưa review; bản cập nhật sau bị Play từ chối nếu versionCode trùng.
- **Đề xuất:** Commit/review các thay đổi hiện tại; tag `v1.0.0`; build `bundleRelease` từ tag trên môi trường sạch; làm build **fail** khi thiếu cấu hình ký hoặc versionCode cho tác vụ release; bật Play App Signing.
- **Tiêu chí xác nhận:** `git status` sạch tại tag; AAB đã ký với `versionCode` > 0 xác định; `apksigner verify` OK; Play Console xác nhận App Signing.

### REL-P1-02 — Chuỗi cung ứng dependency không được kiểm soát (SEC-01)
- **Bằng chứng:** `settings.gradle.kts`: repository `maven.aliyun.com`, `.gradle/manual-maven` (“Browser-downloaded artifacts”, gồm cả `kotlin-compiler-embeddable`), không có `gradle/verification-metadata.xml`.
- **Tác động:** Artifact không rõ nguồn có thể vào APK ký chính thức.
- **Đề xuất / Tiêu chí:** xem `SECURITY_AUDIT.md` SEC‑01.

### REL-P1-03 — Xoá tài khoản không xoá tên thú cưng (SEC-02 / BE-04)
- **Bằng chứng:** `supabase/migrations/20261112003100_account_deletion.sql:34-56` không cập nhật `public.pets`; `supabase/tests/account_deletion.sql` không kiểm tra.
- **Tác động:** Dữ liệu do người dùng nhập còn lại sau khi xoá; Data safety khai “có thể xoá” sẽ không chính xác.
- **Đề xuất:** Migration mới (không sửa migration cũ) thay `delete_my_account()` để reset tên pet; thêm assertion test.
- **Tiêu chí xác nhận:** Test SQL PASS; QA‑A03 kiểm tra tên pet.

### REL-P1-04 — Không có crash reporting / monitoring production
- **Bằng chứng:** `gradle/libs.versions.toml` không có Crashlytics/Sentry/ACRA; không có endpoint log. Chỉ có Android vitals mặc định của Play.
- **Tác động:** Không phát hiện kịp crash/ANR hay lỗi RPC kinh tế sau phát hành; không có cảnh báo cho Supabase (lỗi 5xx, CPU, kết nối).
- **Đề xuất:** Tối thiểu: theo dõi Android vitals + Supabase log/alerts hằng ngày trong tuần đầu; tốt hơn: thêm SDK crash reporting (cập nhật privacy policy & Data safety tương ứng), dashboard tỷ lệ lỗi RPC.
- **Tiêu chí xác nhận:** Có người trực và quy trình kiểm tra; crash thử nghiệm hiển thị trong công cụ đã chọn.

### REL-P1-05 — Không có backup/rollback/incident runbook (BE-02)
- **Bằng chứng:** Không có tài liệu backup/PITR trong `docs/`; ledger append‑only không thể “xoá” để sửa.
- **Đề xuất / Tiêu chí:** xem `BACKEND_AUDIT.md` BE‑02.

### REL-P1-06 — Kiểm thử trên thiết bị thật & accessibility chưa thực hiện
- **Bằng chứng:** `docs/TEST_REPORT.md:45-46` (reboot/alarm), `:620-636` (Doze, TalkBack, 200% font, OEM), `:68` (hiệu năng release). Không có CI (`.github/` không tồn tại).
- **Tác động:** Focus/nhắc nhở có thể không báo đúng giờ trên máy OEM; người dùng khiếm thị không dùng được các luồng chính.
- **Đề xuất:** Thực hiện `QA_TEST_PLAN.md` §3.3–3.4 trên thiết bị vật lý qua internal testing track.
- **Tiêu chí xác nhận:** Kết quả ghi trong `TEST_REPORT.md`; pre‑launch report 0 crash.

### REL-P1-07 — Ngôn ngữ trong app có thể hỏng khi phân phối bằng AAB
- **Bằng chứng:** Lint release `AppBundleLocaleChanges` (`androidApp/build/reports/lint-results-release.xml`); `MainActivity.kt:18-22` tự đặt locale; không có cấu hình `bundle { language { enableSplit = false } }`.
- **Tác động:** Người dùng máy tiếng Anh chọn Tiếng Việt trong app sẽ thấy resource Android (tên app, kênh thông báo, widget) không dịch — chỉ phát hiện được khi cài qua Play.
- **Đề xuất:** Tắt language split hoặc dùng per‑app language API (`LocaleManager`/`AppCompatDelegate.setApplicationLocales`) kèm `locales_config`.
- **Tiêu chí xác nhận:** QA‑Q04 PASS trên bản cài từ internal track.

---

## Tóm tắt
| Mức | Số lượng | ID |
|---|---|---|
| P0 | 3 | REL‑P0‑01, 02, 03 |
| P1 | 7 | REL‑P1‑01 … 07 |
