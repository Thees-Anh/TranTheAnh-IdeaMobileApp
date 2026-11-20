# PawQuest 1.0.0 — Backend & Game Economy Audit

Ngày audit: 2026-11-20 · Phạm vi: `supabase/migrations/` (34 file, 3.538 dòng), `supabase/operations/`, `supabase/tests/`, repository Kotlin gọi RPC, `tools/testing/test_database.py`.
Không truy cập project Supabase hosted. Mọi kết luận về hành vi đều dựa trên mã SQL và bộ test chạy trên **PostgreSQL 18 local, cơ sở dữ liệu dùng một lần** (xem §6).

---

## 1. Kiến trúc kinh tế (đã kiểm chứng từ mã)

| Luồng | RPC | Idempotency | Khoá / Atomic | Kiểm tra phía server |
|---|---|---|---|---|
| Phần thưởng Focus (Coins/XP) | `start_quest_session`, `claim_quest_reward`, `reward_claim` | 1 receipt / session (`unique` session ref) | Khoá `wallets` → session; trigger BEFORE/AFTER INSERT trên ledger trong cùng transaction | Thời lượng snapshot bất biến, `ends_at` do server gán, tối đa 5 lần & 250 Coins / ngày UTC (`DATABASE.md:118-147`) |
| Shop | `shop_purchase` (migration 011) | `unique(user_id,request_id)` (`20261105001100_shop_purchases.sql:16`) | `for update` trên wallet và inventory (`:38,:43`) | Giá do server, số lượng 1–100, giới hạn `max_quantity` |
| Chăm sóc pet / Bistro | `pet_care`, `bistro_feed` | `request_id` + `unique(user_id,request_id)` (`20261111002900_pawquest_bistro.sql:24,56`) | Wallet lock | `p_expected_price` phải khớp giá server |
| Mystery Box | `item_case_open(uuid,uuid)` | request ID + snapshot phản hồi bất biến | Wallet lock, rollback toàn bộ khi lỗi inventory/shard | RNG `gen_random_bytes` + rejection sampling; rate limit 30/60 s; cổng chăm sóc pet (migration 033) |
| Lucky Wheel | `lucky_wheel_spin(uuid,text,uuid)` | `wheel_request_used`, replay trả receipt cũ, khác payload → `request_conflict` (`20261110002800_lucky_wheel.sql:304-309`) | `for update` wallet + inventory, kiểm tra sức chứa **trước** khi quay (`:318-325`) để không có “reroll miễn phí” | Trọng số tổng 10.000; rate limit |
| Exchange shard | `item_shard_quote/redeem`, `pet_shard_quote/redeem` | Quote riêng tư bất biến | Wallet lock | Giá theo phiên bản cấu hình đã publish |
| Ledger | `wallet_transactions`, `shard_ledger`, `lucky_wheel_ticket_ledger`… | — | Trigger `reject_ledger_mutation` chặn UPDATE/DELETE | Chỉ append |

Client (Kotlin) giữ cùng request ID khi retry: Shop (`ShopViewModel.kt:131-133`), Pet care (`PetViewModel.kt:54`), Bistro/Item Case/Exchange/Lucky Wheel lưu request ID xuống `noBackupFilesDir` trước khi gửi (`AndroidPendingItemCaseStore.kt`, `AndroidPendingLuckyOperationStore.kt`, `AndroidBistroIntentStore.kt`; `docs/features/PAWQUEST_BISTRO.md:18`).

**Đánh giá:** Thiết kế kinh tế là server‑authoritative, idempotent và atomic. Không phát hiện đường nào cho client tự cộng Coins/XP/shard.

---

## 2. Phát hiện

| ID | Mức | Tiêu đề | Trạng thái |
|---|---|---|---|
| BE-01 | P0 | Không xác minh được migration parity giữa repo và project hosted; tài liệu xác nhận hosted đang thiếu migration | CHƯA XÁC MINH / có bằng chứng lệch |
| BE-02 | P1 | Không có quy trình backup, PITR, rollback và incident recovery được tài liệu hoá | ĐÃ KIỂM CHỨNG (thiếu) |
| BE-03 | P1 | Xoá tài khoản chưa từng chạy trên hosted; nghi vấn quyền DELETE `auth.users` từ hàm của migration owner | CHƯA XÁC MINH |
| BE-04 | P1 | Tên thú cưng không bị xoá khi xoá tài khoản | ĐÃ KIỂM CHỨNG (xem SEC-02) |
| BE-05 | P2 | Request ID mua hàng Shop chỉ ở bộ nhớ — mất khi process bị kill | ĐÃ KIỂM CHỨNG |
| BE-06 | P2 | Bật Mystery Box / Lucky Wheel là thao tác SQL thủ công, không có feature flag phía client hay kill switch được tài liệu hoá | ĐÃ KIỂM CHỨNG |
| BE-07 | P2 | “Ngày thưởng” cố định theo UTC — người dùng VN (UTC+7) bị reset giới hạn lúc 07:00 sáng | ĐÃ KIỂM CHỨNG (quyết định thiết kế) |
| BE-08 | P3 | Không có `supabase/config.toml`; quy trình áp migration phụ thuộc thao tác tay | ĐÃ KIỂM CHỨNG |

### BE-01 — Migration parity (P0)

- **Mô tả:** Repository có 34 migration (`20261105000100` → `20261112003400`). Không có cách nào trong repo xác nhận project hosted đang ở phiên bản nào. Bằng chứng ngược lại có sẵn:
  - `docs/TEST_REPORT.md:25`: “hosted migrations 20261112003300/20261112003400 not yet applied”.
  - `docs/TEST_REPORT.md:60-62`: migration xoá tài khoản và email template reset chưa được áp dụng/kiểm tra trên hosted.
  - `docs/TEST_REPORT.md:13`: widget hiển thị “—” do hosted thiếu 003400.
- **Tác động thực tế:** App 1.0.0 gọi các RPC/field chưa tồn tại → tính năng xoá tài khoản (bắt buộc theo Play) có thể lỗi; cổng chăm sóc pet chỉ còn ở client (server không chặn); widget thiếu dữ liệu. Nếu migration được áp vội lúc phát hành mà không diễn tập, có rủi ro khoá bảng ledger/wallet.
- **Tái hiện:** `supabase migration list --linked` (chỉ đọc) và so với `ls supabase/migrations`.
- **Đề xuất:** (1) Tạo project **staging** riêng, áp toàn bộ 34 migration từ đầu, chạy `supabase/tests/*.sql` và `verification.sql`. (2) Với production: chụp backup, áp migration còn thiếu theo thứ tự, chạy `verification.sql`. (3) Ghi kết quả vào `docs/TEST_REPORT.md`.
- **Tiêu chí xác nhận:** Danh sách migration hosted trùng khớp 34/34; `verification.sql` không trả dòng vi phạm; smoke test xoá tài khoản + reset mật khẩu thành công trên staging.

### BE-02 — Backup / rollback / incident (P1)

- **Mô tả:** Tìm `backup|PITR|rollback` trong `docs/` chỉ ra kết quả về `allowBackup` và rollback trong test. Không có runbook. Ledger append‑only nghĩa là “rollback” dữ liệu kinh tế phải là bút toán bù, không phải xoá.
- **Tác động:** Khi lỗi migration hoặc bug kinh tế (ví dụ thưởng sai) không có quy trình khôi phục, không biết RPO/RTO.
- **Đề xuất:** Xác nhận gói Supabase có daily backup/PITR; viết `docs/operations/RUNBOOK.md` gồm: khôi phục từ backup, tắt banner gacha (`update ... set enabled=false`), bút toán bù ledger, liên hệ người dùng, quy trình Play “halt rollout”.
- **Tiêu chí xác nhận:** Có runbook; đã diễn tập khôi phục backup sang project tạm ít nhất một lần.

### BE-03 — Xoá tài khoản trên hosted (P1, CHƯA XÁC MINH)

- **Mô tả:** `delete_my_account()` là `SECURITY DEFINER` và thực hiện `delete from auth.users` (`20261112003100_account_deletion.sql:54`). Trên Supabase, quyền của role sở hữu migration đối với schema `auth` do nền tảng quản lý; chỉ được test trên shim local (`supabase/tests/local_auth_shim.sql`).
- **Tác động:** Nếu bị từ chối quyền, người dùng không xoá được tài khoản → vi phạm chính sách Play về xoá tài khoản.
- **Đề xuất:** Trên staging: đăng ký tài khoản thử, tạo dữ liệu, gọi xoá từ app; kiểm tra `auth.users`, `auth.identities`, `auth.refresh_tokens` không còn bản ghi.
- **Tiêu chí xác nhận:** Bản ghi kết quả test trên staging trong `TEST_REPORT.md`.

### BE-05 — Request ID Shop không bền (P2)

- **Bằng chứng:** `ShopViewModel.kt:108,121` tạo `Uuid.random()` và chỉ lưu trong `state.pendingRequest` (bộ nhớ). Các luồng gacha/Bistro thì lưu `AtomicFile` (`docs/gacha/ITEM_CASE_FINAL_TEST_REPORT.md:11`).
- **Tác động:** Timeout → người dùng thoát app → server đã trừ Coins nhưng app không hiển thị kết quả; người dùng có thể mua lại (hai lần mua hợp lệ, không phải double‑spend, nhưng gây khiếu nại). Mức độ: thấp vì inventory được tải lại.
- **Đề xuất:** Dùng chung cơ chế pending store cho Shop.
- **Tiêu chí xác nhận:** Test: kill process sau khi gửi, mở lại → app khôi phục và hỏi lại server bằng cùng request ID.

### BE-06 — Feature flag (P2)

- **Bằng chứng:** `supabase/operations/enable_item_case.sql`, `enable_lucky_wheel.sql` bật `enabled=true` sau các kiểm tra cấu hình. Không có script tắt tương ứng; client không có remote config.
- **Đề xuất:** Thêm `disable_*.sql` vào runbook; xác minh UI hiển thị trạng thái “banner_unavailable” lịch sự khi tắt.

### BE-07 — Ngày thưởng UTC (P2)

- **Bằng chứng:** `DATABASE.md:150-162`. Với người dùng Việt Nam, “ngày” thưởng đổi lúc 07:00. Streak trong Profile/widget cũng dùng UTC (`IMPLEMENTATION_LOG.md`).
- **Tác động:** Gây nhầm lẫn về “hôm nay”. Không phải lỗi bảo mật.
- **Đề xuất:** Hiển thị rõ giờ reset trong UI hoặc lên kế hoạch migration kế toán (không sửa receipt cũ).

---

## 3. Race condition & phục hồi khi mất mạng

| Kịch bản | Kết quả theo thiết kế | Bằng chứng test |
|---|---|---|
| 6 claim đồng thời cùng session | 1 receipt, các lần khác trả cùng receipt | `test_database.py` (“six-way duplicate claim”) |
| 6 claim đồng thời khác session | Không vượt 5 slot/250 Coins | “six-way distinct claim” |
| Mua đồng thời vượt số dư | Từ chối, không âm ví | `TEST_REPORT.md:581` |
| Timeout sau khi server commit (gacha) | Retry cùng request ID trả đúng receipt | `TEST_REPORT.md:705` (ViewModel test) |
| Đóng app khi đang đếm ngược Focus | Thời gian do server, khôi phục khi mở lại | `TEST_REPORT.md:578` |
| Offline | Chỉ xem dữ liệu cache, mọi thay đổi cần server | `TEST_REPORT.md:36-37` |

---

## 4. Kiểm soát truy cập
Xem `SECURITY_AUDIT.md` §2. Tóm tắt: RLS bật trên 36/36 bảng `public`; 40/40 hàm `public.*` revoke khỏi `anon`; 97/97 hàm `SECURITY DEFINER` có `search_path=''`.

## 5. Dữ liệu ngẫu nhiên & minh bạch tỷ lệ
- Mystery Box: tỷ lệ theo tier (bps) và từng vật phẩm hiển thị trong app (`ItemCasePanels.kt:105-114`), làm tròn 4 chữ số thập phân.
- Lucky Wheel: trọng số 8 ô cấu hình trong `20261110002800_lucky_wheel.sql:521-524`, có màn hình tỷ lệ (`LuckyWheelContent.kt:135`).
- Không có thanh toán tiền thật (không có Google Play Billing trong `libs.versions.toml`); Coins chỉ kiếm được qua Focus.

## 6. Kết quả kiểm thử backend trong buổi audit
Xem mục “Kết quả chạy lệnh” trong `RELEASE_READINESS_REPORT.md` (chạy `tools/testing/test_database.py` trên cụm PostgreSQL 18 local, cổng 55439, các DB sinh ngẫu nhiên và bị xoá sau test).
