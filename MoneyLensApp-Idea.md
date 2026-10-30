# MONEYLENS
### AI Personal Finance Assistant

**Tagline:** *See where your money goes.*

---

## 1. Tổng quan sản phẩm

**MoneyLens** là ứng dụng quản lý tài chính cá nhân từ việc chụp hóa đơn AI có thể giúp người dùng ghi nhận, phân loại và phân tích chi tiêu một cách tự động.

Khác với các ứng dụng quản lý tài chính truyền thống yêu cầu người dùng thường xuyên nhập thủ công:

- Số tiền
- Danh mục
- Nội dung giao dịch
- Cửa hàng
- Ngày giao dịch
và điều này đôi khi phức tạp hơn với những người lớn tuổi ít sử dụng điện thoại
MoneyLens hướng tới trải nghiệm đơn giản hơn:

**Chụp hóa đơn → AI hiểu giao dịch → người dùng xác nhận → chi tiêu được ghi nhận.**

Mục tiêu dài hạn của MoneyLens không chỉ là một **Expense Tracker**, mà trở thành một **AI Personal Finance Assistant** có khả năng trả lời:

> "Tiền của tôi đang đi đâu?"

và quan trọng hơn:

> "Tôi có thể quản lý tiền tốt hơn như thế nào?"

---

# 2. Vấn đề người dùng

Các ứng dụng quản lý tài chính cá nhân thường gặp một vấn đề lớn:

### Việc ghi chép chi tiêu quá thủ công.

Ví dụ, sau khi mua cà phê, người dùng phải:

**Bước 1:** Mở ứng dụng.

**Bước 2:** Chọn thêm giao dịch.

**Bước 3:** Nhập 85.000đ.

**Bước 4:** Chọn "Ăn uống".

**Bước 5:** Chọn "Coffee".

**Bước 6:** Nhập tên cửa hàng.

**Bước 7:** Chọn ngày.

**Bước 8:** Lưu.

Một giao dịch chỉ mất khoảng vài chục giây để nhập, nhưng người dùng phải lặp lại hành động này nhiều lần mỗi ngày.

Kết quả là:

> Người dùng muốn quản lý tài chính nhưng không duy trì được thói quen ghi chép.

MoneyLens tập trung giải quyết chính vấn đề này.

---

# 3. Giải pháp của MoneyLens

MoneyLens sử dụng **AI Vision + OCR + AI Classification** để giảm tối đa thao tác nhập liệu.

Thay vì:

**Mua hàng → nhớ giao dịch → mở app → nhập dữ liệu → phân loại**

MoneyLens hướng tới:

**Mua hàng → chụp hóa đơn → xác nhận**

Ví dụ người dùng chụp:

**HIGHLANDS COFFEE**

Phin Sữa Đá — 45.000đ  
Croissant — 35.000đ

**Total: 80.000đ**

MoneyLens tự động nhận diện:

**Merchant:** Highlands Coffee  
**Date:** 30/10/2026  
**Total:** 80.000đ  
**Category:** Food & Drink  
**Subcategory:** Coffee

Sau đó hiển thị kết quả cho người dùng:

**AI detected:**

Highlands Coffee  
80.000đ  
☕ Food & Drink → Coffee

**[Edit] [Confirm]**

Người dùng chỉ cần xác nhận.

---

# 4. Tính năng nổi bật nhất – AI Receipt Intelligence

Đây là tính năng trung tâm của MoneyLens.

Tuy nhiên, MoneyLens không nên chỉ đọc tổng số tiền trên hóa đơn.

Mục tiêu dài hạn là hiểu **từng sản phẩm bên trong hóa đơn**.

Ví dụ:

### Hóa đơn siêu thị

VinMart

Milk — 38.000đ  
Chicken — 120.000đ  
Shampoo — 95.000đ  
Toothpaste — 45.000đ

Total — 298.000đ

Một expense tracker thông thường có thể chỉ lưu:

**VinMart — 298.000đ — Shopping**

MoneyLens có thể hiểu:

**Groceries**
- Milk: 38.000đ
- Chicken: 120.000đ

**Personal Care**
- Shampoo: 95.000đ
- Toothpaste: 45.000đ

Nhờ vậy, MoneyLens không chỉ biết:

> "Bạn đã chi 298.000đ ở VinMart."

mà có thể biết:

> "158.000đ được chi cho thực phẩm và 140.000đ cho sản phẩm chăm sóc cá nhân."

Đây là dữ liệu có giá trị hơn rất nhiều cho việc phân tích tài chính cá nhân.

---

# 5. Điểm khác biệt với app tài chính thông thường

## Traditional Finance App

Thông thường:

**User → nhập transaction → category → database → chart**

Ứng dụng chủ yếu đóng vai trò **ghi chép và hiển thị dữ liệu**.

Người dùng vẫn phải tự hiểu biểu đồ và tự đưa ra kết luận.

---

## MoneyLens

MoneyLens hướng tới:

**Receipt / Screenshot / Manual Entry**

↓

**AI Extraction**

↓

**Item-level Understanding**

↓

**Automatic Categorization**

↓

**Financial Database**

↓

**AI Analysis**

↓

**Personalized Financial Insights**

Ứng dụng không chỉ lưu dữ liệu.

Ứng dụng **hiểu dữ liệu**.

---

# 6. AI Financial Insights

Đây sẽ là lớp khác biệt quan trọng hơn cả Receipt Scanner.

Sau khi người dùng sử dụng MoneyLens một thời gian, AI có thể phân tích hành vi chi tiêu.

Ví dụ:

### Spending anomaly

> ⚠️ Chi phí ăn ngoài tháng này tăng 32% so với tháng trước.

### Spending pattern

> Bạn thường chi nhiều nhất vào thứ Sáu và thứ Bảy.

### Merchant insight

> Bạn đã chi 1.250.000đ tại Highlands trong 3 tháng gần nhất.

### Category insight

> Food & Drink đang chiếm 28% tổng chi tiêu tháng này.

### Budget prediction

> Nếu duy trì tốc độ chi tiêu hiện tại, bạn có thể vượt ngân sách tháng khoảng 1.800.000đ.

MoneyLens vì vậy chuyển từ:

**Expense Tracker**

thành:

**Financial Decision Support Tool.**

---

# 7. Ask MoneyLens

Một tính năng Premium tiềm năng là cho phép người dùng nói chuyện trực tiếp với dữ liệu tài chính của mình.

Ví dụ:

**User:**

> "3 tháng vừa rồi tôi tốn bao nhiêu tiền cho cà phê?"

**MoneyLens:**

> Bạn đã chi khoảng 2.460.000đ cho cà phê trong 3 tháng gần nhất.
>
> Trung bình khoảng 820.000đ/tháng.
>
> Highlands chiếm phần lớn chi tiêu với khoảng 1.350.000đ.

---

**User:**

> "Tháng này tôi tiêu nhiều hơn tháng trước ở đâu?"

**MoneyLens:**

> Chi tiêu tháng này tăng 1.720.000đ.
>
> Ba nhóm tăng nhiều nhất:
>
> Ăn uống: +720.000đ  
> Shopping: +610.000đ  
> Transportation: +390.000đ

Điểm quan trọng:

**AI không trực tiếp tính toán số tiền.**

Database và calculation engine thực hiện phép tính.

AI chịu trách nhiệm:

**Hiểu câu hỏi → xác định dữ liệu cần truy vấn → giải thích kết quả bằng ngôn ngữ tự nhiên.**

---

# 8. Các tính năng chính

### Expense Management

- Thêm khoản chi thủ công
- Chỉnh sửa giao dịch
- Category / Subcategory
- Merchant
- Notes
- Search transactions

### AI Receipt Scanner

- Chụp hóa đơn
- Upload ảnh
- OCR
- Nhận diện merchant
- Nhận diện ngày
- Nhận diện tổng tiền
- AI categorization
- User confirmation

### Item-Level Receipt Analysis

Phiên bản nâng cao có thể:

- Nhận diện từng sản phẩm
- Giá từng sản phẩm
- Quantity
- Discount
- Category từng sản phẩm

### Dashboard

Hiển thị:

- Total Spending
- Spending by Category
- Daily Spending
- Weekly Spending
- Monthly Spending
- Income vs Expense
- Budget Progress

### Budget

Người dùng có thể đặt:

**Monthly Budget: 15.000.000đ**

hoặc:

Food: 4.000.000đ  
Shopping: 2.000.000đ  
Entertainment: 1.000.000đ

MoneyLens cảnh báo khi người dùng gần vượt budget.

### AI Insights

AI tự động phát hiện:

- Spending anomaly
- Category tăng bất thường
- Merchant thường xuyên
- Spending trend
- Budget risk

### Ask MoneyLens

Cho phép đặt câu hỏi bằng ngôn ngữ tự nhiên về dữ liệu tài chính cá nhân.

---

# 9. Unique Value Proposition

Giá trị cốt lõi của MoneyLens có thể tóm tắt bằng:

> **Don't track every expense. Just capture it. MoneyLens understands the rest.**

MoneyLens giảm công việc của người dùng từ:

**Manual Tracking**

xuống:

**Capture → Confirm.**

Nhưng lợi thế dài hạn không chỉ nằm ở capture.

Lợi thế thực sự là:

**Capture → Understand → Analyze → Advise.**

---

# 10. So sánh định vị sản phẩm

| Khả năng | App tài chính truyền thống | MoneyLens |
|---|---|---|
| Nhập expense | ✅ | ✅ |
| Category | ✅ | ✅ |
| Budget | ✅ | ✅ |
| Dashboard | ✅ | ✅ |
| Chụp hóa đơn | Một số app | ✅ |
| AI đọc hóa đơn | Một số app | ✅ |
| Nhận diện từng item | Ít phổ biến hơn | ⭐ Core feature |
| AI phân loại item | Hạn chế/tùy app | ⭐ |
| Spending insights | Có ở một số app | ⭐ AI-driven |
| Natural-language query | Không phổ biến | ⭐ |
| Personalized AI assistant | Hạn chế | ⭐ Long-term vision |

MoneyLens không nên cạnh tranh bằng tuyên bố rằng "các app khác không scan receipt", vì tính năng này đã tồn tại trên thị trường.

MoneyLens nên cạnh tranh bằng mức độ **hiểu dữ liệu sau khi scan**.

---

# 11. MVP – Version 1.0

Không nên xây toàn bộ vision ngay từ đầu.

MVP chỉ cần chứng minh một giả thuyết:

> **Người dùng có ghi chép chi tiêu thường xuyên hơn nếu chỉ cần chụp hóa đơn và xác nhận không?**

Version 1.0 gồm:

### Authentication
- Sign up
- Login
- Profile

### Expense
- Add expense
- Edit expense
- Delete expense
- Category

### AI Receipt Scan
- Camera
- Image upload
- OCR
- Merchant extraction
- Date extraction
- Amount extraction
- Category prediction
- Confirmation screen

### Dashboard
- Today
- This week
- This month
- Spending by category

### Budget
- Monthly budget
- Category budget
- Budget progress

Chưa cần:

- AI Chat
- Investment
- Bank integration
- Financial advice
- Family finance
- Subscription management
- Complex forecasting

---

# 12. Roadmap

## Phase 1 — Expense Tracker

Mục tiêu:

**Capture expenses effortlessly.**

Xây dựng:

Manual expense  
Receipt scan  
Auto categorization  
Dashboard  
Budget

---

## Phase 2 — Smart Expense Intelligence

Mục tiêu:

**Understand spending.**

Thêm:

Item-level receipt extraction  
Merchant recognition  
Advanced categories  
Spending patterns  
Anomaly detection

---

## Phase 3 — AI Financial Assistant

Mục tiêu:

**Understand your money.**

Thêm:

Ask MoneyLens  
AI Insights  
Budget forecasting  
Financial summaries  
Personalized recommendations

---

## Phase 4 — Financial Platform

Có thể mở rộng sang:

Income tracking  
Subscription detection  
Saving goals  
Shared finance  
Family finance  
Recurring payments  
Bank synchronization

MoneyLens lúc này chuyển từ:

**Expense AI**

→ **Personal Finance AI**

---

# 13. Business Model

## Free

Dùng để acquisition:

- Manual expense tracking
- Dashboard cơ bản
- Budget
- 10–20 AI receipt scans/tháng

## MoneyLens Pro

Ví dụ:

**69.000đ – 99.000đ/tháng**

hoặc:

**599.000đ – 799.000đ/năm**

Bao gồm:

- Nhiều AI receipt scans hơn
- Item-level analysis
- AI Insights
- Ask MoneyLens
- Advanced analytics
- Export CSV/Excel
- Cloud backup
- Advanced budget analysis

Mục tiêu là để người dùng cảm thấy:

> Giá trị MoneyLens giúp họ tiết kiệm hoặc quản lý được lớn hơn số tiền subscription.

---

# 14. Công nghệ đề xuất

### Mobile

**Flutter + Dart**

Một codebase cho:

Android + iOS

### Backend

Có thể sử dụng:

**FastAPI / Node.js**

### Database

**PostgreSQL**

Có thể bắt đầu với Supabase để đơn giản hóa authentication, database và storage.

### AI Pipeline

Receipt Image

↓

Image preprocessing

↓

OCR / Vision Model

↓

Structured Extraction

↓

Validation

↓

AI Categorization

↓

User Confirmation

↓

Transaction Database

AI nên trả structured data, ví dụ:

```json
{
  "merchant": "Highlands Coffee",
  "date": "2026-10-30",
  "currency": "VND",
  "total": 80000,
  "category": "food_and_drink",
  "subcategory": "coffee",
  "confidence": 0.94
}
```

Sau đó backend validate dữ liệu trước khi lưu.

---

# 15. Product Principle

MoneyLens nên tuân theo một nguyên tắc:

## AI proposes. User controls.

Đặc biệt vì đây là dữ liệu tài chính.

AI có thể:

- đọc hóa đơn
- đề xuất category
- phát hiện pattern
- giải thích dữ liệu

Nhưng người dùng luôn có thể:

**Review → Edit → Confirm.**

Database vẫn là **source of truth**, không phải LLM.

---

# 16. Tầm nhìn sản phẩm

MoneyLens không hướng tới trở thành một app mà người dùng phải dành nhiều thời gian sử dụng.

Ngược lại:

> **Ứng dụng quản lý tài chính tốt là ứng dụng yêu cầu người dùng làm ít việc nhất nhưng vẫn giúp họ hiểu tiền của mình.**

Quá trình phát triển sản phẩm:

**V1**

> "Tôi không cần nhập expense nữa."

↓

**V2**

> "Tôi biết chính xác tiền của mình đi đâu."

↓

**V3**

> "Tôi hiểu tại sao mình tiêu nhiều."

↓

**V4**

> "MoneyLens giúp tôi đưa ra quyết định tài chính tốt hơn."

Đó là hướng biến MoneyLens từ một **expense tracking app** thành một **AI-powered personal finance assistant**.