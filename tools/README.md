# Công cụ phát triển

Chạy từ thư mục gốc. Python dùng thư viện chuẩn trừ khi script ghi rõ yêu cầu khác. Generator tự tìm root qua `settings.gradle.kts`. Browser/CDP test cần Node hỗ trợ WebSocket (Node 22+).

| Thư mục | Trách nhiệm |
|---|---|
| `assets/` | Bundles, portraits, icon/sound, catalog và GLB phát triển cũ |
| `balance/` | Constants/migration từ `config/*-balance.json` |
| `validation/` | Cấu trúc, bản dịch, GLB/rig |
| `testing/` | Smoke Android, API, browser và PostgreSQL |
| `testing/fixtures/` | Backend giả lập/probe cục bộ, không dùng production |

## Kiểm tra không cần thiết bị

```powershell
python tools/validation/check_project_structure.py
python tools/validation/audit_localization.py
python tools/assets/build_prototype_pet.py --check
python tools/assets/build_companions.py --check
python tools/assets/build_focus_companion.py --check
python tools/assets/import_decor_catalog.py --check
python tools/balance/generate_pet_care_balance.py --check
python tools/balance/generate_hydration.py --check
python tools/balance/generate_play_balance.py --check
python tools/assets/generate_launcher_icons.py --check
python tools/assets/generate_level_sound.py --check
```

Bỏ `--check` để tái tạo nếu script hỗ trợ. Generator balance/catalog có thể sinh migration hiện có: muốn đổi luật đã triển khai cần migration mới; không ghi đè migration đã phát hành.

`build_decor_assets.py` sinh registry, Compose artwork và trang thumbnail tạm. `render_decor_thumbnails.mjs` cần browser CDP riêng trên cổng 9228. `build_pet_portraits.py` cần Chrome headless. PNG được đóng gói sẵn nên Gradle build thông thường không cần chạy browser.

## Database và tích hợp

```powershell
python tools/testing/test_database.py --pg-bin "C:/Program Files/PostgreSQL/18/bin" --port 55439
```

Cần PostgreSQL cục bộ đang chạy trên loopback. Runner tạo/xóa database test ngẫu nhiên; không dùng database thật.

`testing/fixtures/mvp_sql_backend.py` tạo adapter cục bộ trên cổng 55447. Tham khảo [báo cáo](../docs/TEST_REPORT.md) trước khi chạy smoke Android. APK fixture phải dùng URL loopback và test-only key; các smoke test kiểm tra để tránh thao tác tài khoản thật. Kết quả tạm nằm trong `build/`.

Các test `.mjs` dùng browser hoặc debug WebView; đọc đầu từng file để biết cổng CDP. Không import hàng loạt fixture: một số file khởi động HTTP server khi chạy.
