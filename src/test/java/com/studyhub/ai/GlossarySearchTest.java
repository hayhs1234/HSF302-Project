package com.studyhub.ai;

import com.studyhub.ai.service.Bm25Retriever;
import com.studyhub.model.Document;
import com.studyhub.model.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tìm kiếm theo khái niệm: "cơ sở dữ liệu" và "database" phải cho cùng kết quả.
 */
class GlossarySearchTest {

    private final Bm25Retriever retriever = new Bm25Retriever();

    private DocumentChunk chunk(long id, String title, String content) {
        Document d = new Document();
        d.setId(id);
        d.setTitle(title);
        return new DocumentChunk(d, 1, content, content.split("\\s+").length);
    }

    private final DocumentChunk englishDbChunk = chunk(1, "Slide_Chuong_4_Truy_van_SQL.pptx",
            "A relational database stores data in tables. Each table has a primary key and SQL is used to query the database.");
    private final DocumentChunk vietDbChunk = chunk(2, "De_cuong_CSDL.docx",
            "Cơ sở dữ liệu quan hệ lưu trữ thông tin trong các bảng, mỗi bảng có khóa chính.");
    private final DocumentChunk marketingChunk = chunk(3, "Slide_Marketing_4P.pptx",
            "Marketing mix 4P gồm Product, Price, Place và Promotion.");
    private final DocumentChunk algoChunk = chunk(4, "Giao_trinh_Giai_thuat.pdf",
            "Thuật toán Dijkstra tìm đường đi ngắn nhất trên đồ thị có trọng số không âm.");

    private final List<DocumentChunk> all = List.of(englishDbChunk, vietDbChunk, marketingChunk, algoChunk);

    @Test
    void vietnamesePhraseIsKeptAsOneConceptWithSynonyms() {
        List<List<String>> concepts = Bm25Retriever.extractQueryConcepts("cho tôi tài liệu về cơ sở dữ liệu");
        assertEquals(1, concepts.size(), "Chỉ còn đúng 1 khái niệm, không vỡ thành 'so', 'du'");
        assertTrue(concepts.get(0).contains("database"));
        assertTrue(concepts.get(0).contains("csdl"));
    }

    @Test
    void vietnameseQueryFindsEnglishAndVietnameseDatabaseDocs() {
        List<Long> ids = retriever.retrieveTopK("cho tôi tài liệu về cơ sở dữ liệu", all, 5)
                .stream().map(sc -> sc.getChunk().getDocument().getId()).toList();
        assertTrue(ids.contains(1L), "Phải thấy tài liệu tiếng Anh viết 'database'");
        assertTrue(ids.contains(2L), "Phải thấy tài liệu tiếng Việt viết 'cơ sở dữ liệu'");
        assertFalse(ids.contains(3L));
        assertFalse(ids.contains(4L));
    }

    @Test
    void englishAndVietnameseAndAbbreviationGiveSameDocuments() {
        List<Long> viet = idsFor("tài liệu cơ sở dữ liệu");
        List<Long> eng = idsFor("tài liệu database");
        List<Long> abbr = idsFor("tài liệu CSDL");
        assertEquals(viet.stream().sorted().toList(), eng.stream().sorted().toList());
        assertEquals(viet.stream().sorted().toList(), abbr.stream().sorted().toList());
    }

    @Test
    void englishQueryFindsVietnameseAlgorithmText() {
        List<Long> ids = idsFor("shortest path algorithm");
        assertEquals(List.of(4L), ids);
    }

    @Test
    void vietnameseWordAiIsNotTreatedAsArtificialIntelligence() {
        List<List<String>> concepts = Bm25Retriever.extractQueryConcepts("ai là người phát minh ra Dijkstra");
        assertTrue(concepts.stream().noneMatch(c -> c.contains("artificial intelligence")));
    }

    private List<Long> idsFor(String query) {
        return retriever.retrieveTopK(query, all, 5).stream().map(sc -> sc.getChunk().getDocument().getId()).toList();
    }
}
