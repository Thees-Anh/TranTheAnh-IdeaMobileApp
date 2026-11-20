# PawQuest — Release Process

## 1. Phiên bản
- `versionName`: SemVer `MAJOR.MINOR.PATCH` (1.0.0, 1.0.1 …).
- `versionCode`: `MAJOR*10000 + MINOR*100 + PATCH` → 1.0.0 = **10000**, 1.0.1 = 10001, 1.1.0 = 10100. Phải tăng ở mọi bản upload, kể cả internal testing (nếu cần upload lại cùng phiên bản, dùng 10000 → 10000 không được; tăng PATCH).
- Mỗi bản upload tương ứng một **tag git** `vX.Y.Z` trên nhánh `release/X.Y`, working tree sạch.

## 2. Cấu hình build release (không commit)
Đặt qua biến môi trường (CI secrets) hoặc `local.properties` của máy build:

| Khoá | Ý nghĩa |
|---|---|
| `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | Project **production** (chỉ publishable/anon key) |
| `PAWQUEST_VERSION_CODE`, `PAWQUEST_VERSION_NAME` | Theo §1 |
| `PAWQUEST_RELEASE_STORE_FILE`, `PAWQUEST_RELEASE_STORE_PASSWORD`, `PAWQUEST_RELEASE_KEY_ALIAS`, `PAWQUEST_RELEASE_KEY_PASSWORD` | Upload key (keystore ngoài repo, có bản sao lưu an toàn) |
| `PAWQUEST_PRIVACY_POLICY_URL` | URL công khai của `docs/legal/privacy-policy.html` đã điền thông tin |
| `PAWQUEST_ACCOUNT_DELETION_URL` | URL công khai của `docs/legal/account-deletion.html` |
| `PAWQUEST_OFFICIAL_REPOSITORIES_ONLY=true` | Chỉ tải dependency từ Google Maven / Maven Central |

`bundleRelease` chạy `verifyPawQuestRelease` trước và **fail** nếu thiếu bất kỳ mục nào ở trên (trừ cờ repository). `assembleRelease` vẫn tạo APK chưa ký để thử R8 trên máy dev.

## 3. Lệnh build
```powershell
$env:PAWQUEST_OFFICIAL_REPOSITORIES_ONLY="true"
.\gradlew.bat :shared:testAndroidHostTest :androidApp:lintRelease :androidApp:bundleRelease
```
Kết quả: `androidApp/build/outputs/bundle/release/androidApp-release.aab`. Kiểm tra:
```powershell
jarsigner -verify -verbose:summary androidApp\build\outputs\bundle\release\androidApp-release.aab
```

## 3a. Khi thêm hoặc nâng cấp thư viện
`gradle/verification-metadata.xml` ghim SHA‑256 mọi artifact; CI chạy strict nên artifact mới sẽ làm build fail.
1. Trên máy dev: `.\gradlew.bat --write-verification-metadata sha256 :shared:testAndroidHostTest :androidApp:lintRelease :androidApp:assembleRelease`.
2. Trên GitHub: *Actions → CI → Run workflow* với `refresh_verification_metadata = true`, tải artifact `verification-metadata-linux` (artifact chỉ có trên Linux, ví dụ `aapt2-*-linux.jar`, một số BOM).
3. Chỉ gộp entry mới sau khi đối chiếu SHA‑1 công bố của repository gốc. Checksum **khác** cho cùng một file là dấu hiệu cần điều tra (ngoại lệ đã biết: plugin marker JetBrains khác nhau giữa Maven Central và Gradle Plugin Portal, đã ghi `also-trust`).

## 4. Checklist trước khi upload
- [ ] Tag `vX.Y.Z` trỏ đúng commit; `git status` sạch.
- [ ] Unit test, lint release (0 error), `tools/testing/test_database.py` PASS (CI xanh).
- [ ] Production Supabase có **đủ** migration của tag (so `supabase migration list` với `supabase/migrations/`), đã backup trước khi áp migration mới.
- [ ] `supabase/tests/verification.sql` trên production: 0 vi phạm.
- [ ] Privacy policy & trang xoá tài khoản truy cập được, khớp Data safety.
- [ ] Ghi chú phát hành (vi/en).

## 5. Rollout
| Giai đoạn | Đối tượng | Thời gian tối thiểu | Điều kiện đi tiếp |
|---|---|---|---|
| Internal testing | Nhóm phát triển | 1–2 ngày | QA_TEST_PLAN P0/P1 PASS trên bản cài từ Play |
| Closed testing | Tester ngoài (đáp ứng yêu cầu số tester/thời gian của Play cho tài khoản cá nhân, nếu áp dụng) | Theo yêu cầu Play | Không có SEV‑1/2 mở; pre‑launch report 0 crash |
| Production 10 % | | 2 ngày | Crash‑free ≥ 99,5 %, không lỗi kinh tế |
| Production 50 % | | 2 ngày | như trên |
| Production 100 % | | — | |

**Tiêu chí dừng ngay (Halt rollout):** crash‑free < 99 %, ANR tăng vượt ngưỡng Play, bất kỳ sai lệch số dư nào trong `verification.sql`, lỗi đăng nhập/xoá tài khoản diện rộng. Xem `RUNBOOK.md` §4.

## 6. Sau phát hành
Theo dõi theo `RUNBOOK.md` §2 hằng ngày trong 7 ngày; ghi kết quả vào `docs/TEST_REPORT.md`.
