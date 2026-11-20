# PawQuest 1.0.0 — Release Action Plan

Nguyên tắc sắp xếp: **bảo mật → mất dữ liệu/quyền riêng tư → giao dịch kinh tế → tuân thủ phát hành → vận hành → chất lượng/UI/UX**. Mỗi bước có tiêu chí hoàn thành; không chuyển sang “Gate” kế tiếp khi bước trước chưa đạt.

Ước lượng công sức: S ≤ 0,5 ngày · M ≤ 2 ngày · L > 2 ngày (một người).

---

## Giai đoạn 0 — Đóng băng & truy vết (ngay lập tức)

| # | Việc | Issue | Công | Hoàn thành khi |
|---|---|---|---|---|
| 0.1 | Review và commit 17 thay đổi đang dở (gacha full‑screen, reward flight); chạy lại test | REL‑P1‑01 | S | `git status` sạch, 228+ test pass |
| 0.2 | Tạo nhánh `release/1.0.0`; quy ước versionCode (vd. 10000) | REL‑P1‑01 | S | Nhánh tồn tại, versionCode ghi trong tài liệu release |

## Giai đoạn 1 — Bảo mật & chuỗi cung ứng

| # | Việc | Issue | Công | Hoàn thành khi |
|---|---|---|---|---|
| 1.1 | Bỏ Aliyun mirror và `manual-maven` khỏi cấu hình build release; tạo `verification-metadata.xml` | REL‑P1‑02 | M | Build release sạch chỉ từ Google/Maven Central, verification `strict` |
| 1.2 | Đối chiếu cấu hình Supabase Auth production (rate limit, độ dài mật khẩu, JWT expiry, CAPTCHA/leaked password nếu có) và ghi lại | SEC‑05 | S | Checklist cấu hình ký duyệt |
| 1.3 | Chạy Supabase Security Advisor trên staging & production (chỉ đọc) | REL‑P0‑03 | S | 0 cảnh báo mức ERROR |

## Giai đoạn 2 — Dữ liệu người dùng & xoá tài khoản

| # | Việc | Issue | Công | Hoàn thành khi |
|---|---|---|---|---|
| 2.1 | Dựng **staging** Supabase, áp 34 migration từ đầu, chạy `supabase/tests/*.sql` + `verification.sql` | REL‑P0‑03 | M | Toàn bộ PASS trên staging |
| 2.2 | Migration mới: `delete_my_account()` reset tên pet; bổ sung test | REL‑P1‑03 | S | `account_deletion.sql` kiểm tra không còn text người dùng |
| 2.3 | Thêm `dataExtractionRules` loại trừ SharedPreferences | SEC‑04 | S | Lint `DataExtractionRules` biến mất |
| 2.4 | Chạy QA‑A01…A04, A07 trên staging (đăng ký, OTP reset, xoá tài khoản thật) | REL‑P0‑03 | M | Bằng chứng trong `TEST_REPORT.md` |
| 2.5 | Viết runbook backup/PITR/khôi phục; xác nhận gói Supabase có backup; diễn tập khôi phục 1 lần | REL‑P1‑05 | M | Runbook có trong `docs/`, diễn tập thành công |
| 2.6 | **Production:** backup → áp các migration còn thiếu theo thứ tự → `verification.sql` → cấu hình email template OTP | REL‑P0‑03 | S | 34/34 migration; 0 vi phạm; smoke test bằng tài khoản thử |

## Giai đoạn 3 — Giao dịch kinh tế

| # | Việc | Issue | Công | Hoàn thành khi |
|---|---|---|---|---|
| 3.1 | Chạy QA‑E01…E12 trên staging, đối soát `verification.sql` sau mỗi nhóm | — | M | 0 sai lệch số dư/ledger |
| 3.2 | Lưu request ID Shop vào pending store bền | BE‑05 | M | QA‑E05 PASS |
| 3.3 | Thêm `disable_item_case.sql` / `disable_lucky_wheel.sql` vào runbook; xác minh UI khi banner tắt | BE‑06 | S | QA‑E11 PASS |
| 3.4 | Quyết định bật Mystery Box/Lucky Wheel ở 1.0.0 hay để tắt lúc ra mắt (giảm rủi ro) | — | S | Quyết định được ghi lại |

## Giai đoạn 4 — Tuân thủ Google Play

| # | Việc | Issue | Công | Hoàn thành khi |
|---|---|---|---|---|
| 4.1 | Viết & đăng privacy policy (vi/en), thêm link trong app (màn đăng nhập + Settings) | REL‑P0‑01 | M | QA‑A06 PASS |
| 4.2 | Trang web yêu cầu xoá tài khoản + quy trình xử lý | REL‑P0‑02 | S | QA‑A05 PASS |
| 4.3 | Điền Data safety theo `GOOGLE_PLAY_CHECKLIST.md` §5; IARC; Target audience (khuyến nghị 13+); Ads = No | — | S | Play Console không còn mục App content thiếu |
| 4.4 | Sửa language split (per‑app locale hoặc `enableSplit=false`) | REL‑P1‑07 | S | QA‑Q04 PASS |
| 4.5 | Build fail khi thiếu cấu hình ký/versionCode cho release; tạo `bundleRelease`; bật Play App Signing | REL‑P1‑01 | S | AAB đã ký, `apksigner verify` OK |
| 4.6 | Store listing (vi/en), ảnh chụp, feature graphic; tài khoản demo + hướng dẫn trong App access | — | M | Listing hoàn chỉnh |
| 4.7 | Kiểm tra lại toàn bộ chính sách trên Play Console ngay trước khi nộp (tài liệu này tra cứu ngày 2026‑11‑20) | — | S | Ghi ngày đối chiếu |

**Gate A → CONDITIONALLY READY:** Giai đoạn 0–2 và 4.1, 4.2, 4.5 hoàn tất → upload AAB lên **internal testing**.

## Giai đoạn 5 — Vận hành

| # | Việc | Issue | Công | Hoàn thành khi |
|---|---|---|---|---|
| 5.1 | Chọn crash reporting (hoặc cam kết theo dõi Android vitals hằng ngày), cập nhật privacy/Data safety nếu thêm SDK | REL‑P1‑04 | M | Crash thử hiển thị trên dashboard |
| 5.2 | Bật cảnh báo Supabase (lỗi API, CPU/DB, dung lượng) | REL‑P1‑04 | S | Cảnh báo thử nhận được |
| 5.3 | CI tối thiểu: unit test, lint release, SQL suite, `bundleRelease`, `zipalign -P 16` | QA‑01 | M | Pipeline xanh trên tag |
| 5.4 | Kế hoạch rollout: internal → closed → production 10% → 50% → 100%, tiêu chí dừng (crash‑free < 99,5%, lỗi RPC kinh tế > ngưỡng) | — | S | Tài liệu rollout |

## Giai đoạn 6 — Chất lượng & UI/UX

| # | Việc | Issue | Công | Hoàn thành khi |
|---|---|---|---|---|
| 6.1 | Thiết bị vật lý: QA‑S01…S06 (Doze, reboot, quyền, múi giờ, widget, update) | REL‑P1‑06 | M | PASS, có bằng chứng |
| 6.2 | Accessibility: QA‑Q01, Q02 (TalkBack, 200% font) | REL‑P1‑06 | M | Không còn lỗi chặn |
| 6.3 | Màn hình lớn / xoay: QA‑Q03 | ARC‑01 | M | Không vỡ layout |
| 6.4 | Hiệu năng release trên máy thấp: QA‑Q05; pre‑launch report | REL‑P1‑06 | M | 0 crash/ANR |
| 6.5 | Dọn dẹp P3: loại Filament/SceneView khỏi release nếu không dùng, `uiTooling` → debug, guard `Log`, warning lint | ARC‑02/03, SEC‑06, LNT‑01 | M | APK nhỏ hơn, lint ít warning hơn |
| 6.6 | Làm rõ giờ reset UTC trong UI | BE‑07 | S | Copy UI cập nhật |

**Gate B → READY (production rollout):** Toàn bộ P0/P1 đóng, Exit criteria trong `QA_TEST_PLAN.md` §4 đạt, closed testing không có lỗi chặn.

---

## Thứ tự tóm tắt
0.1 → 0.2 → 1.1 → 2.1 → 2.2 → 2.4 → 2.5 → 2.6 → 3.1 → 4.1 → 4.2 → 4.5 → **Gate A (internal testing)** → 1.2/1.3, 2.3, 3.2–3.4, 4.3/4.4/4.6/4.7 → 5.x → 6.1–6.4 → **Gate B (production)** → 6.5–6.6 (1.0.x).
