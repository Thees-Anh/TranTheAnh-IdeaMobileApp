# PawQuest 1.0.0 — QA Test Plan

Mục tiêu: đóng các khoảng trống kiểm thử mà repository ghi nhận là “NOT TESTED / manual”, và chứng minh bản build ứng viên (RC) an toàn về dữ liệu, kinh tế và tuân thủ trước khi phát hành.

## 1. Hiện trạng kiểm thử (đã kiểm chứng trong buổi audit 2026‑11‑20)

| Tầng | Số lượng / công cụ | Kết quả audit | Ghi chú |
|---|---|---|---|
| Unit/host test (`commonTest`, `androidHostTest`) | 228 test, 38 file | **228 pass, 0 fail/error/skip** | `./gradlew --offline :shared:testAndroidHostTest` trên working tree hiện tại (có 17 file chưa commit) |
| Lint release | Android Lint | **0 error, 48 warning** | Đáng chú ý: `DataExtractionRules`, `AppBundleLocaleChanges`, `LockedOrientationActivity`, `MonochromeLauncherIcon` |
| Build release R8 | `assembleRelease` | **Thành công**, APK unsigned 29,0 MB | Chưa build AAB |
| SQL/RLS/concurrency | `tools/testing/test_database.py` + 32 file `supabase/tests/*.sql` | Xem `RELEASE_READINESS_REPORT.md` | PostgreSQL local + shim Auth; **không** phải Supabase hosted |
| Device/instrumented | 15 file `androidDeviceTest` | Không chạy trong audit | Lần gần nhất ghi trong `TEST_REPORT.md:703` (145 test, emulator) |
| E2E Python/UIAutomator | `tools/testing/test_*_android.py` | Không chạy trong audit | Dùng fixture local, không phải hosted |
| CI | — | **Không có** | Không có `.github/workflows` hay pipeline nào |

**Khoảng trống chính** (trích `docs/TEST_REPORT.md`): xoá tài khoản thật và reset mật khẩu thật trên hosted (`:60-62`); migration 003300/003400 trên hosted (`:25`); reboot & chuỗi alarm inexact (`:45-46`); TalkBack, cỡ chữ 200%, thiết bị vật lý/OEM, Doze thật (`:620-636`); hiệu năng release trên GPU thật (`:68`).

## 2. Môi trường

| Môi trường | Mục đích | Điều kiện |
|---|---|---|
| Staging Supabase (project riêng) | Toàn bộ test hosted | Áp 34 migration từ đầu; Auth & email template giống production; dữ liệu giả |
| Thiết bị vật lý tối thiểu | Pixel (Android 16/17), Samsung (One UI, Android 15+), máy Android 7–9 cấp thấp (minSdk 24), 1 tablet/foldable | Cài từ **internal testing track** (AAB qua Play) để kiểm tra language split và chữ ký |
| Mạng | Wi‑Fi, 4G, chế độ máy bay, mạng chập chờn (proxy throttling) | |

## 3. Ca kiểm thử bắt buộc trước phát hành (P0/P1)

Mỗi ca ghi: ID, bước, kết quả mong đợi, bằng chứng (ảnh/log/truy vấn). Ca kinh tế phải kèm truy vấn `verification.sql` sau khi chạy.

### 3.1 Tài khoản & quyền riêng tư
| ID | Kịch bản | Kết quả mong đợi |
|---|---|---|
| QA-A01 | Đăng ký → xác nhận email → đăng nhập (staging) | Có profile, ví 0 Coins, 1 pet |
| QA-A02 | Quên mật khẩu → nhận mã OTP qua email → đặt mật khẩu mới | Mã có trong email; đăng nhập bằng mật khẩu mới; mật khẩu cũ bị từ chối |
| QA-A03 | Xoá tài khoản từ app (nhập sai mật khẩu, rồi đúng) | Sai: không xoá. Đúng: `auth.users/identities/refresh_tokens` không còn bản ghi; quest/profile/**pet name** đã ẩn danh; app về màn hình đăng nhập; widget/alarm/cache bị xoá |
| QA-A04 | Đăng nhập lại bằng email đã xoá | Như tài khoản mới, không thấy dữ liệu cũ |
| QA-A05 | Yêu cầu xoá qua **web link** | Form hoạt động, có tên app/nhà phát triển, có quy trình xử lý |
| QA-A06 | Link privacy policy trong app | Mở được, đúng nội dung, cả vi/en |
| QA-A07 | Hết hạn session khi offline, mở lại online | Làm mới token hoặc yêu cầu đăng nhập; không mất dữ liệu |

### 3.2 Kinh tế game (staging, ghi lại số dư trước/sau)
| ID | Kịch bản | Kết quả mong đợi |
|---|---|---|
| QA-E01 | Focus 5 phút → claim | +10 Coins, +5 XP, 1 receipt |
| QA-E02 | Claim 6 phiên trong 1 ngày UTC | Pay theo bảng cap; phiên 6 bị từ chối; tổng ≤ 250 Coins |
| QA-E03 | Bật chế độ máy bay ngay sau khi bấm Claim/Mua/Open Case/Spin/Feed, bật lại, bấm Retry | Chỉ một giao dịch; UI hiển thị đúng kết quả |
| QA-E04 | Kill process sau khi bấm Open Case/Spin (trước khi có phản hồi), mở lại | App khôi phục yêu cầu đang chờ, không cho mở lượt mới tới khi xác nhận |
| QA-E05 | Như E04 cho **Shop** | Ghi nhận hành vi (BE‑05); không trừ tiền hai lần cho cùng một request |
| QA-E06 | Double‑tap nhanh mọi nút giao dịch | Một giao dịch |
| QA-E07 | Hai thiết bị cùng tài khoản mua đồng thời với số dư vừa đủ một lần | Một thành công, một `insufficient_coins` |
| QA-E08 | Gacha khi Hunger/Water ≤ 25 | Nút bị khoá trên UI; gọi RPC trực tiếp trả `pet_needs_care`, không trừ tiền (cần migration 033 trên hosted) |
| QA-E09 | Inventory đầy → Lucky Wheel | `inventory_full`, không trừ tiền, không quay |
| QA-E10 | Xem bảng tỷ lệ trước khi Open/Spin, cả vi/en | Tổng tỷ lệ 100%; khớp DB |
| QA-E11 | Tắt banner bằng SQL trong khi đang mở màn hình | Thông báo lịch sự, không crash, không trừ tiền |
| QA-E12 | Đổi shard (Item/Pet) | Quote → redeem đúng giá; retry không nhân đôi |

### 3.3 Nền & hệ thống
| ID | Kịch bản | Kết quả mong đợi |
|---|---|---|
| QA-S01 | Focus 25 phút với màn hình khoá, Doze (`adb shell dumpsys deviceidle force-idle`) | Thông báo “Focus finished” đúng giờ (±1 phút nếu có quyền exact) |
| QA-S02 | Khởi động lại máy giữa phiên Focus & khi có nhắc quest | Alarm được khôi phục |
| QA-S03 | Từ chối `POST_NOTIFICATIONS` / exact alarm | App vẫn dùng được; nhắc trễ trong cửa sổ cho phép |
| QA-S04 | Đổi múi giờ/giờ hệ thống | Alarm sắp xếp lại; ngày thưởng vẫn theo UTC server |
| QA-S05 | Widget trên launcher Pixel/Samsung, đổi ngôn ngữ, dark mode | Hiển thị đúng; streak/done khác “—” sau migration 034 |
| QA-S06 | Cập nhật từ bản internal trước lên RC | Không mất session; alarm được đặt lại (`MY_PACKAGE_REPLACED`) |

### 3.4 Chất lượng
| ID | Kịch bản | Kết quả mong đợi |
|---|---|---|
| QA-Q01 | TalkBack toàn bộ luồng đăng nhập, quest, Focus, Shop, gacha, xoá tài khoản | Mọi nút có nhãn, thứ tự đọc hợp lý (16 chỗ `contentDescription = null` cần rà soát là trang trí) |
| QA-Q02 | Cỡ chữ 200%, màn hình 320×568dp, bàn phím mở | Không cắt chữ, nút truy cập được |
| QA-Q03 | Tablet/foldable, xoay màn hình | Không vỡ layout; trạng thái không mất |
| QA-Q04 | Cài từ Play internal track trên máy ngôn ngữ English, chuyển sang Tiếng Việt trong app | Mọi chuỗi (kể cả kênh thông báo, widget, tên app) đúng tiếng Việt |
| QA-Q05 | Hiệu năng: khởi động lạnh, cuộn danh sách, animation gacha trên máy cấp thấp | Không ANR; khởi động lạnh < 5 s; không rò rỉ bộ nhớ sau 20 lần mở gacha |
| QA-Q06 | Mất mạng giữa mọi màn hình | Banner offline, không crash, retry hoạt động |
| QA-Q07 | Pre‑launch report của Play Console | 0 crash, 0 cảnh báo bảo mật/accessibility nghiêm trọng |

## 4. Tiêu chí thoát (Exit criteria) cho RC
- 100% ca P0/P1 ở §3.1–3.3 PASS trên staging với bằng chứng lưu trong `docs/TEST_REPORT.md`.
- Unit test, lint release (0 error), SQL suite PASS trên **commit được gắn tag**, build từ môi trường sạch.
- Pre‑launch report: 0 crash.
- Không còn issue P0/P1 mở trong `CRITICAL_ISSUES.md`.

## 5. Tự động hoá đề xuất (sau 1.0.0 hoặc song song)
CI chạy trên mỗi PR: `:shared:testAndroidHostTest`, `:androidApp:lintRelease`, `:androidApp:bundleRelease`, `tools/testing/test_database.py` trên PostgreSQL container, các script `tools/validation/*.py --check`, `zipalign -c -P 16`.
