package com.studyhub;

import com.studyhub.model.Document;
import com.studyhub.repository.DocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class SearchVietnameseTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("Tìm kiếm tác giả có dấu: 'Trần Hùng'")
    void testSearchAuthorAccented() {
        List<Document> results = documentRepository.searchDocuments("Trần Hùng", null, null, false, Sort.unsorted());
        System.out.println(">>> Search 'Trần Hùng' found: " + results.size());
        assertFalse(results.isEmpty(), "Phải tìm thấy tài liệu của tác giả Trần Hùng!");
    }

    @Test
    @DisplayName("Tìm kiếm tác giả không dấu: 'tran hung'")
    void testSearchAuthorUnaccented() {
        List<Document> results = documentRepository.searchDocuments("tran hung", null, null, false, Sort.unsorted());
        System.out.println(">>> Search 'tran hung' found: " + results.size());
        assertFalse(results.isEmpty(), "Phải tìm thấy tài liệu khi gõ không dấu 'tran hung'!");
    }

    @Test
    @DisplayName("Tìm kiếm tác giả viết thường có dấu: 'trần hùng'")
    void testSearchAuthorLowercaseAccented() {
        List<Document> results = documentRepository.searchDocuments("trần hùng", null, null, false, Sort.unsorted());
        System.out.println(">>> Search 'trần hùng' found: " + results.size());
        assertFalse(results.isEmpty(), "Phải tìm thấy tài liệu khi gõ 'trần hùng'!");
    }

    @Test
    @DisplayName("Tìm kiếm theo tên môn học hoặc mã môn học: 'CTDL' hoặc 'Cấu trúc dữ liệu'")
    void testSearchBySubject() {
        List<Document> results = documentRepository.searchDocuments("CTDL", null, null, false, Sort.unsorted());
        System.out.println(">>> Search 'CTDL' found: " + results.size());
        assertFalse(results.isEmpty(), "Phải tìm thấy tài liệu thuộc môn CTDL!");
    }

    @Test
    @DisplayName("Tìm kiếm theo từ khóa mô tả thuật toán: 'Dijkstra'")
    void testSearchByDescription() {
        List<Document> results = documentRepository.searchDocuments("Dijkstra", null, null, false, Sort.unsorted());
        System.out.println(">>> Search 'Dijkstra' found: " + results.size());
        assertFalse(results.isEmpty(), "Phải tìm thấy tài liệu chứa thuật toán Dijkstra!");
    }
}
