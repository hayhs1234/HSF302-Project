# 📖 HƯỚNG DẪN CÀI ĐẶT, SỬ DỤNG VÀ BÁO CÁO MÃ NGUỒN (USER GUIDE)
# PROJECT CUỐI KỲ: STUDYHUB AI ASSISTANT & RAG KNOWLEDGE BASE
> **Môn học:** HSF302 - Java & Spring Framework  
> **Dự án:** Hệ thống Quản lý Tài liệu Học tập & Trợ lý Trí tuệ Nhân tạo StudyHub  
> **Kiến trúc:** Spring Boot 4.1 (Java 17+), Spring Data JPA, Thymeleaf, Bootstrap 5.3, SQL Server, Multi-provider LLMs (Google Gemini / Groq Meta Llama 3) & Okapi BM25 RAG Engine  

---

## 📑 MỤC LỤC
1. [Giới thiệu Đề tài & Yêu cầu Đồ án](#1-giới-thiệu-đề-tài--yêu-cầu-đồ-án)
2. [Hướng dẫn Cài đặt & Khởi chạy (Setup Guide)](#2-hướng-dẫn-cài-đặt--khởi-chạy-setup-guide)
3. [Hướng dẫn Lấy API Key & Cấu hình Hoán chuyển Nền tảng](#3-hướng-dẫn-lấy-api-key--cấu-hình-hoán-chuyển-nền-tảng)
4. [Kiến trúc Mã nguồn & Thiết kế Kỹ thuật (Code Walkthrough)](#4-kiến-trúc-mã-nguồn--thiết-kế-kỹ-thuật-code-walkthrough)
   - [4.1. Thiết kế Hoán chuyển Nền tảng (Strategy Pattern)](#41-thiết-kế-hoán-chuyển-nền-tảng-strategy-pattern)
   - [4.2. Kiến trúc Đường ống RAG Chuẩn (Ingestion, Indexing, Okapi BM25 & Grounded Generation)](#42-kiến-trúc-đường-ống-rag-chuẩn-ingestion-indexing-okapi-bm25--grounded-generation)
   - [4.3. Bộ giải mã & Trích xuất Văn bản Đa định dạng (PDF, DOCX, PPTX, TXT)](#43-bộ-giải-mã--trích-xuất-văn-bản-đa-định-dạng-pdf-docx-pptx-txt)
   - [4.4. Quản lý Hội thoại Đa lượt (Multi-turn Conversation Memory) & Phân quyền Tài liệu](#44-quản-lý-hội-thoại-đa-lượt-multi-turn-conversation-memory--phân-quyền-tài-liệu)
   - [4.5. Giao tiếp Giọng nói Tiếng Việt (Voice-to-Text & Text-to-Speech)](#45-giao-tiếp-giọng-nói-tiếng-việt-voice-to-text--text-to-speech)
   - [4.6. Hệ thống Trắc nghiệm Tương tác RAG Quiz (Interactive RAG Quiz Engine)](#46-hệ-thống-trắc-nghiệm-tương-tác-rag-quiz-interactive-rag-quiz-engine)
5. [Kịch bản Kiểm thử & Hướng dẫn Demo (Demo Scenarios)](#5-kịch-bản-kiểm-thử--hướng-dẫn-demo-demo-scenarios)

---

## 1. GIỚI THIỆU ĐỀ TÀI & YÊU CẦU ĐỒ ÁN

Đồ án được thiết kế đáp ứng đầy đủ 5 tiêu chí cốt lõi của giảng viên:
1. **1 Ứng dụng Web hoàn chỉnh (`StudyHub`):** Hệ thống quản lý học liệu đại học theo môn học, hỗ trợ tải lên, phân loại, đánh dấu sao, chia sẻ cộng đồng và kéo tài liệu về kho cá nhân.
2. **Chat với AI (Chat ra ngoài / Trò chuyện trực tiếp):** Trò chuyện đa lượt (Multi-turn) với mô hình ngôn ngữ lớn để giải đáp kiến thức học thuật, lập trình, giải thuật, cơ sở dữ liệu, kinh tế...
3. **Chat với AI (RAG - Retrieval-Augmented Generation):**
   - Tự động bóc tách văn bản từ tệp vật lý (`.pdf`, `.docx`, `.pptx`, `.txt`, `.md`), chia nhỏ thành các phân đoạn (`document_chunks`) và lập chỉ mục trong SQL Server.
   - Tìm kiếm và xếp hạng độ liên quan bằng thuật toán **Okapi BM25** có chuẩn hóa tiếng Việt không dấu, loại bỏ hư từ (stopwords) và kiểm soát ngưỡng điểm liên quan (`MIN_RELEVANCE_SCORE`).
   - Trả lời kèm **số hiệu trích dẫn `[1]`, `[2]` tương tác** và thẻ nguồn tài liệu cho phép xem chi tiết hoặc tải tệp gốc ngay trong khung chat.
4. **Cấu hình API Key để hoán chuyển nền tảng chat:** Hỗ trợ hoán đổi động lúc runtime giữa **Google Gemini** (`gemini-2.5-flash`, `gemini-2.5-flash-lite`, `gemini-3-flash-preview`) và **Groq (Meta Llama 3.3 70B & Llama 3.1 8B - Miễn phí 100% & Siêu tốc)** theo mẫu thiết kế **Strategy Pattern + Factory Pattern**. Người dùng chỉ việc chọn model từ thanh công cụ là chat ngay lập tức.
5. **User Guide chi tiết:** Hướng dẫn cài đặt cơ sở dữ liệu, cấu hình môi trường, phân tích kiến trúc mã nguồn và kịch bản demo.

---

## 2. HƯỚNG DẪN CÀI ĐẶT & KHỞI CHẠY (SETUP GUIDE)

### 2.1. Yêu cầu môi trường (Prerequisites)
- **Java Development Kit (JDK):** Phiên bản **Java 17** trở lên (tương thích hoàn toàn với JDK 17, 21, 25).
- **Apache Maven:** 3.8+ (hoặc Maven tích hợp sẵn trong IntelliJ IDEA / Eclipse / VS Code).
- **Cơ sở dữ liệu:** **Microsoft SQL Server** (2019/2022).

---

### 2.2. Khởi tạo Cơ sở Dữ liệu SQL Server
1. Mở **SQL Server Management Studio (SSMS)**.
2. Mở tệp: `src/main/resources/db/setup-sqlserver.sql` và nhấn **Execute (F5)** để tạo cơ sở dữ liệu `StudyHubDB` cùng đầy đủ các bảng:
   - `users`, `subjects`, `documents`
   - `document_chunks` (lưu trữ phân đoạn văn bản đã lập chỉ mục RAG)
   - `chat_conversations`, `chat_messages` (lưu trữ lịch sử trò chuyện đa lượt và trích dẫn JSON)
3. Kiểm tra cấu hình kết nối trong `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=StudyHubDB;encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=true
   spring.datasource.username=sa
   spring.datasource.password=123456
   spring.jpa.hibernate.ddl-auto=update
   ```
   *(Lưu ý: Với `ddl-auto=update`, Hibernate cũng tự động đồng bộ các bảng và cột khi ứng dụng khởi chạy).*

---

### 2.3. Lệnh Khởi chạy Ứng dụng

Mở Terminal tại thư mục `Assignment` và chạy:

```powershell
mvn spring-boot:run
```

Khi khởi chạy lần đầu, `DataInitializer` kết hợp cùng `SampleDocumentFileGenerator` và `DocumentIndexingService` sẽ tự động:
1. Tạo 2 tài khoản sinh viên mẫu:
   - **Tài khoản 1:** `student@gmail.com` / mật khẩu: `123456` (Nguyễn Văn An)
   - **Tài khoản 2:** `maianh@gmail.com` / mật khẩu: `123456` (Trần Mai Anh)
2. Tạo các tệp học liệu thực tế (`.pdf`, `.docx`, `.pptx`) trong thư mục `./uploads/` với nội dung học thuật chi tiết (Marketing 4P, Truy vấn SQL, Giải thuật nâng cao, Kiểm thử phần mềm, Xác suất thống kê, Kinh tế vi mô, Pháp luật đại cương...).
3. Bóc tách văn bản, chia nhỏ thành các `document_chunks` và lập chỉ mục vào SQL Server.

Sau khi ứng dụng khởi động xong:
- Truy cập trang chủ: **`http://localhost:8080`**
- Truy cập Trợ lý AI: **`http://localhost:8080/ai/chat`**

---

## 3. HƯỚNG DẪN LẤY API KEY & CẤU HÌNH HOÁN CHUYỂN NỀN TẢNG

Hệ thống hỗ trợ cấu hình API Key theo 2 cách:
- **Cách 1 (Khuyến nghị cho môi trường thực tế):** Khai báo biến môi trường hệ thống `GROQ_API_KEY` hoặc `GEMINI_API_KEY` (hoặc cấu hình trong `application.properties`) để người dùng đăng nhập vào là sử dụng ngay mà không cần tự nhập khóa.
- **Cách 2 (Phục vụ Demo hoán chuyển nền tảng trực tiếp trên Web):** Nhập API Key và chuyển đổi qua lại giữa **Groq (Meta Llama 3)** và **Google Gemini** ngay trong hộp thoại **Cài đặt AI** hoặc chọn nhanh mô hình ngay trên thanh công cụ chat.

### 3.1. Cách lấy Groq (Llama 3) API Key Miễn Phí 100% (Khuyên dùng)
1. Truy cập Groq Console: **[https://console.groq.com/keys](https://console.groq.com/keys)**
2. Đăng nhập tài khoản Google và nhấn **"Create API Key"**.
3. Đặt tên bất kỳ (ví dụ: `StudyHub`) và sao chép chuỗi khóa có dạng `gsk_...`.
4. *Lợi ích:* Hoàn toàn miễn phí, không yêu cầu thẻ tín dụng, tốc độ phản hồi cực nhanh (>300 từ/giây).

### 3.2. Cách lấy Google Gemini API Key Miễn Phí
1. Truy cập Google AI Studio: **[https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)**
2. Đăng nhập tài khoản Google và nhấn **"Create API Key"**.
3. Sao chép chuỗi khóa có dạng `AIzaSy...`.

### 3.3. Cấu hình trên Giao diện StudyHub
1. Đăng nhập vào hệ thống và mở mục **"Trợ lý AI"** (`/ai/chat`).
2. Chọn nhanh mô hình tại thanh chọn mô hình ở đầu trang (ví dụ: *⚡ Llama 3.3 70B* hoặc *✨ Gemini 2.5 Flash*).
3. Nếu muốn đổi API Key cá nhân, nhấn nút **"Cài đặt AI"** ở góc trên bên phải, dán API Key và nhấn **"Lưu thay đổi"**.

---

## 4. KIẾN TRÚC MÃ NGUỒN & THIẾT KẾ KỸ THUẬT (CODE WALKTHROUGH)

Cấu trúc mã nguồn của phân hệ AI & RAG được tổ chức tách biệt, rõ ràng:

```
src/main/java/com/studyhub/
├── ai/
│   ├── config/
│   │   ├── AiProvider.java               # Enum định nghĩa nền tảng: GEMINI, GROQ
│   │   └── AiSessionConfig.java          # Quản lý cấu hình AI & API Key theo Session
│   ├── dto/
│   │   ├── AiConfigFormDto.java          # DTO cập nhật nền tảng, model và API key
│   │   ├── ChatRequestDto.java           # DTO nhận câu hỏi, mode, documentId, conversationId
│   │   ├── ChatResponseDto.java          # DTO trả về câu trả lời, trích dẫn và thông tin hội thoại
│   │   ├── ChatTurnDto.java              # DTO đại diện một lượt hội thoại (USER / ASSISTANT)
│   │   └── SourceChunkDto.java           # DTO chứa thông tin đoạn trích dẫn [1], [2]...
│   └── service/
│       ├── AiChatService.java            # Interface Strategy chung cho mọi nền tảng LLM
│       ├── GeminiChatService.java        # Strategy thực thi gọi REST API Google Gemini (đa lượt)
│       ├── GroqChatService.java          # Strategy thực thi gọi REST API Groq Llama 3 (đa lượt)
│       ├── AiServiceFactory.java         # Factory lựa chọn Strategy theo AiProvider lúc runtime
│       ├── DocumentTextExtractor.java    # Bóc tách văn bản từ PDF (PDFBox), DOCX/PPTX (POI), TXT
│       ├── DocumentIndexingService.java  # Đường ống Chunking & lưu chỉ mục vào bảng document_chunks
│       ├── Bm25Retriever.java            # Thuật toán xếp hạng Okapi BM25 tiếng Việt
│       ├── RagService.java               # Điều phối Retrieval + Grounded Prompt + Citations
│       ├── RagQuizService.java           # Trích xuất RAG và tạo Quiz trắc nghiệm tương tác
│       └── ChatHistoryService.java       # Lưu trữ và khôi phục hội thoại đa lượt từ SQL Server
├── config/
│   ├── SampleDocumentFileGenerator.java  # Khởi tạo tệp PDF/DOCX/PPTX mẫu thực tế khi chạy lần đầu
│   └── DataInitializer.java              # Seed dữ liệu mẫu và kích hoạt lập chỉ mục RAG tự động
├── model/
│   ├── Document.java                     # Entity Tài liệu (kèm trạng thái chỉ mục indexStatus)
│   ├── DocumentChunk.java                # Entity Phân đoạn văn bản phục vụ truy hồi RAG
│   ├── ChatConversation.java             # Entity Phiên hội thoại AI của người dùng
│   └── ChatMessage.java                  # Entity Tin nhắn chi tiết & nguồn trích dẫn JSON
└── controller/
    └── AiChatController.java             # Controller phục vụ giao diện /ai/chat và các REST API
```

---

### 4.1. Thiết kế Hoán chuyển Nền tảng (Strategy Pattern)
Để đáp ứng yêu cầu *"Cấu hình API Key để hoán chuyển nền tảng chat"*, hệ thống áp dụng **Strategy Pattern** kết hợp **Factory Pattern**:

```
                       ┌──────────────────────┐
                       │   AiChatController   │
                       └──────────┬───────────┘
                                  │
                                  ▼
                       ┌──────────────────────┐
                       │   AiServiceFactory   │
                       └──────────┬───────────┘
                                  │
                       ┌──────────▼───────────┐
                       │    AiChatService     │ (Strategy Interface)
                       └──────────┬───────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         ▼                        ▼                        ▼
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ GeminiChatService│     │  GroqChatService │     │                  │
│   (Google API)   │     │(Groq/Llama 3 API)│     │  (OpenAI API)    │
└──────────────────┘     └──────────────────┘     └──────────────────┘
```

- `AiChatService` định nghĩa phương thức chuẩn hỗ trợ cả câu hỏi đơn lẻ và hội thoại đa lượt (`List<ChatTurnDto> historyTurns`).
- `AiServiceFactory` tự động nạp toàn bộ các bean thực thi `AiChatService` vào `EnumMap<AiProvider, AiChatService>`. Khi người dùng chuyển đổi giữa Gemini, Groq và OpenAI trên giao diện, hệ thống chuyển luồng xử lý tức thì mà không cần khởi động lại ứng dụng.

---

### 4.2. Kiến trúc Đường ống RAG Chuẩn (Ingestion, Indexing, Okapi BM25 & Grounded Generation)

Khác với cách đọc tệp trực tiếp mỗi lần hỏi gây chậm và thiếu chính xác, StudyHub triển khai đường ống RAG tách biệt 2 pha **Ingestion (Lập chỉ mục)** và **Retrieval & Generation (Truy hồi & Sinh câu trả lời)**:

```
[PHA 1: INGESTION & INDEXING (Khi tải tài liệu lên hoặc khởi tạo)]
Tệp vật lý (.pdf / .docx / .pptx / .txt)
        │
        ▼
DocumentTextExtractor (Trích xuất toàn văn nội dung)
        │
        ▼
DocumentIndexingService (Chia nhỏ thành các đoạn ~800 ký tự, gối đầu 150 ký tự,
                         chuẩn hóa ASCII tiếng Việt & lưu vào bảng document_chunks)

[PHA 2: RETRIEVAL & GENERATION (Khi người dùng đặt câu hỏi RAG)]
Câu hỏi người dùng + Ngữ cảnh hội thoại
        │
        ▼
Bm25Retriever (Lọc hư từ tiếng Việt, tính điểm Okapi BM25 + Phrase Bonus + Metadata Weight
               trên toàn bộ các chunk thuộc quyền truy cập của người dùng)
        │
        ▼
Kiểm tra ngưỡng điểm liên quan (MIN_RELEVANCE_SCORE = 0.55)
        ├── Không có đoạn nào đạt ngưỡng ──► Trả lời trung thực: Kho tài liệu chưa có thông tin
        └── Có các đoạn đạt ngưỡng (Top-K) ──► Đánh số [1], [2]... đưa vào System Prompt
                                                      │
                                                      ▼
                                        LLM sinh câu trả lời kèm trích dẫn [1], [2]
```

**Điểm nổi bật của `Bm25Retriever`:**
- **Chuẩn hóa tiếng Việt (`normalizeAscii`):** Chuyển đổi đồng nhất chữ hoa/thường và dấu tiếng Việt để câu hỏi có dấu hay không dấu đều khớp chính xác.
- **Loại bỏ hư từ (`STOPWORDS_ASCII`):** Loại bỏ các từ chức năng trong câu hỏi tự nhiên (như *"cho"*, *"tôi"*, *"các"*, *"tệp"*, *"tài"*, *"liệu"*, *"liên"*, *"quan"*, *"tới"*, *"việc"*) để tập trung chấm điểm từ khóa chuyên môn (ví dụ: *"marketing"*, *"dijkstra"*, *"join"*).
- **Chống ảo giác (Anti-Hallucination):** Tuyệt đối không lấy ngẫu nhiên đoạn đầu của tài liệu không liên quan khi điểm BM25 bằng 0.

---

### 4.3. Bộ giải mã & Trích xuất Văn bản Đa định dạng (PDF, DOCX, PPTX, TXT)

Lớp `DocumentTextExtractor.java` hỗ trợ bóc tách nội dung từ các định dạng học liệu phổ biến nhất:
- **`.pdf`**: Sử dụng **Apache PDFBox** (`PDDocument`, `PDFTextStripper`).
- **`.docx`**: Sử dụng **Apache POI** (`XWPFDocument`, `XWPFWordExtractor`).
- **`.pptx`**: Sử dụng **Apache POI** (`XMLSlideShow`, duyệt từng `XSLFSlide` và `XSLFTextShape` để trích xuất nội dung từng slide thuyết trình).
- **`.txt`, `.md`, `.sql`, `.java`**: Đọc trực tiếp với bảng mã UTF-8.
- **Tệp nén (`.zip`, `.rar`)**: Được đánh dấu trạng thái `NO_CONTENT` và hiển thị rõ trên giao diện để người dùng biết tệp nén không dùng để trích xuất văn bản trực tiếp.

---

### 4.4. Quản lý Hội thoại Đa lượt (Multi-turn Conversation Memory) & Phân quyền Tài liệu

1. **Lưu trữ lịch sử hội thoại bền vững trên SQL Server (`chat_conversations` & `chat_messages`):**
   - Mỗi cuộc trò chuyện được tự động đặt tiêu đề theo câu hỏi đầu tiên, cho phép chuyển đổi qua lại ở thanh bên trái, đổi tên hoặc xóa.
   - Khi tải lại trang (F5) hoặc đăng nhập lại, toàn bộ nội dung cuộc trò chuyện và các thẻ trích dẫn RAG được khôi phục nguyên vẹn.
   - Khi người dùng hỏi tiếp câu phụ thuộc ngữ cảnh (ví dụ: *"Tóm tắt slide 3 của tài liệu vừa rồi"*), hệ thống gửi kèm các lượt hội thoại gần nhất để cả bộ truy hồi BM25 và mô hình AI hiểu đúng mạch câu chuyện.
2. **Bảo mật & Phân quyền truy cập tài liệu:**
   - Mọi đường dẫn `/ai/**` và `/api/ai/**` đều được bảo vệ qua `AuthInterceptor`.
   - Khi truy vấn RAG, hệ thống chỉ tìm kiếm trên các tài liệu thuộc sở hữu của người dùng đang đăng nhập hoặc tài liệu đã được chia sẻ công khai (`isShared = true`), ngăn chặn rò rỉ tài liệu riêng tư giữa các tài khoản.

---

### 4.5. Giao tiếp Giọng nói Tiếng Việt (Voice-to-Text & Text-to-Speech)

Hệ thống tích hợp chuẩn **Web Speech API** trên trình duyệt để hỗ trợ tương tác tự nhiên bằng tiếng Việt:
1. **Nhận diện giọng nói tiếng Việt (STT - Speech-to-Text):**
   - Nút Micro (`🎙️`) tích hợp hiệu ứng sóng âm thu âm (`.voice-btn.recording`).
   - Sử dụng `webkitSpeechRecognition` với ngôn ngữ `vi-VN` và `interimResults = true`, cho phép chữ chạy trực tiếp theo thời gian thực vào ô chat ngay khi người dùng đang nói.
2. **Đọc câu trả lời bằng tiếng Việt (TTS - Text-to-Speech):**
   - Mỗi câu trả lời của AI có nút Loa (`🔊`).
   - Hệ thống tự động lọc bỏ các ký tự Markdown (`#`, `*`, `` ` ``, `[1]`) và phát âm tự nhiên bằng giọng đọc tiếng Việt của hệ điều hành thông qua `window.speechSynthesis`.

---

### 4.6. Hệ thống Trắc nghiệm Tương tác RAG Quiz (Interactive RAG Quiz Engine)

Phân hệ Quiz trắc nghiệm được xây dựng hoàn chỉnh từ Backend đến Frontend:
1. **Backend (`RagQuizService.java` & `POST /api/ai/quiz`):**
   - Cho phép người dùng tùy chọn: Tài liệu cần ôn, Chủ đề trọng tâm, Số câu hỏi (3, 5, 10 câu) và Mức độ khó (Cơ bản, Vận dụng, Nâng cao).
   - Truy hồi các phân đoạn tài liệu tương ứng qua `DocumentChunkRepository` và `Bm25Retriever`.
   - Ép kiểu JSON Schema nghiêm ngặt (`QuizQuestionDto` gồm `question`, 4 `options`, `correctIndex`, `explanation`, `sourceReference`).
2. **Frontend Interactive Quiz Runner (`chat.html`):**
   - Giao diện làm bài trực tiếp với thẻ lựa chọn A, B, C, D.
   - **Chấm điểm & phản hồi tức thì (Instant Feedback):** Tô xanh đáp án đúng, tô đỏ đáp án sai, hiển thị giải thích chi tiết trích từ tài liệu gốc.
   - **Bảng tổng kết điểm số:** Chấm điểm theo thang 10, hiển thị tỷ lệ chính xác %, nhận xét sư phạm và bảng đối chiếu từng câu hỏi.

---

## 5. KỊCH BẢN KIỂM THỬ & HƯỚNG DẪN DEMO (DEMO SCENARIOS)

### Kịch bản 1: Demo "Trò chuyện trực tiếp (Chat ra ngoài)" & Hội thoại Đa lượt
1. Đăng nhập với tài khoản `student@gmail.com` / `123456` và mở mục **Trợ lý AI** (`/ai/chat`).
2. Chọn chế độ **[Trò chuyện]**.
3. Nhập câu hỏi: *"Giải thích khái niệm Dependency Injection trong Spring Boot kèm ví dụ ngắn gọn"*.
4. Sau khi AI trả lời, hỏi tiếp câu thứ 2: *"So sánh Constructor Injection và Field Injection trong ví dụ trên"* để kiểm chứng khả năng nhớ ngữ cảnh đa lượt.
5. Nhấn **F5 (Tải lại trang)** để kiểm chứng cuộc trò chuyện vẫn được lưu giữ nguyên vẹn trong danh sách lịch sử bên trái.

### Kịch bản 2: Demo "Tra cứu tài liệu (RAG)" trên toàn kho & Trích dẫn tương tác
1. Chuyển sang chế độ **[Tra cứu tài liệu]**, để dropdown ở mục **"Tất cả tài liệu trong kho"**.
2. Nhập câu hỏi: *"Cho tôi các tệp tài liệu liên quan tới việc marketing và tóm tắt chiến lược 4P"*.
3. **Kết quả:**
   - Hệ thống lọc chính xác tệp `Slide_Chien_luoc_Marketing_4P_Can_ban.pptx` (loại bỏ các tài liệu không liên quan như Giải thuật hay SQL).
   - Câu trả lời hiển thị rõ chiến lược 4P (Product, Price, Place, Promotion), công thức định giá, mô hình AIDA và ví dụ Vinamilk / Highlands Coffee kèm số hiệu trích dẫn **`[1]`**, **`[2]`**.
   - Nhấn vào số **`[1]`** hoặc thẻ nguồn bên dưới để xem đoạn trích gốc, mở hộp thoại **Chi tiết** tài liệu hoặc **Tải file** về máy.

### Kịch bản 3: Demo "Cài đặt API Key & Hoán chuyển Nền tảng (Groq Llama 3 ⇄ Gemini)"
1. Tại thanh công cụ chat, chọn nhanh mô hình trong danh sách (ví dụ chuyển từ **⚡ Llama 3.3 70B** sang **✨ Gemini 2.5 Flash**).
2. Huy hiệu nhà cung cấp và mô hình trên thanh tiêu đề cập nhật ngay lập tức, các lượt chat tiếp theo trong cùng cuộc trò chuyện sẽ do nền tảng mới đảm nhiệm.
3. Nếu muốn dùng API Key riêng, nhấn nút **"Cài đặt AI"** trên thanh công cụ để thay đổi.

### Kịch bản 4: Demo "Nhận diện Giọng nói Tiếng Việt & Đọc phản hồi"
1. Tại ô nhập tin nhắn, nhấn vào biểu tượng **Micro (`🎙️`)**.
2. Cho phép trình duyệt truy cập micro (nếu được hỏi) và nói bằng tiếng Việt: *"Tóm tắt các giải thuật tìm đường đi ngắn nhất"*.
3. Chữ sẽ chạy trực tiếp vào ô chat theo thời gian thực.
4. Sau khi AI trả lời, bấm biểu tượng **Loa (`🔊`)** trên bong bóng câu trả lời để nghe AI đọc to câu trả lời bằng giọng tiếng Việt.

### Kịch bản 5: Demo "Tạo & Làm bài Quiz Trắc nghiệm Tương tác từ Tài liệu"
1. Trên thanh công cụ, nhấn nút **"📝 Tạo Quiz ôn tập"** (hoặc mở hộp thoại chi tiết tài liệu rồi nhấn *"Tạo Quiz tài liệu này"*).
2. Chọn tài liệu: *Slide_Chien_luoc_Marketing_4P_Can_ban.pptx*, số lượng *5 câu*, mức độ *Vận dụng*, rồi nhấn **"Bắt đầu tạo bài Quiz"**.
3. **Làm bài trực tiếp:** Bấm chọn các đáp án A/B/C/D. Hệ thống sẽ chấm đúng/sai ngay lập tức, đổi màu xanh/đỏ và mở khung giải thích chi tiết trích từ slide gốc.
4. Bấm **"Câu tiếp theo"** đến hết bài để xem bảng điểm tổng kết (thang điểm 10, tỷ lệ % và nhận xét).

---
*Bản quyền dự án thuộc về Nhóm sinh viên môn HSF302 - Đại học FPT.*

