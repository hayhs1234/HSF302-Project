# 📚 StudyHub - Hệ Thống Quản Lý Tài Liệu Học Tập

Ứng dụng web quản lý tài liệu học tập, giáo trình, đề thi, slide bài giảng xây dựng bằng **Spring Boot**, **Thymeleaf**, **Bootstrap 5** và **Microsoft SQL Server**.

---

## 🛠️ Công Nghệ Sử Dụng

- **Backend:** Java 17+, Spring Boot (Spring WebMVC, Spring Data JPA, Hibernate, Bean Validation)
- **Database:** Microsoft SQL Server (kèm driver `mssql-jdbc` và hỗ trợ H2 profile để chạy thử nhanh)
- **Frontend / UI:** Thymeleaf + Bootstrap 5.3 + Bootstrap Icons (Giao diện tối giản, hiện đại, thoáng mắt, không rối chữ)
- **Build tool:** Apache Maven

---

## 📁 Cấu Trúc Dự Án

```
Assignment/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/studyhub/
│   │   │   ├── StudyHubApplication.java       # Main class chạy ứng dụng
│   │   │   ├── config/DataInitializer.java    # Khởi tạo tự động dữ liệu mẫu ban đầu
│   │   │   ├── controller/
│   │   │   │   ├── HomeController.java        # Dashboard, bộ lọc danh mục & môn học
│   │   │   │   ├── DocumentController.java    # Quản lý tài liệu (CRUD, download, favorite)
│   │   │   │   └── SubjectController.java     # Quản lý môn học / học phần
│   │   │   ├── dto/DocumentFormDto.java       # Form DTO tải lên và chỉnh sửa
│   │   │   ├── model/
│   │   │   │   ├── Document.java              # Entity Tài liệu (PDF, PPTX, DOCX, ZIP...)
│   │   │   │   └── Subject.java               # Entity Môn học
│   │   │   ├── repository/
│   │   │   │   ├── DocumentRepository.java
│   │   │   │   └── SubjectRepository.java
│   │   │   └── service/
│   │   │       ├── DocumentService.java
│   │   │       └── SubjectService.java
│   │   └── resources/
│   │       ├── application.properties         # Cấu hình kết nối SQL Server
│   │       ├── application-h2.properties      # Cấu hình H2 database (dùng test nhanh)
│   │       ├── db/setup-sqlserver.sql         # Script tạo DB & chèn dữ liệu trên SQL Server
│   │       ├── static/
│   │       │   ├── css/style.css              # Giao diện tùy chỉnh gọn gàng, hiện đại
│   │       │   └── js/app.js                  # AJAX xem chi tiết offcanvas & toggle sao
│   │       └── templates/
│   │           ├── index.html                 # Trang Tổng quan / Dashboard chính
│   │           ├── fragments/                 # Header & Sidebar
│   │           ├── documents/form.html        # Trang Form thêm/sửa tài liệu
│   │           └── subjects/list.html         # Trang danh sách môn học
```

---

## 🗄️ Cấu Hình & Chuẩn Bị Cơ Sở Dữ Liệu SQL Server

### Cách 1: Sử dụng SQL Server (Khuyên dùng)
1. Mở **SQL Server Management Studio (SSMS)** hoặc **Azure Data Studio**.
2. Mở tệp script: `src/main/resources/db/setup-sqlserver.sql`.
3. Bấm **Execute** để tạo cơ sở dữ liệu `StudyHubDB` và các bảng dữ liệu mẫu.
4. Kiểm tra hoặc điều chỉnh thông tin tài khoản SQL Server trong `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=StudyHubDB;encrypt=true;trustServerCertificate=true
   spring.datasource.username=sa
   spring.datasource.password=sa123456
   ```

---

## 🚀 Hướng Dẫn Lệnh Chạy Ứng Dụng Local

### 1. Chạy với SQL Server (Mặc định):
Mở terminal tại thư mục dự án (`c:\FALL2026\HSF302\Assignment`) và chạy:

```powershell
mvn spring-boot:run
```

Hoặc sau khi build file JAR:
```powershell
mvn package -DskipTests
java -jar target/studyhub-1.0.0.jar
```

### 2. Chạy thử nghiệm nhanh không cần bật SQL Server (H2 Profile):
Nếu bạn muốn mở giao diện kiểm tra nhanh ngay lập tức mà chưa bật SQL Server:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```
Hoặc với JAR:
```powershell
java -jar target/studyhub-1.0.0.jar --spring.profiles.active=h2
```

---

## 🌐 Truy Cập Ứng Dụng

Sau khi server khởi động thành công, mở trình duyệt truy cập:
- **Trang chủ StudyHub:** [http://localhost:8080](http://localhost:8080)
- **Quản lý môn học:** [http://localhost:8080/subjects](http://localhost:8080/subjects)
- **Tài liệu đã đánh dấu sao:** [http://localhost:8080/favorites](http://localhost:8080/favorites)

---

## ✨ Các Tính Năng Đã Hoàn Thiện:
- [x] Khởi tạo khung dự án Spring Boot + Thymeleaf + Bootstrap 5 + SQL Server.
- [x] **Lưu trữ đầy đủ thông tin tài liệu:** Tên tài liệu, Môn học, Phân loại, Tác giả, Mô tả chi tiết, **Đường dẫn liên kết URL** (Drive, Docs, Github, v.v.).
- [x] **15 tài liệu mẫu:** Nạp sẵn 15 tài liệu học tập thực tế thuộc nhiều môn chuyên ngành CNTT (Cấu trúc dữ liệu & Giải thuật, CSDL, Kiểm thử phần mềm, Mạng máy tính, Kiến trúc máy tính, Lập trình Java Web, Trí tuệ nhân tạo).
- [x] **Cơ chế chống nhân đôi dữ liệu (Idempotent):** Kiểm tra `existsByTitle` và `existsByCode` trước khi thêm, đảm bảo khởi động lại server bao nhiêu lần cũng không bị trùng lặp dữ liệu.
- [x] **Nút mở liên kết URL trực tiếp:** Có biểu tượng huy hiệu `URL` và nút mở liên kết ngoài trên bảng danh sách cũng như trong ngăn kéo chi tiết (Offcanvas).
- [x] Thanh tìm kiếm từ khóa tức thì (tên tài liệu, tác giả, thẻ tags, tóm tắt).
- [x] Lọc tài liệu theo danh mục và môn học; sắp xếp linh hoạt.
- [x] Đánh dấu sao yêu thích (Favorite) tức thời không tải lại trang (AJAX).
- [x] Modal & Form thêm/sửa tài liệu kèm ô nhập URL và upload file.
- [x] Tải tài liệu về máy (Download).
- [x] Tối ưu hóa UI bảng danh sách: Chuyển cột "Dung lượng" và toàn bộ cụm "Thao tác" (Mở URL, Tải về, Sửa, Xóa) vào ngăn kéo chi tiết (Offcanvas) bên phải, giúp bảng bên ngoài thoáng mắt, hiển thị trọn vẹn cột Phân loại và Tên tài liệu.

