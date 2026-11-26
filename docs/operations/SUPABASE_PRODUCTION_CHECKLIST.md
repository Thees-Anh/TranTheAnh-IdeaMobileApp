# Supabase — Staging & Production Checklist

Thực hiện trên **staging trước**, sau đó production. Mục nào làm trên Dashboard thì ghi lại giá trị đã chọn (không ghi secret).

## A. Migration parity (REL‑P0‑03)
- [ ] `supabase link --project-ref <ref>` rồi `supabase migration list` (chỉ đọc): liệt kê đủ **36** migration của repo (`20261105000100` … `20261126003600`).
- [ ] Staging dựng mới: `supabase db push` (hoặc áp lần lượt theo tên file) → chạy toàn bộ `supabase/tests/*.sql` **trừ** `local_auth_shim.sql` bằng psql với quyền `postgres`. Mọi file rollback dữ liệu thử.
- [ ] Production: **backup trước** → áp các migration còn thiếu theo thứ tự (hiện biết thiếu tối thiểu `20261112003300`, `20261112003400`; kiểm tra cả `20261112003100` và `20261120003500`) → `supabase/tests/verification.sql` = 0 vi phạm.
- [ ] Không có bảng/hàm/grant được tạo tay qua Dashboard ngoài migration (Database → Tables/Functions, đối chiếu với repo).

## B. Auth
- [ ] Email provider bật; **Confirm email** bật.
- [ ] Email template *Reset Password* chứa mã: `<p>Your PawQuest code: <strong>{{ .Token }}</strong></p>` (app dùng OTP, không dùng link).
- [ ] SMTP tuỳ chỉnh cho production (giới hạn email mặc định của Supabase rất thấp) — ghi nhà cung cấp.
- [ ] Độ dài mật khẩu tối thiểu ≥ 8 (khớp kiểm tra trong app); bật kiểm tra mật khẩu bị lộ nếu gói hỗ trợ.
- [ ] Rate limits (đăng ký, OTP, email) ở mức mặc định hoặc chặt hơn; cân nhắc CAPTCHA.
- [ ] JWT expiry ≤ 3600 s; refresh token rotation bật.
- [ ] Không bật provider OAuth/anonymous sign‑in nào không dùng.

## C. Bảo mật DB
- [ ] Database → **Security Advisor**: không còn mục ERROR (RLS disabled, security definer view, …).
- [ ] Performance Advisor: xem các cảnh báo index.
- [ ] Network restrictions / SSL enforcement cho kết nối trực tiếp DB.
- [ ] Không chia sẻ service‑role key ngoài người vận hành; không dùng trong app.

## D. Backup & giám sát
- [ ] Xác nhận loại backup (Daily/PITR) và thời gian lưu; ghi vào `RUNBOOK.md` §1.
- [ ] Diễn tập khôi phục 1 lần sang project tạm (`RUNBOOK.md` §4.5).
- [ ] Bật cảnh báo sử dụng (CPU, disk, egress) và email thông báo của dự án.

## E. Smoke test sau khi áp (staging, rồi production bằng tài khoản thử)
- [ ] QA‑A01 đăng ký/đăng nhập, QA‑A02 reset mật khẩu bằng mã, QA‑A03 xoá tài khoản (kiểm tra `auth.users`, `auth.identities`, `auth.refresh_tokens`, tên pet).
- [ ] QA‑E01 Focus + claim, mua 1 thức ăn, QA‑E08 cổng chăm sóc pet trả `pet_needs_care`.
- [ ] Widget hiển thị streak/done (migration 034).
- [ ] Chạy `supabase/operations/disable_*.sql` rồi `enable_*.sql` trên **staging**, xác nhận app hiển thị “không khả dụng” và bật lại bình thường.
