# 📚 StudyHub — Kho học liệu & Trợ lý học tập AI

StudyHub là ứng dụng web giúp sinh viên **quản lý tài liệu học tập theo môn** và **học cùng AI ngay trên chính tài liệu của mình**: hỏi đáp có trích dẫn nguồn, tạo đề trắc nghiệm, thi thử có tính giờ và theo dõi tiến bộ.

> Đồ án môn **HSF302** — Spring Boot · Spring Security · Spring Data JPA · Thymeleaf · SQL Server.
> Phân tích kiến trúc chi tiết và kịch bản demo: xem [USER_GUIDE.md](USER_GUIDE.md).

---

## ✨ Tính năng

### 🔐 Tài khoản & bảo mật
- Đăng ký / đăng nhập bằng email và mật khẩu, **Đăng nhập với Google** (OAuth2 / OpenID Connect).
- **Spring Security**: phân quyền theo URL, mật khẩu băm **BCrypt**, chống **CSRF** cho form và mọi lệnh `fetch()`.
- Tài khoản cũ (hash SHA-256) vẫn đăng nhập được và **tự nâng cấp lên BCrypt** ở lần đăng nhập kế tiếp.
- Đăng nhập Google bằng email đã có tài khoản sẽ **liên kết** vào tài khoản đó, nếu chưa có thì tạo tài khoản mới (kèm ảnh đại diện Google).
- Đăng xuất bằng `POST` có token CSRF. API trả `401 JSON` khi hết phiên đăng nhập.

### 📁 Quản lý tài liệu & môn học
- Thêm / sửa / xóa tài liệu. Upload file (PDF, DOCX, PPTX, ZIP…, tối đa 50MB) hoặc gắn link ngoài (Drive, GitHub…).
- Tìm kiếm tức thì không phân biệt dấu tiếng Việt, lọc theo danh mục và môn, sắp xếp, đánh dấu sao (AJAX), tải về.
- Danh mục **môn học / học phần**.
- **Kho tài liệu chia sẻ**: chia sẻ lên kho chung, hủy chia sẻ, kéo tài liệu của người khác về kho riêng.

### 🤖 Trợ lý AI (RAG chatbot)
- 2 chế độ: **Tra cứu tài liệu** (chỉ trả lời từ tài liệu, có trích dẫn `[1]`, `[2]`) và **Trò chuyện tự do**.
- Tự lập chỉ mục tài liệu: trích văn bản PDF / DOCX / PPTX / TXT **theo từng trang, từng slide**, chia đoạn có chồng lấn.
- Truy hồi bằng **Okapi BM25** viết bằng Java, chuẩn hóa tiếng Việt không dấu, kèm **từ điển thuật ngữ Việt–Anh** ("cơ sở dữ liệu" = "database" = "CSDL").
- **Streaming** (Server-Sent Events): chữ hiện dần như ChatGPT.
- **Bấm `[n]` mở đúng trang PDF** ngay trong app. Với PPTX thì hiện "Slide N" kèm đoạn trích.
- **Đánh giá 👍 / 👎** từng câu trả lời, lưu DB để đo chất lượng RAG.
- Lịch sử nhiều cuộc trò chuyện (đổi tên, xóa), nhớ ngữ cảnh nhiều lượt.
- **Groq + Google Gemini**, chọn model ngay trên giao diện. Khi model quá tải hoặc quá thời gian chờ: **tự thử lại, đổi sang model dự phòng, rồi đổi nhà cung cấp** (kiên nhẫn tối đa khoảng 3 phút).
- Markdown + công thức toán (KaTeX), nhập bằng giọng nói, đọc câu trả lời bằng giọng tiếng Việt.

### 📝 Quiz & Phòng thi thử
- **Quiz luyện nhanh** trong khung chat: AI soạn câu A/B/C/D từ tài liệu (3 mức độ, có giải thích và nguồn).
- **Phòng thi thử (Mock Exam Room)**:
  - Chọn tài liệu, chủ đề, số câu, độ khó, thời gian (5–30 phút).
  - Đồng hồ đếm ngược, hết giờ tự nộp. Bảng điều hướng câu, đánh dấu "xem lại".
  - Đáp án **tự lưu** mỗi lần chọn, tải lại trang không mất bài.
  - **Chấm điểm trên server** (đáp án đúng không gửi xuống trình duyệt). Ghi lại số lần rời tab.
  - Trang kết quả: điểm, câu đúng/sai/bỏ trống, xem lại từng câu kèm giải thích.

### 📊 Kết quả & thống kê học tập
- Số bài đã làm, điểm trung bình và cao nhất, tỉ lệ đúng, **biểu đồ tiến bộ** (Chart.js).
- Tỉ lệ đúng theo độ khó, **tài liệu cần ôn thêm** (yếu nhất lên đầu).
- **Sổ tay câu hay sai** ("Sai N lần") và nút **Luyện lại** tạo đề ngay từ các câu đã sai.
- Thống kê 👍 / 👎 câu trả lời AI, tách theo RAG và chat thường.

---

## 🛠️ Tech stack

| Tầng | Công nghệ |
|---|---|
| Ngôn ngữ / Build | Java 17, Apache Maven |
| Framework | Spring Boot **4.1.1** (Spring Framework 7): Spring Web MVC, Bean Validation, DevTools |
| Bảo mật | **Spring Security 7** (form login, BCrypt, CSRF), **OAuth2 Client** (Đăng nhập với Google / OIDC) |
| Dữ liệu | Spring Data JPA + **Hibernate 7**, **Microsoft SQL Server** (`mssql-jdbc`) |
| Giao diện | **Thymeleaf** (fragments dùng chung), **Bootstrap 5.3.3**, Bootstrap Icons, CSS tự viết |
| Thư viện JS | Marked.js, DOMPurify, KaTeX, Chart.js 4.4.1, Web Speech API |
| AI | **Groq Cloud** và **Google Gemini API** qua `java.net.http.HttpClient`; streaming bằng `SseEmitter` |
| RAG | BM25 tự cài + từ điển thuật ngữ, không cần vector DB |
| Xử lý tài liệu | Apache **PDFBox 2.0.31**, Apache **POI 5.2.5**, Jackson |
| Kiểm thử | JUnit 5, Mockito, Spring Boot Test, Spring Security Test (MockMvc) |

**Kiến trúc:** Controller → Service → Repository → Entity.
- Phần AI dùng **Strategy Pattern**: `AiChatService` có 2 cài đặt Groq và Gemini, được chọn và chuyển đổi dự phòng qua `AiServiceFactory`.
- Sau khi Spring Security xác thực, người dùng được nạp vào session (`currentUser`) để các controller sử dụng.

**Entity chính:** `User`, `Subject`, `Document`, `DocumentChunk`, `ChatConversation`, `ChatMessage`, `QuizAttempt`, `QuizAttemptQuestion`.

---

## 🚀 Cài đặt & chạy

### 1. Yêu cầu
- JDK 17+ và Maven 3.9+ (hoặc IntelliJ IDEA).
- Microsoft SQL Server (chạy local cổng `1433`).

### 2. Cơ sở dữ liệu
1. Tạo database `StudyHubDB`, có thể chạy script [`src/main/resources/db/setup-sqlserver.sql`](src/main/resources/db/setup-sqlserver.sql) bằng SSMS hoặc Azure Data Studio.
2. Sửa tài khoản SQL Server trong [`application.properties`](src/main/resources/application.properties):
   ```properties
   spring.datasource.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=StudyHubDB;encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=true
   spring.datasource.username=sa
   spring.datasource.password=<mật khẩu của bạn>
   ```
   Bảng và cột mới được Hibernate tự tạo (`ddl-auto=update`), không mất dữ liệu cũ.

### 3. Biến môi trường
| Biến | Bắt buộc | Dùng cho |
|---|---|---|
| `GROQ_API_KEY` | Nên có | Trợ lý AI qua Groq (https://console.groq.com/keys) |
| `GEMINI_API_KEY` | Nên có | Trợ lý AI qua Google Gemini (https://aistudio.google.com/apikey) |
| `GOOGLE_CLIENT_ID` | Không | Đăng nhập với Google |
| `GOOGLE_CLIENT_SECRET` | Không | Đăng nhập với Google |

- Có ít nhất một key AI là dùng được Trợ lý AI. Có cả hai thì app tự chuyển sang bên còn lại khi một bên quá tải.
- Không cấu hình Google thì app vẫn chạy và **ẩn nút "Đăng nhập với Google"**.

Trong IntelliJ: **Run → Edit Configurations → Environment variables**.

### 4. Bật "Đăng nhập với Google" (tùy chọn)
1. Vào [Google Cloud Console](https://console.cloud.google.com/) → tạo hoặc chọn project.
2. **APIs & Services → OAuth consent screen**: chọn *External*, điền tên app và email hỗ trợ. Ở chế độ *Testing*, thêm email của nhóm vào **Test users**.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**, loại *Web application*.
4. Thêm **Authorized redirect URI**:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
5. Copy *Client ID* và *Client secret* vào `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`, rồi khởi động lại app. Log sẽ in `Đăng nhập với Google: ĐÃ BẬT`.

### 5. Chạy ứng dụng
```bash
mvn spring-boot:run
```
Hoặc chạy `StudyHubApplication` trong IntelliJ. Sau đó mở http://localhost:8080

**Tài khoản mẫu** (tự tạo khi khởi động): `student@gmail.com` / `123456` và `maianh@gmail.com` / `123456`.

### 6. Chạy kiểm thử
```bash
mvn test
```
Một số test tích hợp (`SecurityConfigTest`, `SearchVietnameseTest`) khởi động toàn bộ ứng dụng nên cần SQL Server đang chạy.

---

## 🗺️ Các trang chính

| Đường dẫn | Chức năng |
|---|---|
| `/` | Trang giới thiệu, đăng nhập / đăng ký |
| `/dashboard` | Tổng quan kho tài liệu cá nhân |
| `/shared-documents` | Kho tài liệu chia sẻ |
| `/subjects` | Môn học / học phần |
| `/ai/chat` | Trợ lý AI (RAG + chat tự do + quiz) |
| `/exam` | Phòng thi thử |
| `/exam/history` | Kết quả & thống kê học tập |

---

## 📂 Cấu trúc mã nguồn

```
src/main/java/com/studyhub/
├── StudyHubApplication.java
├── config/          # SecurityConfig, PasswordConfig, LegacySha256PasswordEncoder, GlobalModelAdvice, DataInitializer...
├── controller/      # Home, Document, Subject, SharedDocument, AiChat, Exam, GlobalExceptionHandler
├── service/         # UserService, StudyHubUserDetailsService, GoogleOidcUserService, DocumentService, QuizAttemptService...
├── ai/
│   ├── config/      # AiProvider, AiSessionConfig, AiConfigResolver
│   ├── dto/         # Chat / Quiz / Source DTO
│   └── service/     # RagService, Bm25Retriever, TermGlossary, Groq/GeminiChatService, AiServiceFactory, RagQuizService...
├── model/           # Entity JPA
├── repository/      # Spring Data JPA repositories
└── dto/
src/main/resources/
├── templates/       # Thymeleaf: landing, index, ai/chat, exam/*, fragments/*
├── static/          # css/style.css, css/exam.css, js/app.js, js/csrf.js
└── application.properties
src/test/java/...    # 57 test: bảo mật, mật khẩu, RAG/BM25, dự phòng AI, trích xuất theo trang...
```
