# UI UX Pro Max trong PawQuest

Đã cài skill cục bộ tại `.agents/skills/ui-ux-pro-max/` cho dự án này.
Nguồn: [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill).
Upstream sử dụng `.agents/skills` cho Codex và Antigravity.

## Sử dụng

Ở lượt trò chuyện tiếp theo, có thể yêu cầu:

> Dùng ui-ux-pro-max để review giao diện Pet Home của PawQuest. Tuân thủ
> docs/AI_INSTRUCTIONS.md, Compose Multiplatform và portrait-only.

Nếu công cụ AI chưa nhận skill mới, mở phiên trò chuyện mới trong dự án.
Skill hỗ trợ AI tìm hướng dẫn thiết kế; cài skill không tự thay đổi giao diện app.

Chạy tìm kiếm thủ công từ thư mục gốc PawQuest:

```powershell
python .agents/skills/ui-ux-pro-max/scripts/search.py "mobile touch target accessibility" --domain ux
python .agents/skills/ui-ux-pro-max/scripts/search.py "portrait safe area navigation" --stack jetpack-compose
```

Python đã có sẵn trên máy. Công cụ tìm kiếm sử dụng dữ liệu cục bộ và thư viện
chuẩn Python, không cần thêm thư viện vào Gradle hoặc cài package Python.

Các gợi ý phải được đối chiếu với quy định và design tokens hiện có của PawQuest.
Dùng hướng dẫn Android/Jetpack Compose phù hợp, không tự chuyển ứng dụng sang
web stack hoặc áp dụng kích thước CSS vào layout Compose.

## Thông tin cài đặt

- Ngày: 2026-11-04.
- Dùng helper `skill-installer` với nguồn `.claude/skills/ui-ux-pro-max` trên
  nhánh `main`, đích là `.agents/skills` trong PawQuest.
- Cài đầy đủ SKILL.md, scripts, data và references; giữ giấy phép MIT upstream.
- Đổi đường dẫn mẫu dành riêng cho Claude Plugin sang đường dẫn cục bộ dự án.
- Không cài CLI toàn máy và không sửa source Kotlin/Gradle.
- Đã chạy thành công truy vấn UX và stack Jetpack Compose.
- Validator upstream xác nhận 12 tệp domain, 22 tệp stack và
  `ui-reasoning.csv` hợp lệ.

Đọc [SKILL.md](../../.agents/skills/ui-ux-pro-max/SKILL.md) để xem hướng dẫn đầy đủ.
