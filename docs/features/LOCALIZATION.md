# Ngôn ngữ ứng dụng

PawQuest hỗ trợ **Tiếng Việt** và **English**. Đổi tại **Hồ sơ → Cài đặt → Ngôn ngữ** (Profile → Settings → Language). Màn hình đăng nhập cũng có lựa chọn này. Dấu ✓ cho biết ngôn ngữ đang chọn; tên “Tiếng Việt” và “English” luôn giữ nguyên để dễ nhận biết.

Lựa chọn được lưu trên thiết bị, dùng chung khi đăng nhập/đăng xuất và được khôi phục sau khi khởi động lại. Lần đầu dùng tiếng Việt nếu ngôn ngữ thiết bị là `vi`; các ngôn ngữ khác dùng tiếng Anh. Không cần migration Supabase.

## Phạm vi

- Đăng nhập, đăng ký, lỗi xác thực và lỗi kết nối.
- Nhà thú cưng, chăm sóc, lời thoại theo giờ và trạng thái, bộ sưu tập thú cưng.
- Danh sách/tạo/sửa/chi tiết nhiệm vụ, tập trung, nhận thưởng, lên cấp.
- Shop, Kho đồ, tên/mô tả vật phẩm hệ thống, nhãn trang bị và nội thất.
- Hồ sơ, lịch thói quen, thống kê, nhắc việc và cài đặt.
- Nội dung widget và thông báo, tên kênh thông báo, nhãn hỗ trợ tiếp cận.

Tên/mô tả nhiệm vụ tự nhập, tên người dùng và tên riêng thú cưng được giữ nguyên; không tự dịch hoặc ghi đè dữ liệu. PawQuest, Mochi, Capybara, mã định danh và UTC vẫn là tên/ký hiệu riêng. Hộp thoại/quyền và màn hình cài đặt do Android hoặc bàn phím bên ngoài quản lý có thể dùng ngôn ngữ của hệ điều hành.

## Kiến trúc

`shared/.../localization/Translations.kt` chứa cặp nội dung tiếng Anh/Việt. `tr()` áp dụng ngôn ngữ quan sát được của `AppLocale` ở ranh giới hiển thị; `translate(text, language)` là hàm thuần dùng cho test và các dịch vụ nền. Mẫu `{0}`, `{1}` giữ nguyên đối số như tên, số lượng và ngày tháng. Các mẫu cụ thể được ưu tiên trước mẫu tổng quát. Dữ liệu do người dùng nhập bỏ qua bộ dịch.

`LanguagePreferences` ở Android lưu mã `vi`/`en` trong SharedPreferences riêng. Activity dùng configuration theo mã này và được tạo lại khi đổi ngôn ngữ để bộ chọn ngày/giờ của Compose cũng cập nhật. Việc tạo lại dùng lifecycle/cleanup hiện có của scene, không thay đổi dữ liệu máy chủ.

Widget đọc ngôn ngữ đã lưu ở mỗi lần render, kể cả khi không có Activity. Notification coordinator dùng cùng mã khi tạo nội dung/kênh. Khi đổi ngôn ngữ, thông báo cũ được dọn khỏi khay để tránh trộn ngôn ngữ; không đặt lại quyền đồng ý hoặc trạng thái chống gửi lặp. Widget đang có được vẽ lại ngay, không cần mạng chỉ để đổi chữ.

Không thay đổi giá, phần thưởng, enum/RPC, khóa dữ liệu hoặc luật gameplay. Database tiếp tục lưu danh mục chuẩn; tên và mô tả của các vật phẩm hệ thống hiện có được dịch ở UI. Khi thêm vật phẩm hoặc câu hệ thống mới, thêm đủ cặp dịch vào catalog và chạy kiểm tra.

## Kiểm tra

```powershell
python tools/validation/audit_localization.py
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
.\gradlew.bat :shared:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.pawquest.LanguageDeviceTest
```

`LocalizationTest` kiểm tra khôi phục lựa chọn, đối số, hai ngôn ngữ của lời thoại/chăm sóc/lỗi xác thực và bộ placeholder. `LanguageDeviceTest` chạy trên ICU của Android. `tools/testing/test_language_android.py` kiểm tra giao diện bằng tài khoản fixture và PostgreSQL cục bộ; script từ chối APK dùng backend thật. Kết quả thực thi và các bước còn cần kiểm tra thủ công được ghi trong TEST_REPORT.md.
