# PawQuest — Production Runbook

Dành cho người trực phát hành 1.0.x. Mọi thao tác SQL trên production phải: (1) có người thứ hai xem lại, (2) chạy trong `begin … commit`, (3) ghi vào nhật ký sự cố (không ghi email/tên người dùng).

## 1. Thông tin cần điền trước khi phát hành

| Mục | Giá trị |
|---|---|
| Supabase project production (ref, region) | `{{PROJECT_REF}}`, `{{REGION}}` |
| Supabase project staging | `{{STAGING_REF}}` |
| Gói Supabase & loại backup (Daily / PITR), thời gian lưu | `{{PLAN}}`, `{{RETENTION}}` |
| Người trực chính / phụ | `{{ONCALL}}` |
| Kênh báo động (email/Slack) | `{{ALERT_CHANNEL}}` |

## 2. Theo dõi hằng ngày (tuần đầu sau mỗi rollout)

1. **Firebase → Crashlytics**: crash‑free users theo `versionCode`; non‑fatal `ServerErrorReport` (tên RPC + mã 5xx), custom key `screen`.
2. **Play Console → Android vitals**: crash‑free users ≥ 99,5 %, ANR rate < 0,47 %, user‑perceived crash < 1,09 % (ngưỡng “bad behavior” của Play — kiểm tra lại ngưỡng hiện hành trong Play Console).
3. **Supabase → Reports / Logs**: tỷ lệ lỗi 5xx của PostgREST, CPU/RAM DB, số kết nối, Auth errors.
4. **Đối soát kinh tế** (chỉ đọc): chạy `supabase/tests/verification.sql` trên production. Kết quả là **một bảng 19 dòng**; mọi dòng phải `PASS` (dòng FAIL luôn hiện lên đầu). Khi FAIL:
   - `access` / `config`: ai đó đã sửa quyền, RLS, trigger hoặc tỷ lệ ngoài migration → xem lại thay đổi gần nhất trên Dashboard, sửa bằng migration mới.
   - `economy`: số dư lệch sổ cái → SEV‑1 nếu nhiều tài khoản: kill switch (§4.2), điều tra theo §4.3. Một dòng lẻ thường do ai đó sửa Coins/XP trực tiếp bằng SQL.
   - `privacy`: tài khoản đã xoá còn dữ liệu → chạy lại quy trình §4.6 cho tài khoản đó.
   Xem chi tiết: chạy riêng phần CTE cùng tên trong file.
5. **Đánh giá Play**: đọc review 1–2 sao, đặc biệt về mất Coins/vật phẩm.

## 3. Mức độ sự cố

| Mức | Ví dụ | Phản hồi |
|---|---|---|
| SEV‑1 | Mất/nhân đôi Coins hàng loạt, lộ dữ liệu, không đăng nhập được | Ngay lập tức: kill switch + tạm dừng rollout |
| SEV‑2 | Một tính năng hỏng (gacha, widget, nhắc nhở), crash tăng mạnh | Trong ngày |
| SEV‑3 | Lỗi hiển thị, bản dịch | Bản 1.0.x kế tiếp |

## 4. Quy trình xử lý

### 4.1 Dừng phát hành (Play)
Play Console → Release → Production → **Halt rollout**. Không thể “rút” bản đã cài; chuẩn bị bản sửa với `versionCode` lớn hơn.

### 4.1a Buộc cập nhật (sau khi bản sửa đã lên 100 % trên Play)
Chạy `supabase/operations/set_min_supported_version.sql` với `versionCode` của bản sửa. Mọi bản thấp hơn chỉ còn hiển thị “Vui lòng cập nhật PawQuest”. **Không** chạy khi bản sửa chưa có trên Play, vì người dùng sẽ bị khoá. Hạ giá trị xuống để hoàn tác. App bỏ qua kiểm tra khi offline/lỗi (fail‑open).

### 4.2 Kill switch gacha (không ảnh hưởng số dư)
- Mystery Box: chạy `supabase/operations/disable_item_case.sql`
- Lucky Wheel: chạy `supabase/operations/disable_lucky_wheel.sql`

Giao dịch đã commit vẫn được trả lại receipt khi app retry. Bật lại bằng `enable_*.sql` sau khi đã sửa và kiểm tra trên staging.

### 4.3 Sai lệch kinh tế
Ledger là **append‑only** (trigger chặn UPDATE/DELETE). Không sửa hay xoá dòng ledger.
1. Kill switch luồng liên quan.
2. Xác định phạm vi bằng truy vấn chỉ đọc (theo `created_at`, `request_id`, loại giao dịch).
3. Viết **migration bù** (bút toán điều chỉnh có lý do, qua RPC/trigger hiện có hoặc migration mới đã review), thử trên staging với bản sao dữ liệu ẩn danh, rồi áp production.
4. Chạy lại `verification.sql`.

### 4.4 Migration lỗi
1. Mỗi migration là một transaction: nếu lỗi giữa chừng thì không có gì được áp.
2. Nếu migration đã commit nhưng gây lỗi: **không** chỉnh sửa file migration cũ; viết migration mới để sửa (roll‑forward).
3. Chỉ khôi phục backup khi dữ liệu bị hỏng không thể sửa bằng roll‑forward (xem 4.5) — khôi phục sẽ mất mọi giao dịch sau thời điểm backup.

### 4.5 Khôi phục từ backup
1. Khôi phục sang **project tạm** trước (Supabase: restore to new project nếu gói hỗ trợ), kiểm tra bằng `verification.sql`.
2. Quyết định (người phụ trách sản phẩm + kỹ thuật) giữa: khôi phục toàn bộ production (mất dữ liệu sau mốc) hoặc sao chép có chọn lọc.
3. Thông báo người dùng nếu có mất dữ liệu.

**Diễn tập bắt buộc trước 1.0.0:** khôi phục backup gần nhất sang project tạm, chạy `verification.sql`, ghi thời gian thực hiện (RTO) và mốc dữ liệu (RPO) vào bảng §1.

### 4.6 Yêu cầu xoá tài khoản qua web/email
1. Xác minh email gửi đến từ chính địa chỉ của tài khoản.
2. Chạy `supabase/operations/delete_account_by_email.sql` (thay địa chỉ).
3. Trả lời người dùng; ghi ngày nhận/ngày hoàn tất. Hạn tối đa: 30 ngày (như công bố tại trang xoá tài khoản).

### 4.7 Lộ khoá / sự cố bảo mật
- Publishable key là công khai theo thiết kế; nếu nghi lạm dụng: xoay key trong Supabase, phát hành bản app mới.
- Service‑role/secret key **không bao giờ** nằm trong app; nếu lộ (repo, máy dev): xoay ngay, kiểm tra Auth/DB logs.
- Mất upload key: yêu cầu Google reset upload key (Play App Signing).

## 5. Sau sự cố
Viết post‑mortem trong 5 ngày làm việc: dòng thời gian, nguyên nhân gốc, tác động (số tài khoản, không ghi danh tính), hành động phòng ngừa, test bổ sung.
