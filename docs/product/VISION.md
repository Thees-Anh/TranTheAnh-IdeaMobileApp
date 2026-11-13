# PAWQUEST 3D – PROJECT DEVELOPMENT PLAN

**Tên dự án:** PawQuest 3D  
**Loại sản phẩm:** Mobile Application kết hợp Gamification & 3D Virtual Pet  
**Nền tảng:** Android (định hướng mở rộng iOS)  
**Công nghệ chính:** Kotlin Multiplatform (KMM/KMP), Compose Multiplatform, SceneView, Filament, Supabase  
**Thời gian phát triển:** 5 tuần (35 ngày)  
**Phiên bản mục tiêu:** MVP 1.0  
**Phong cách thiết kế:** 3D Cartoon, màu chủ đạo trắng và xanh da trời  
**Công cụ hỗ trợ phát triển:** Android Studio, Antigravity, Codex, Blender

---

# I. TỔNG QUAN DỰ ÁN

## 1. Giới thiệu

PawQuest 3D là ứng dụng di động kết hợp giữa quản lý nhiệm vụ cá nhân (Task Management), xây dựng thói quen (Habit Tracking) và trò chơi chăm sóc thú cưng ảo 3D (Virtual Pet Simulation).

Ứng dụng lấy cảm hứng từ cơ chế nuôi thú cưng của Talking Tom, kết hợp hệ thống nhiệm vụ và phần thưởng của các ứng dụng Gamification.

Người dùng hoàn thành các nhiệm vụ trong cuộc sống thực như học tập, tập thể dục, đọc sách hoặc làm việc để nhận Coins và Pet XP.

Các phần thưởng này được sử dụng để:

- Mua thức ăn và chăm sóc thú cưng.
- Mua trang phục, trang sức và phụ kiện.
- Trang trí và nâng cấp căn phòng.
- Tăng cấp độ thú cưng.
- Mở khóa các vật phẩm mới.

Mục tiêu là tạo ra một trải nghiệm vừa có giá trị thực tế, vừa mang tính giải trí và tương tác.

## 2. Vấn đề cần giải quyết

Các ứng dụng quản lý nhiệm vụ truyền thống thường chỉ cung cấp danh sách công việc, lịch trình và thông báo.

Một số người dùng gặp khó khăn trong việc duy trì động lực khi xây dựng thói quen lâu dài.

PawQuest giải quyết vấn đề này bằng cách chuyển đổi thành quả đời thực thành phần thưởng trong một thế giới game 3D.

Thay vì chỉ đánh dấu nhiệm vụ đã hoàn thành, người dùng sẽ nhìn thấy sự phát triển của thú cưng và không gian sống thông qua những hoạt động tích cực của mình.

## 3. Mục tiêu dự án

### Mục tiêu sản phẩm

- Xây dựng ứng dụng Android có giao diện hiện đại và trải nghiệm 3D.
- Cho phép người dùng quản lý nhiệm vụ cá nhân.
- Khuyến khích duy trì thói quen tích cực.
- Tích hợp hệ thống phần thưởng Coins và Pet XP.
- Cho phép chăm sóc và tương tác với thú cưng 3D.
- Hỗ trợ mua sắm và cá nhân hóa thú cưng.
- Cho phép trang trí căn phòng bằng các vật phẩm trong game.

### Mục tiêu kỹ thuật

- Sử dụng Kotlin Multiplatform để xây dựng kiến trúc ứng dụng.
- Sử dụng Compose Multiplatform để phát triển giao diện.
- Tích hợp SceneView và Filament để hiển thị nhân vật 3D trên Android.
- Sử dụng Supabase để quản lý authentication và dữ liệu.
- Xây dựng kiến trúc MVVM kết hợp Repository Pattern.
- Tổ chức mã nguồn theo hướng có thể mở rộng sang iOS.
- Hoàn thành bản APK chạy trên thiết bị Android thật.

## 4. Đối tượng người dùng

### Nhóm người dùng chính

- Học sinh, sinh viên muốn tạo động lực học tập.
- Người đi làm muốn quản lý công việc cá nhân.
- Người muốn xây dựng thói quen tích cực.
- Người yêu thích game nuôi thú cưng và game casual.

### Nhóm người dùng ưu tiên trong MVP

Học sinh, sinh viên và người trẻ có nhu cầu quản lý nhiệm vụ hằng ngày nhưng muốn trải nghiệm thú vị hơn so với ứng dụng To-do List truyền thống.

---

# II. CORE GAMEPLAY LOOP

## 1. Vòng lặp hoạt động chính

PawQuest hoạt động dựa trên vòng lặp:

**Create Quest → Complete Quest → Earn Rewards → Care for Pet → Customize Pet & Room → Unlock Progress → Repeat**

### Bước 1: Tạo nhiệm vụ

Người dùng tạo nhiệm vụ ngoài đời thực.

Ví dụ:

- Học tiếng Anh 30 phút.
- Tập thể dục 20 phút.
- Đọc sách 15 trang.
- Hoàn thành bài tập.
- Dọn dẹp phòng.

### Bước 2: Hoàn thành nhiệm vụ

Người dùng đánh dấu nhiệm vụ đã hoàn thành.

Hệ thống ghi nhận thời điểm hoàn thành và cập nhật tiến độ.

### Bước 3: Nhận phần thưởng

Người dùng nhận:

- Coins: tiền tệ trong game.
- Pet XP: điểm kinh nghiệm giúp thú cưng lên cấp.

### Bước 4: Chăm sóc thú cưng

Người dùng sử dụng tài nguyên để:

- Cho thú cưng ăn.
- Chơi đùa.
- Cho thú cưng nghỉ ngơi.

### Bước 5: Mua sắm và trang trí

Coins được sử dụng để mua:

- Thức ăn.
- Phụ kiện.
- Trang phục.
- Đồ nội thất.

### Bước 6: Phát triển thú cưng

Thú cưng tích lũy XP, tăng cấp và mở khóa vật phẩm mới.

Vòng lặp tiếp tục khi người dùng quay lại hoàn thành các nhiệm vụ tiếp theo.

## 2. Ví dụ User Journey

Một sinh viên tạo nhiệm vụ "Học tiếng Anh 30 phút".

Sau khi hoàn thành:

1. Hệ thống cộng 50 Coins.
2. Thú cưng nhận 25 Pet XP.
3. Người dùng mở Pet Home.
4. Mua thức ăn với giá 20 Coins.
5. Cho thú cưng ăn và tăng chỉ số Hunger.
6. Tiết kiệm Coins để mua một chiếc mũ mới.
7. Tiếp tục hoàn thành nhiệm vụ vào ngày hôm sau.

---

# III. PHÂN TÍCH CHỨC NĂNG

## 1. Authentication System

### Chức năng

- Đăng ký tài khoản.
- Đăng nhập.
- Đăng xuất.
- Duy trì phiên đăng nhập.
- Lưu thông tin hồ sơ.

### Công nghệ

- Supabase Auth.
- Kotlin Multiplatform.
- Ktor hoặc Supabase Kotlin SDK.

## 2. Quest Management System

### Chức năng

- Tạo nhiệm vụ mới.
- Chỉnh sửa nhiệm vụ.
- Xóa nhiệm vụ.
- Đánh dấu nhiệm vụ hoàn thành.
- Phân loại nhiệm vụ.
- Thiết lập độ khó.
- Thiết lập nhiệm vụ lặp lại hằng ngày.
- Xem lịch sử hoàn thành.

### Thuộc tính nhiệm vụ

- Quest ID.
- User ID.
- Title.
- Description.
- Category.
- Difficulty.
- Repeat Type.
- Created At.
- Due Date.
- Completion Status.

### Hệ thống phần thưởng đề xuất

| Difficulty | Coins | Pet XP |
|---|---:|---:|
| Easy | 20 | 10 |
| Medium | 50 | 25 |
| Hard | 100 | 50 |

Phần thưởng do backend xác định và chỉ được cấp một lần cho mỗi lượt hoàn thành hợp lệ.

Đối với nhiệm vụ lặp lại, hệ thống cần lưu từng lần hoàn thành theo ngày thay vì chỉ sử dụng một trạng thái hoàn thành duy nhất.

## 3. Pet Management System

### Chức năng

- Khởi tạo thú cưng khi tạo tài khoản.
- Đặt tên thú cưng.
- Hiển thị thú cưng 3D.
- Quản lý cấp độ và XP.
- Quản lý trạng thái thú cưng.
- Lưu thông tin thú cưng.

### Thuộc tính thú cưng

- Pet ID.
- User ID.
- Pet Name.
- Pet Type.
- Level.
- Current XP.
- Hunger.
- Happiness.
- Energy.
- Current Animation.
- Created At.
- Last Updated At.

### Chỉ số thú cưng

**Hunger:** Mức độ no của thú cưng, từ 0 đến 100.

**Happiness:** Mức độ vui vẻ, từ 0 đến 100.

**Energy:** Năng lượng, từ 0 đến 100.

Các chỉ số thay đổi theo thời gian và hành động của người dùng.

Trong MVP, thú cưng không chết hoặc bị mất vĩnh viễn khi người dùng không đăng nhập.

## 4. Pet Interaction System

### Các tương tác chính

**Feed**

- Người dùng chọn thức ăn.
- Hệ thống kiểm tra số lượng trong Inventory.
- Trừ một đơn vị thức ăn.
- Tăng chỉ số Hunger.
- Phát animation Eating.

**Play**

- Người dùng chọn hành động chơi đùa.
- Tăng Happiness.
- Giảm một lượng Energy.
- Phát animation Playing.

**Sleep**

- Người dùng cho thú cưng ngủ.
- Chuyển trạng thái sang Sleeping.
- Phục hồi Energy theo thời gian.
- Phát animation Sleeping.

**Pet Touch**

- Người dùng chạm vào thú cưng.
- Thú cưng phản ứng bằng animation Happy.

## 5. 3D Pet Rendering System

### Công nghệ

- SceneView.
- Google Filament.
- GLB/glTF.
- Blender.

### Yêu cầu

- Load được model mèo 3D.
- Hiển thị model trong Pet Home.
- Phát animation theo trạng thái.
- Hỗ trợ tương tác chạm.
- Hiển thị ánh sáng và camera.
- Hỗ trợ thay đổi phụ kiện.
- Tối ưu hiệu năng Android.

### Animation MVP

1. Idle.
2. Happy.
3. Eating.
4. Sleeping.
5. Playing.

### Giới hạn MVP

- Một loại thú cưng: mèo.
- Một model chính.
- Một căn phòng.
- Không có hệ thống vật lý phức tạp.
- Không có di chuyển tự do trong thế giới mở.

## 6. Pet Customization System

### Chức năng

- Mua phụ kiện.
- Lưu phụ kiện trong Inventory.
- Trang bị phụ kiện.
- Tháo phụ kiện.
- Hiển thị phụ kiện trên model 3D.

### Phụ kiện MVP

- Hat.
- Glasses.
- Collar.

### Yêu cầu kỹ thuật

Phụ kiện được gắn vào các vị trí phù hợp trên model thú cưng.

Hệ thống cần lưu trạng thái trang bị để khôi phục khi người dùng mở lại ứng dụng.

## 7. Home Decoration System

### Chức năng

- Mua nội thất.
- Lưu nội thất đã sở hữu.
- Thay đổi nội thất trong phòng.
- Lưu trạng thái trang trí.

### Nội thất MVP

- Pet Bed.
- Floor Lamp.
- Carpet.

### Cơ chế

Mỗi loại nội thất có một vị trí cố định.

Khi người dùng chọn nội thất mới, hệ thống thay thế model 3D tại vị trí tương ứng.

Không triển khai hệ thống kéo thả tự do trong MVP.

## 8. Shop & Inventory System

### Chức năng Shop

- Hiển thị danh sách vật phẩm.
- Phân loại vật phẩm.
- Hiển thị giá.
- Mua vật phẩm.
- Kiểm tra số Coins.
- Trừ Coins khi giao dịch thành công.

### Chức năng Inventory

- Hiển thị vật phẩm sở hữu.
- Hiển thị số lượng vật phẩm.
- Sử dụng thức ăn.
- Trang bị phụ kiện.
- Lựa chọn nội thất.

### Giá vật phẩm tham khảo

| Item | Price |
|---|---:|
| Pet Food | 20 |
| Blue Hat | 80 |
| Sunglasses | 100 |
| Collar | 120 |
| Pet Bed | 300 |
| Floor Lamp | 200 |
| Carpet | 150 |

## 9. Progress Dashboard

### Chức năng

- Tổng số nhiệm vụ hoàn thành.
- Số nhiệm vụ hoàn thành trong ngày.
- Thống kê theo tuần.
- Tỷ lệ hoàn thành.
- Daily Streak.
- Pet Level.
- Tổng Coins đã kiếm được.

---

# IV. THIẾT KẾ GIAO DIỆN

## 1. Phong cách UI/UX

- Modern Mobile UI.
- 3D Cartoon.
- Clean Design.
- Soft Rounded Corners.
- Pastel Colors.
- Friendly Animations.

### Màu sắc

- Primary: Sky Blue (#38BDF8).
- Background: White (#FFFFFF).
- Secondary Background: Light Blue (#EFF6FF).
- Accent: Yellow (#FACC15).
- Success: Green (#22C55E).
- Text: Dark Slate (#0F172A).

## 2. Các màn hình chính

### Authentication

- Login.
- Register.

### Pet Home

- Pet 3D.
- Hunger Bar.
- Happiness Bar.
- Energy Bar.
- Coins Balance.
- Feed Button.
- Play Button.
- Sleep Button.

### Quest

- Quest List.
- Create Quest.
- Edit Quest.
- Quest Detail.
- Quest History.

### Shop

- Food Shop.
- Accessories Shop.
- Furniture Shop.

### Inventory

- Food Inventory.
- Accessories Inventory.
- Furniture Inventory.

### Profile

- User Information.
- Pet Information.
- Statistics.
- Settings.

## 3. Bottom Navigation

Ứng dụng có 5 tab:

1. Home.
2. Quests.
3. Shop.
4. Inventory.
5. Profile.

---

# V. TECHNOLOGY STACK

| Layer | Technology |
|---|---|
| Programming Language | Kotlin |
| Mobile Framework | Kotlin Multiplatform |
| UI | Compose Multiplatform |
| Android 3D Rendering | SceneView + Filament |
| 3D Modeling | Blender |
| 3D Asset Format | GLB/glTF |
| Architecture | MVVM + Repository |
| Dependency Injection | Koin |
| Networking | Ktor Client |
| Backend | Supabase |
| Database | PostgreSQL |
| Authentication | Supabase Auth |
| Local Storage | DataStore / SQLDelight |
| Development IDE | Android Studio |
| AI Development Tools | Antigravity + Codex |

Lưu ý: SceneView là thư viện rendering 3D dành cho Android, không phải game engine hoàn chỉnh. Phần điều khiển trạng thái, animation và gameplay cần được xây dựng bằng Kotlin.

---

# VI. SYSTEM ARCHITECTURE

## 1. Tổng quan kiến trúc

```text
PawQuest Android Application
            |
            v
Compose Multiplatform UI
            |
            v
ViewModel / State Management
            |
      +-----+-----+
      |           |
      v           v
Quest Module   Pet Module
      |           |
      v           v
Reward Logic   Pet State Logic
      |           |
      +-----+-----+
            |
            v
Shared KMP Domain Layer
            |
            v
Repository Layer
            |
            v
Ktor / Supabase API
            |
            v
Supabase Backend
            |
            v
PostgreSQL Database
```

3D Rendering được triển khai trong Android-specific module và nhận trạng thái từ Pet ViewModel.

## 2. Cấu trúc dự án đề xuất

```text
PawQuest/
|
+-- composeApp/
|   |
|   +-- src/
|       |
|       +-- commonMain/
|       |   |
|       |   +-- ui/
|       |   |   +-- home/
|       |   |   +-- quests/
|       |   |   +-- shop/
|       |   |   +-- inventory/
|       |   |   +-- profile/
|       |   |
|       |   +-- navigation/
|       |   +-- theme/
|       |   +-- components/
|       |
|       +-- androidMain/
|       |   |
|       |   +-- pet3d/
|       |       +-- PetScene.kt
|       |       +-- PetAnimator.kt
|       |       +-- PetInteraction.kt
|       |       +-- RoomRenderer.kt
|       |
|       +-- iosMain/
|
+-- shared/
|   |
|   +-- src/commonMain/
|       |
|       +-- domain/
|       |   +-- model/
|       |   +-- usecase/
|       |
|       +-- data/
|       |   +-- repository/
|       |   +-- remote/
|       |   +-- local/
|       |
|       +-- presentation/
|           +-- viewmodel/
|
+-- backend/
|   +-- supabase/
|       +-- migrations/
|       +-- functions/
|
+-- assets/
|   +-- models/
|   +-- animations/
|   +-- textures/
|
+-- docs/
```

Cấu trúc có thể điều chỉnh theo template KMP thực tế. Không bắt buộc phải tạo cả `shared` và `composeApp` nếu điều đó gây trùng lặp module.

---

# VII. DATABASE DESIGN

## 1. Profiles

Lưu thông tin người dùng.

Các trường chính:

- id.
- username.
- avatar_url.
- created_at.

## 2. Pets

Lưu thông tin thú cưng.

- id.
- user_id.
- name.
- type.
- level.
- current_xp.
- hunger.
- happiness.
- energy.
- last_updated_at.

## 3. Quests

Lưu định nghĩa nhiệm vụ.

- id.
- user_id.
- title.
- description.
- category.
- difficulty.
- repeat_type.
- created_at.
- due_date.

## 4. Quest Completions

Lưu từng lần hoàn thành nhiệm vụ.

- id.
- quest_id.
- user_id.
- completion_date.
- completed_at.
- coins_earned.
- xp_earned.

## 5. Wallets

- user_id.
- coins_balance.
- updated_at.

## 6. Wallet Transactions

- id.
- user_id.
- transaction_type.
- amount.
- reference_id.
- created_at.

## 7. Items

- id.
- name.
- category.
- price.
- asset_path.
- created_at.

## 8. User Items

- id.
- user_id.
- item_id.
- quantity.
- acquired_at.

## 9. Pet Equipment

- pet_id.
- equipment_slot.
- item_id.

## 10. Room Furniture

- user_id.
- furniture_slot.
- item_id.

### Database Requirements

- Sử dụng UUID cho các định danh chính.
- Thiết lập foreign keys.
- Bật Row Level Security cho dữ liệu người dùng.
- Xử lý giao dịch mua hàng và nhận thưởng bằng transaction.
- Ngăn nhận thưởng trùng lặp.
- Không cho phép client tự thay đổi số Coins.
- Có cơ chế xử lý cập nhật trạng thái thú cưng theo thời gian.

---

# VIII. KẾ HOẠCH PHÁT TRIỂN 5 TUẦN

## WEEK 1 – PROJECT SETUP & 3D PROTOTYPE

### Mục tiêu

Khởi tạo dự án và xác minh khả năng hiển thị thú cưng 3D trên Android.

### Công việc

- [ ] Cài đặt Android Studio và Android SDK.
- [ ] Khởi tạo Kotlin Multiplatform Project.
- [ ] Thiết lập Compose Multiplatform.
- [ ] Cấu hình project architecture.
- [ ] Tích hợp SceneView/Filament.
- [ ] Chuẩn bị model mèo 3D dạng GLB.
- [ ] Load model trong ứng dụng.
- [ ] Thiết lập camera và ánh sáng.
- [ ] Phát animation Idle.
- [ ] Thử chuyển sang animation Eating.
- [ ] Thiết kế UI/UX tổng thể.

### Deliverables

- Project KMP chạy trên Android.
- Hiển thị được pet 3D.
- Animation hoạt động.
- Có bản prototype Pet Home.

### Tiêu chí hoàn thành

Model 3D hiển thị và phát animation ổn định trên thiết bị Android thử nghiệm.

---

## WEEK 2 – AUTHENTICATION & QUEST SYSTEM

### Mục tiêu

Xây dựng chức năng quản lý nhiệm vụ và hệ thống phần thưởng.

### Công việc

- [ ] Thiết lập Supabase Project.
- [ ] Thiết kế database.
- [ ] Xây dựng Authentication.
- [ ] Xây dựng Login/Register UI.
- [ ] Tạo Quest Model.
- [ ] Tạo Quest Repository.
- [ ] Xây dựng Create Quest.
- [ ] Xây dựng Edit Quest.
- [ ] Xây dựng Delete Quest.
- [ ] Xây dựng Complete Quest.
- [ ] Xử lý nhiệm vụ lặp lại.
- [ ] Tích hợp Coins và Pet XP.
- [ ] Kiểm thử logic phần thưởng.

### Deliverables

- Đăng nhập hoạt động.
- CRUD nhiệm vụ hoạt động.
- Hoàn thành nhiệm vụ nhận Coins và XP.
- Dữ liệu được lưu vào Supabase.

---

## WEEK 3 – PET CARE SYSTEM

### Mục tiêu

Hoàn thiện hệ thống chăm sóc và tương tác với thú cưng 3D.

### Công việc

- [ ] Xây dựng Pet Model.
- [ ] Xây dựng Pet Repository.
- [ ] Xây dựng Pet ViewModel.
- [ ] Quản lý Hunger.
- [ ] Quản lý Happiness.
- [ ] Quản lý Energy.
- [ ] Xây dựng Feed Action.
- [ ] Xây dựng Play Action.
- [ ] Xây dựng Sleep Action.
- [ ] Tích hợp các animation tương ứng.
- [ ] Đồng bộ Pet State với backend.
- [ ] Xây dựng Pet Level System.

### Deliverables

- Người dùng có thể cho pet ăn, chơi và ngủ.
- Các chỉ số pet thay đổi chính xác.
- Animation phản ánh trạng thái.
- Trạng thái pet được lưu và khôi phục.

---

## WEEK 4 – SHOP, INVENTORY & CUSTOMIZATION

### Mục tiêu

Xây dựng hệ thống mua sắm và cá nhân hóa thú cưng.

### Công việc

- [ ] Xây dựng Shop UI.
- [ ] Xây dựng Item Catalog.
- [ ] Xây dựng Purchase Logic.
- [ ] Xây dựng Inventory.
- [ ] Xây dựng Food Inventory.
- [ ] Xây dựng Accessory Inventory.
- [ ] Xây dựng Furniture Inventory.
- [ ] Tích hợp phụ kiện 3D.
- [ ] Trang bị và tháo phụ kiện.
- [ ] Thay đổi giường, đèn và thảm.
- [ ] Lưu trạng thái customization.
- [ ] Kiểm thử giao dịch Coins.

### Deliverables

- Người dùng có thể mua vật phẩm.
- Inventory hoạt động.
- Có thể trang bị phụ kiện.
- Có thể thay đổi nội thất.
- Trạng thái được lưu vào database.

---

## WEEK 5 – TESTING, OPTIMIZATION & RELEASE

### Mục tiêu

Hoàn thiện ứng dụng và chuẩn bị bản demo.

### Công việc

- [ ] Hoàn thiện Dashboard.
- [ ] Kiểm thử Authentication.
- [ ] Kiểm thử Quest System.
- [ ] Kiểm thử Rewards.
- [ ] Kiểm thử Pet Care.
- [ ] Kiểm thử Shop.
- [ ] Kiểm thử Inventory.
- [ ] Kiểm thử Pet Customization.
- [ ] Kiểm thử Room Decoration.
- [ ] Tối ưu hiệu năng 3D.
- [ ] Kiểm tra memory usage.
- [ ] Kiểm thử trên Android thật.
- [ ] Sửa các lỗi nghiêm trọng.
- [ ] Xuất APK.
- [ ] Chuẩn bị tài liệu và video demo.

### Deliverables

- PawQuest MVP 1.0.
- APK chạy trên Android.
- Source code hoàn chỉnh.
- Database schema.
- Tài liệu kỹ thuật.
- Video demo sản phẩm.

---

# IX. PHÂN CHIA CÔNG VIỆC

## 1. Antigravity

Phụ trách:

- Thiết kế UI/UX.
- Thiết kế Compose screens.
- Tạo reusable components.
- Thiết kế theme.
- Xây dựng giao diện Quest.
- Xây dựng Shop UI.
- Xây dựng Inventory UI.
- Xây dựng Dashboard.
- UI polish.

## 2. Codex

Phụ trách:

- Thiết lập KMP architecture.
- Domain models.
- Business logic.
- Repository layer.
- Supabase integration.
- Quest and Reward System.
- Pet State Management.
- SceneView integration.
- Animation control.
- Inventory and Purchase Logic.
- Unit tests.
- Debugging.

## 3. Blender

Phụ trách:

- Kiểm tra model 3D.
- Chỉnh sửa model.
- Rigging khi cần thiết.
- Animation.
- Chuẩn bị phụ kiện.
- Chuẩn bị nội thất.
- Export GLB/glTF.

## 4. Android Studio

Phụ trách:

- Gradle configuration.
- Android emulator.
- Debugging.
- Performance profiling.
- Build APK/AAB.

---

# X. RỦI RO VÀ GIẢI PHÁP

| Risk | Impact | Mitigation |
|---|---|---|
| Model GLB không tương thích | High | Test model trong tuần 1 |
| Animation không hoạt động | High | Kiểm tra rig và animation clips |
| Phụ kiện không khớp model | High | Sử dụng model và skeleton đồng bộ |
| SceneView khó mở rộng | High | Xây dựng 3D prototype trước |
| Hiệu năng Android thấp | High | Giảm polygon và texture |
| Lỗi đồng bộ Coins | High | Backend transaction |
| Scope quá lớn | High | Giới hạn MVP |
| Thiếu thời gian testing | Medium | Kiểm thử theo từng tuần |

### Kế hoạch dự phòng

Nếu hệ thống 3D gặp khó khăn:

- Giảm số lượng animation.
- Giảm số lượng phụ kiện.
- Sử dụng vị trí nội thất cố định.
- Loại bỏ các hiệu ứng không cần thiết.
- Ưu tiên Pet Home, Quest và Pet Care.

Nếu không hoàn thành được customization trong thời gian dự kiến, vẫn phải đảm bảo Quest System, Rewards và Pet Care hoạt động ổn định.

---

# XI. MVP SCOPE

## Must Have

- [ ] Authentication.
- [ ] Quest CRUD.
- [ ] Daily Quest.
- [ ] Quest Completion.
- [ ] Coins & Pet XP.
- [ ] One 3D Cat.
- [ ] Pet Home.
- [ ] Pet State.
- [ ] Feed / Play / Sleep.
- [ ] Basic Animation.
- [ ] Shop.
- [ ] Inventory.
- [ ] Basic Accessories.
- [ ] Basic Room Decoration.
- [ ] Data Persistence.
- [ ] Android APK.

## Should Have

- [ ] Daily Streak.
- [ ] Progress Dashboard.
- [ ] Pet Level Unlocks.
- [ ] Notifications.
- [ ] Additional Animations.

## Future Features

- [ ] Multiple Pet Species.
- [ ] Pet Evolution.
- [ ] Mini-games.
- [ ] AI Pet Chat.
- [ ] Multiplayer.
- [ ] Friend System.
- [ ] Visit Friends' Homes.
- [ ] Advanced Room Customization.
- [ ] iOS Support.

---

# XII. SUCCESS METRICS

Các chỉ số dưới đây là mục tiêu nghiệm thu kỹ thuật đề xuất cho MVP.

| Metric | Target |
|---|---|
| Quest CRUD | Hoạt động chính xác |
| Quest Reward | Không nhận thưởng trùng |
| Pet Animation | Tối thiểu 5 trạng thái |
| Pet Interaction | Feed, Play, Sleep |
| Accessories | Tối thiểu 3 loại |
| Furniture | Tối thiểu 3 loại |
| Data Persistence | Lưu và khôi phục thành công |
| Android Performance | Mục tiêu 30 FPS ổn định trên thiết bị thử nghiệm |
| Critical Bugs | Không còn lỗi nghiêm trọng đã biết |
| Final Build | APK cài đặt và demo được |

---

# XIII. ĐỊNH HƯỚNG PHÁT TRIỂN SAU MVP

## Phase 2 – Advanced Pet System

- Thêm nhiều giống thú cưng.
- Hệ thống tiến hóa.
- Nhiều trạng thái cảm xúc.
- Nâng cấp animation.
- Thêm nhiều phụ kiện và trang phục.

## Phase 3 – Mini-games & Social Features

- Mini-games.
- Hệ thống bạn bè.
- Thăm nhà bạn bè.
- Chia sẻ thành tích.
- Sự kiện theo mùa.

## Phase 4 – AI Pet Companion

- Tích hợp AI Chat.
- Pet phản hồi bằng hội thoại.
- AI gợi ý nhiệm vụ.
- Cá nhân hóa trải nghiệm.
- Phân tích tiến độ thói quen.

---

# XIV. KẾT LUẬN

PawQuest 3D là ứng dụng kết hợp giữa quản lý nhiệm vụ đời thực và game chăm sóc thú cưng ảo 3D.

Giá trị cốt lõi của sản phẩm là chuyển đổi những hoạt động tích cực trong cuộc sống thành phần thưởng trực quan, giúp người dùng có thêm động lực duy trì thói quen.

Trong giai đoạn MVP 5 tuần, dự án tập trung vào:

1. Một thú cưng mèo 3D.
2. Một căn phòng 3D.
3. Hệ thống nhiệm vụ cá nhân.
4. Coins và Pet XP.
5. Chăm sóc thú cưng.
6. Shop và Inventory.
7. Phụ kiện và trang trí cơ bản.
8. Ứng dụng Android có thể chạy và demo.

**Ưu tiên phát triển quan trọng nhất là hoàn thành 3D Proof of Concept trong tuần đầu tiên.**

Sau khi xác minh thành công khả năng hiển thị model, animation và tương tác 3D bằng SceneView/Filament, dự án sẽ tiếp tục phát triển các chức năng quản lý nhiệm vụ, phần thưởng và chăm sóc thú cưng theo kế hoạch.