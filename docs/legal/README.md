# Legal pages (Google Play)

| File | Dùng cho |
|---|---|
| `privacy-policy.html` | Play Console → App content → Privacy policy; link trong app (`PAWQUEST_PRIVACY_POLICY_URL`) |
| `account-deletion.html` | Data safety → Account deletion URL; `PAWQUEST_ACCOUNT_DELETION_URL` |

## Đã đăng (2026-11-26)
- Privacy policy: <https://thees-anh.github.io/TranTheAnh-IdeaMobileApp/privacy-policy.html>
- Xoá tài khoản: <https://thees-anh.github.io/TranTheAnh-IdeaMobileApp/account-deletion.html>

Nguồn hiển thị là nhánh `gh-pages` (GitHub Pages, HTTPS). Khi sửa nội dung: sửa file trong thư mục này, rồi chép sang nhánh `gh-pages` và push; Pages tự build lại trong ~1 phút.

## Quy tắc khi cập nhật
1. Thay mọi `{{...}}` (tên nhà phát triển đúng như trên Play, email liên hệ, region Supabase, ngày hiệu lực, URL) và xoá comment đầu file.
2. Đối chiếu lại nội dung với Data safety form (`docs/release-audit/GOOGLE_PLAY_CHECKLIST.md` §5). Nếu sau này thêm SDK crash/analytics, cập nhật cả hai.
3. Đăng ở nơi công khai, không cần đăng nhập, không chặn theo quốc gia, là trang HTML (không phải PDF). Ví dụ GitHub Pages: đưa 2 file vào repo/nhánh Pages, bật *Settings → Pages*.
4. Đặt hai URL `https://…` vào cấu hình build release (`docs/operations/RELEASE_PROCESS.md` §2) và vào Play Console.

Trang xoá tài khoản dẫn tới quy trình vận hành `supabase/operations/delete_account_by_email.sql` (`docs/operations/RUNBOOK.md` §4.6).
