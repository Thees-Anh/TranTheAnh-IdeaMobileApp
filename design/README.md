# Nguồn thiết kế

Đây là đầu vào gốc, không phải assets được app tải trực tiếp.

| Đầu vào | Generator |
|---|---|
| `prototypes/pets/mochi.html` | `tools/assets/build_prototype_pet.py` |
| `prototypes/pets/capybara.html`, `rabbit.html` | `tools/assets/build_companions.py` |
| `prototypes/focus/mochi-reading.html` | `tools/assets/build_focus_companion.py` |
| `assets/pets/puppy/puppy.svg`, `puppy.css` | `tools/assets/build_puppy.py` |
| `assets/decor/accessories.svg`, `furniture.svg` | `tools/assets/decor_stage.py`, `build_decor_assets.py` |
| `assets/decor/catalog.json`, `animations.css` | Catalog import, thumbnails, scene bundles |

HTML mẫu có thể chứa mã demo/CDN. Generator chỉ trích xuất hình/hoạt ảnh và đóng gói ngoại tuyến. Không sao chép logic demo thành gameplay; phần thưởng, inventory và trang bị tiếp tục do shared domain/backend quản lý.

Xem [cách sinh/check assets](../tools/README.md) và [quy tắc cấu trúc](../docs/PROJECT_STRUCTURE.md).
