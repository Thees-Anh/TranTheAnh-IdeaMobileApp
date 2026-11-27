# PawQuest 1.0.0 — Google Play Compliance Checklist

Ngày đối chiếu chính sách: **2026-11-20**. Nguồn là các trang Play Console Help / Android Developers truy cập qua tìm kiếm web cùng ngày. Chính sách Google Play thay đổi thường xuyên; **phải đối chiếu lại trên Play Console ngay trước khi nộp**. Những mục không thể kiểm tra từ repository (Play Console, store listing, website) được đánh dấu **CHƯA XÁC MINH**.

Ký hiệu: ✅ đạt (đã kiểm chứng) · ❌ chưa đạt · ⚠️ rủi ro / cần làm thêm · ❓ chưa xác minh

---

## 1. Yêu cầu kỹ thuật

| # | Yêu cầu | Nguồn & thời điểm áp dụng | Trạng thái | Bằng chứng |
|---|---|---|---|---|
| T1 | Target API ≥ 36 (Android 16) cho app mới/cập nhật | [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en): bắt buộc từ 31/08/2026 (gia hạn tối đa 01/11/2026). API 37 dự kiến bắt buộc 08/2027 ([Android Developers Blog, 02/2026](https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html)) | ✅ | `gradle/libs.versions.toml`: `android-targetSdk = "37"`, `compileSdk = "37"` |
| T2 | Hỗ trợ 16 KB page size (app có native code, target ≥ 35) | [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes): trang mới nhất ghi chặn cập nhật không hỗ trợ từ **01/02/2027**; các bài blog 2025 ghi 01/11/2025 ([blog 05/2025](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html)). Coi như **đã bắt buộc** | ✅ | `zipalign -c -P 16 -v 4` trên `androidApp-release-unsigned.apk`: “Verification successful” cho 16 file `.so`; phân tích ELF: mọi segment LOAD của arm64‑v8a/x86_64 align = 16384 |
| T3 | 64‑bit | [64-bit requirement](https://developer.android.com/google/play/requirements/64-bit) | ✅ | APK có `arm64-v8a`, `x86_64` |
| T4 | Định dạng AAB, ký bằng upload key, Play App Signing | Play Console | ❓ | `build.gradle.kts:96-104`: ký release chỉ khi có `PAWQUEST_RELEASE_STORE_FILE`; build audit cho ra **APK unsigned**. Chưa có lệnh/CI tạo `bundleRelease` |
| T5 | `versionCode` tăng dần, không trùng | Play Console | ⚠️ | `versionCode` mặc định `1` nếu thiếu `PAWQUEST_VERSION_CODE` (`build.gradle.kts:91`) |
| T6 | Chất lượng màn hình lớn / thay đổi orientation | [Android 17 resizability](https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html); [adaptive quality standards](https://developer.android.com/blog/posts/google-play-adaptive-quality-standards) | ⚠️ | Manifest khoá `portrait`; với target 36+ hệ thống bỏ qua khoá trên màn hình ≥ 600dp. `TEST_REPORT.md:58` ghi app hiển thị cột dọc ở giữa trên tablet; lint `LockedOrientationActivity` |
| T7 | Ngôn ngữ trong app khi phát hành AAB | Lint `AppBundleLocaleChanges` | ⚠️ | App đổi locale trong app (`MainActivity.kt:18-22`) nhưng không tắt language split → khi cài từ AAB, resource Android (tên app, kênh thông báo, widget) tiếng Việt có thể không được tải trên thiết bị không dùng tiếng Việt. Cần `bundle { language { enableSplit = false } }` hoặc dùng `LocaleManager`/`AppCompatDelegate` |

## 2. Quyền riêng tư & dữ liệu người dùng

| # | Yêu cầu | Nguồn | Trạng thái | Bằng chứng / hành động |
|---|---|---|---|---|
| P1 | Privacy policy: URL công khai trong Play Console **và** link/văn bản **bên trong app** | [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) | ❌ | Tìm `privacy` trong `shared/`, `androidApp/`, `Translations.kt` → **không có**. Không có văn bản chính sách trong repo |
| P2 | Nội dung privacy policy: nhà phát triển, liên hệ, dữ liệu thu thập/chia sẻ, bảo mật, **thời hạn lưu & xoá** (gồm ledger ẩn danh được giữ lại) | như trên; [Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) | ❌ | Chưa có. `AUTHENTICATION.md:66-69` yêu cầu công bố việc giữ ledger |
| P3 | Xoá tài khoản **trong app** | [Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) | ⚠️ | Có (`AccountSheets.kt`, RPC `delete_my_account`). **Chưa chạy trên hosted**; tên pet không bị xoá (SEC‑02) |
| P4 | **Web link** yêu cầu xoá tài khoản (không cần cài app), khai báo trong Data safety | như trên | ❌ | Không có trang web/form nào trong repo hoặc tài liệu |
| P5 | Data safety form khớp thực tế | [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) | ❓ | Xem bảng §5 bên dưới làm dữ liệu đầu vào |
| P6 | Truyền dữ liệu mã hoá | Data safety | ✅ | HTTPS; release không cho phép cleartext |

## 3. Quyền (permissions)

| Quyền | Mục đích | Đánh giá |
|---|---|---|
| `INTERNET` | Supabase | ✅ |
| `POST_NOTIFICATIONS` | Nhắc quest, Focus | ✅ Xin runtime trên API 33+ (`CompanionAndroidHost.kt:28-29`) |
| `SCHEDULE_EXACT_ALARM` | Báo kết thúc Focus/nhắc hạn quest | ⚠️ Quyền do người dùng cấp, mặc định bị từ chối trên Android 14+; app có fallback inexact (`CompanionAlarms.kt:36-43`). Không dùng `USE_EXACT_ALARM` nên không cần khai báo chính sách exact alarm. Kiểm tra lại câu hỏi “Exact alarm” trong App content nếu Play Console yêu cầu ❓ |
| `RECEIVE_BOOT_COMPLETED` | Khôi phục alarm | ✅ Receiver `exported=false` |

Không có quyền vị trí, camera, danh bạ, ảnh, quyền truy cập tất cả gói.

## 4. Nội dung, phần thưởng ngẫu nhiên & đánh giá nội dung

| # | Yêu cầu | Nguồn | Trạng thái | Ghi chú |
|---|---|---|---|---|
| C1 | Công bố tỷ lệ vật phẩm ngẫu nhiên trước và gần thời điểm “mua” | [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en) | ✅ (một phần ❓) | Tỷ lệ tier + từng vật phẩm của Mystery Box (`ItemCasePanels.kt:105-114`), bảng tỷ lệ Lucky Wheel (`LuckyWheelContent.kt:135`). Chính sách áp cho vật phẩm ngẫu nhiên có được từ **mua**; PawQuest dùng Coins kiếm trong app, không có tiền thật — vẫn nên giữ công bố. Cần kiểm tra trên thiết bị rằng bảng tỷ lệ truy cập được **trước** nút Open/Spin |
| C2 | Bảng câu hỏi IARC | Play Console | ❓ | Khai báo: có “phần thưởng ngẫu nhiên”/vòng quay dùng tiền ảo, không có mua bằng tiền thật, không có chat/UGC công khai. Tên pet/quest là nội dung riêng tư |
| C3 | Đối tượng mục tiêu & Families | Play Console | ❓ | Nhân vật thú cưng dễ thương có thể thu hút trẻ em → nếu chọn nhóm tuổi < 13, cơ chế gacha và yêu cầu Families policy cần xem xét kỹ. Khuyến nghị chọn 13+ và có lập luận |
| C4 | Real-money gambling | [Real‑Money Gambling](https://support.google.com/googleplay/android-developer/answer/9877032?hl=en) | ✅ | Không có tiền thật, không quy đổi ra tiền thật |
| C5 | Ads | — | ✅ | Không có SDK quảng cáo. Khai báo “No ads” |

## 5. Dữ liệu đầu vào cho Data safety (từ mã nguồn)

| Loại dữ liệu | Thu thập? | Mục đích | Chia sẻ bên thứ ba | Bắt buộc | Có thể xoá |
|---|---|---|---|---|---|
| Email | Có (Supabase Auth) | Quản lý tài khoản | Không (Supabase là bộ xử lý dữ liệu) | Có | Có |
| Tên hiển thị, tên pet | Có | Chức năng app | Không | Không | ⚠️ tên pet hiện chưa bị xoá |
| Nội dung quest (tiêu đề/mô tả/hạn) | Có | Chức năng app | Không | Không | Có (thay bằng placeholder) |
| Hoạt động trong app (phiên Focus, giao dịch Coins/shard, lịch sử gacha) | Có | Chức năng app | Không | Có | **Giữ lại ẩn danh** — phải công bố |
| Crash logs, Diagnostics (App info and performance) | Có — Firebase Crashlytics, từ 2026‑11‑26 | Analytics (ổn định ứng dụng) | Không (Google là bộ xử lý dữ liệu) | **Có** — luôn gửi khi có lỗi (từ 2026‑11‑27, không có công tắc) | Google lưu tối đa 90 ngày; xoá tài khoản không xoá báo cáo đã gửi |
| Device or other IDs (mã cài đặt Firebase) | Có — kèm báo cáo sự cố | Analytics | Không | Có | Như trên |
| Vị trí, danh bạ, ảnh, sức khoẻ | Không | — | — | — | — |

Bên thứ ba trong APK: Supabase‑kt, Ktor/OkHttp, Koin, kotlinx, AndroidX, Filament/SceneView (Google, renderer không hoạt động), **Firebase Crashlytics** (từ 2026‑11‑26). Không có SDK analytics hành vi hay quảng cáo. Cần xác nhận khu vực lưu trữ Supabase (region) cho chính sách quyền riêng tư ❓.

## 6. Store listing & tài khoản nhà phát triển (CHƯA XÁC MINH)

| Mục | Trạng thái |
|---|---|
| Tên app, mô tả ngắn/dài (vi, en), icon 512×512, feature graphic 1024×500, ≥2 ảnh chụp màn hình điện thoại | ❓ Không có trong repo |
| Email liên hệ, website | ❓ |
| Tài khoản cá nhân tạo sau 13/11/2023: bắt buộc closed testing với số tester tối thiểu trong khoảng thời gian tối thiểu trước khi được lên production | ❓ Không kiểm chứng được số liệu hiện hành qua nguồn chính thức trong buổi audit — kiểm tra Play Console |
| App access cho reviewer: app yêu cầu đăng nhập → phải cung cấp **tài khoản demo** và hướng dẫn trong “App access” | ❓ Bắt buộc chuẩn bị |
| README ghi “dự án học tập” — đảm bảo tên/thương hiệu “PawQuest” không trùng nhãn hiệu đã đăng ký | ❓ |

## 7. Tổng kết chặn phát hành (Play)
1. ❌ P1 Privacy policy (Console + trong app)
2. ❌ P4 Web link xoá tài khoản
3. ⚠️ P3 Xoá tài khoản chưa được chứng minh hoạt động trên backend production
4. ❓ T4 AAB đã ký + Play App Signing, T5 versionCode
5. ❓ Tài khoản demo cho reviewer
