package com.studyhub.ai;

import com.studyhub.ai.dto.QuizQuestionDto;
import com.studyhub.ai.service.Bm25Retriever;
import com.studyhub.ai.service.DocumentIndexingService;
import com.studyhub.ai.service.RagQuizService;
import com.studyhub.model.Document;
import com.studyhub.model.DocumentChunk;
import com.studyhub.model.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagPipelineTest {

    private Bm25Retriever bm25Retriever;
    private RagQuizService ragQuizService;

    @BeforeEach
    void setUp() {
        bm25Retriever = new Bm25Retriever();
        ragQuizService = new RagQuizService(null, null, bm25Retriever, null);
    }

    @Test
    void testVietnameseAsciiNormalizationAndStopwords() {
        String normalized = Bm25Retriever.normalizeAscii("Chiến lược Marketing 4P Căn bản & Định giá Sản phẩm");
        assertEquals("chien luoc marketing 4p can ban dinh gia san pham", normalized);

        List<String> queryTokens = Bm25Retriever.extractQueryTokens("cho tôi các tệp tài liệu liên quan tới việc marketing");
        assertTrue(queryTokens.contains("marketing"), "Phải giữ lại từ khóa chính 'marketing'");
        assertFalse(queryTokens.contains("cho"), "Phải loại bỏ hư từ 'cho'");
        assertFalse(queryTokens.contains("toi"), "Phải loại bỏ hư từ 'tôi'");
        assertFalse(queryTokens.contains("tai"), "Phải loại bỏ hư từ 'tài liệu'");
        assertFalse(queryTokens.contains("lieu"), "Phải loại bỏ hư từ 'tài liệu'");
    }

    @Test
    void testChunkSplittingPreservesContentAndOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            sb.append("Đoạn văn thứ ").append(i)
              .append(": Phân tích chiến lược Marketing Mix 4P gồm Product, Price, Place và Promotion trong doanh nghiệp.\n\n");
        }
        List<String> chunks = DocumentIndexingService.splitIntoChunks(
                sb.toString(),
                DocumentIndexingService.DEFAULT_CHUNK_SIZE,
                DocumentIndexingService.DEFAULT_CHUNK_OVERLAP
        );
        assertTrue(chunks.size() >= 2, "Văn bản dài phải được chia thành nhiều đoạn (chunks)");
        for (String chunk : chunks) {
            assertFalse(chunk.isBlank());
            assertTrue(chunk.contains("Marketing Mix 4P"));
        }
    }

    @Test
    void testBm25RanksMarketingDocFirstAndFiltersIrrelevantDocs() {
        Subject mktSub = new Subject("MKT101", "Marketing căn bản", "", "#fd7e14", "bi-megaphone");
        Document mktDoc = new Document(
                "Slide_Chien_luoc_Marketing_4P_Can_ban.pptx",
                "Slide_Chien_luoc_Marketing_4P_Can_ban.pptx",
                "PPTX", "6.2 MB", 6500000L,
                "Slide thuyết trình", "ThS. Hoàng Thùy Linh",
                "Bộ slide bài giảng chi tiết về phối thức tiếp thị 4P (Product, Price, Place, Promotion).",
                "#Marketing #Marketing4P #ChienLuoc",
                false, 100, 200, mktSub
        );
        mktDoc.setId(1L);

        Subject algoSub = new Subject("CTDL-GT", "Cấu trúc dữ liệu & Giải thuật", "", "#0d6efd", "bi-diagram-3");
        Document algoDoc = new Document(
                "Giao_trinh_Giai_thuat_Nang_cao.pdf",
                "Giao_trinh_Giai_thuat_Nang_cao.pdf",
                "PDF", "14.2 MB", 14800000L,
                "Giáo trình & Bài giảng", "PGS. TS. Trần Hùng",
                "Giáo trình chuyên sâu bao gồm cây cân bằng AVL, Red-Black, thuật toán Dijkstra và quy hoạch động.",
                "#ThuatToan #DoThi #QuyHoachDong",
                true, 200, 400, algoSub
        );
        algoDoc.setId(2L);

        String mktContent = "Tài liệu: Slide_Chien_luoc_Marketing_4P_Can_ban.pptx | Môn học: Marketing căn bản\n"
                + "[Slide 1] Tổng quan Marketing và Phối thức Tiếp thị 4P: Product (Sản phẩm), Price (Giá cả), Place (Phân phối), Promotion (Xúc tiến thương mại).";
        DocumentChunk mktChunk = new DocumentChunk(mktDoc, 1, mktContent, 32);

        String algoContent = "Tài liệu: Giao_trinh_Giai_thuat_Nang_cao.pdf | Môn học: Cấu trúc dữ liệu & Giải thuật\n"
                + "Chương 1: Cây tìm kiếm nhị phân cân bằng AVL và thuật toán tìm đường đi ngắn nhất Dijkstra trên đồ thị trọng số không âm.";
        DocumentChunk algoChunk = new DocumentChunk(algoDoc, 1, algoContent, 34);

        List<Bm25Retriever.ScoredChunk> results = bm25Retriever.retrieveTopK(
                "cho tôi các tệp tài liệu liên quan tới việc marketing",
                List.of(mktChunk, algoChunk),
                5
        );

        assertFalse(results.isEmpty(), "Phải tìm thấy đoạn trích tài liệu Marketing");
        assertEquals(1L, results.get(0).getChunk().getDocument().getId(), "Tài liệu Marketing 4P phải xếp hạng #1");
        assertEquals(1, results.size(), "Tài liệu Giải thuật không liên quan phải bị loại bỏ bởi ngưỡng điểm BM25");
    }

    @Test
    void testBm25ReturnsEmptyWhenNoRelevantDocumentExists() {
        Subject sqlSub = new Subject("CSDL", "Cơ sở dữ liệu", "", "#198754", "bi-database");
        Document sqlDoc = new Document(
                "Slide_Chuong_4_Truy_van_SQL.pptx",
                "Slide_Chuong_4_Truy_van_SQL.pptx",
                "PPTX", "8.5 MB", 8900000L,
                "Slide thuyết trình", "TS. Lê Thị Mai",
                "Các kỹ thuật JOIN nâng cao, truy vấn lồng Subquery và Window Functions trong SQL Server.",
                "#SQL #Database",
                false, 100, 200, sqlSub
        );
        sqlDoc.setId(3L);

        String sqlContent = "Tài liệu: Slide_Chuong_4_Truy_van_SQL.pptx\nINNER JOIN, LEFT JOIN, ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC).";
        DocumentChunk sqlChunk = new DocumentChunk(sqlDoc, 1, sqlContent, 20);

        List<Bm25Retriever.ScoredChunk> results = bm25Retriever.retrieveTopK(
                "cách nấu món phở bò truyền thống Hà Nội",
                List.of(sqlChunk),
                5
        );

        assertTrue(results.isEmpty(), "Truy vấn hoàn toàn ngoài phạm vi kho tài liệu phải trả về danh sách rỗng");
    }

    @Test
    void testQuizJsonParsingHandlesMarkdownBlocksAndValidatesQuestions() {
        String mockAiResponse = """
                Dưới đây là bộ câu hỏi trắc nghiệm ôn tập cho tài liệu:
                ```json
                [
                  {
                    "question": "Khái niệm 4P trong Marketing căn bản đại diện cho 4 thành tố nào?",
                    "options": [
                      "Product, Price, Place, Promotion",
                      "People, Process, Philosophy, Profit",
                      "Position, Payment, Plan, Package",
                      "Public, Policy, Pricing, Partnership"
                    ],
                    "correctIndex": 0,
                    "explanation": "Chiến lược 4P gồm Product (Sản phẩm), Price (Giá), Place (Phân phối) và Promotion (Xúc tiến).",
                    "sourceReference": "Slide Marketing 4P"
                  },
                  {
                    "question": "Trong SQL Server, mệnh đề nào được dùng để phân nhóm dòng dữ liệu?",
                    "options": [
                      "ORDER BY",
                      "GROUP BY",
                      "WHERE",
                      "HAVING"
                    ],
                    "correctIndex": 1,
                    "explanation": "GROUP BY dùng để gom nhóm các hàng có cùng giá trị trong các cột chỉ định.",
                    "sourceReference": "Slide CSDL Chương 4"
                  }
                ]
                ```
                Chúc bạn ôn tập tốt!
                """;

        List<QuizQuestionDto> questions = ragQuizService.parseQuizJson(mockAiResponse);
        assertEquals(2, questions.size(), "Phải parse thành công đúng 2 câu hỏi từ khối markdown json");

        QuizQuestionDto q1 = questions.get(0);
        assertEquals("Khái niệm 4P trong Marketing căn bản đại diện cho 4 thành tố nào?", q1.getQuestion());
        assertEquals(4, q1.getOptions().size());
        assertEquals("Product, Price, Place, Promotion", q1.getOptions().get(0));
        assertEquals(0, q1.getCorrectIndex());
        assertEquals("Slide Marketing 4P", q1.getSourceReference());

        QuizQuestionDto q2 = questions.get(1);
        assertEquals(1, q2.getCorrectIndex());
        assertEquals("GROUP BY", q2.getOptions().get(1));
    }

    @Test
    void testQuizJsonParsingHandlesMalformedInputGracefully() {
        List<QuizQuestionDto> empty1 = ragQuizService.parseQuizJson("");
        assertTrue(empty1.isEmpty());

        List<QuizQuestionDto> empty2 = ragQuizService.parseQuizJson("Xin chào, tôi không thể tạo câu hỏi.");
        assertTrue(empty2.isEmpty());
    }
}
