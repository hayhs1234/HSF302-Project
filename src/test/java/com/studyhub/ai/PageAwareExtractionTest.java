package com.studyhub.ai;

import com.studyhub.ai.service.DocumentIndexingService;
import com.studyhub.ai.service.DocumentTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra trích xuất văn bản theo từng trang PDF để trích dẫn [n] mở đúng trang.
 */
class PageAwareExtractionTest {

    @TempDir
    Path tempDir;

    @Test
    void extractPages_pdf_keepsPageNumbersAndSkipsBlankPages() throws Exception {
        File pdf = tempDir.resolve("sample.pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            addPage(doc, "Chapter one introduces relational databases");
            doc.addPage(new PDPage()); // trang trắng
            addPage(doc, "Chapter three explains SQL JOIN operations");
            doc.save(pdf);
        }

        List<DocumentTextExtractor.PageText> pages = new DocumentTextExtractor().extractPages(pdf, "PDF");

        assertEquals(2, pages.size(), "Trang trắng phải bị bỏ qua");
        assertEquals(1, pages.get(0).pageNumber());
        assertTrue(pages.get(0).text().contains("relational databases"));
        assertEquals(3, pages.get(1).pageNumber());
        assertTrue(pages.get(1).text().contains("SQL JOIN"));
    }

    @Test
    void extractPages_plainText_hasNoPageNumber() throws Exception {
        File txt = tempDir.resolve("notes.txt").toFile();
        java.nio.file.Files.writeString(txt.toPath(), "Ghi chú ôn tập môn Cơ sở dữ liệu");

        List<DocumentTextExtractor.PageText> pages = new DocumentTextExtractor().extractPages(txt, "txt");

        assertEquals(1, pages.size());
        assertNull(pages.get(0).pageNumber());
    }

    @Test
    void longPage_isSplitIntoSeveralChunks() {
        String longText = "Câu văn mẫu về chuẩn hóa dữ liệu. ".repeat(80);
        List<String> chunks = DocumentIndexingService.splitIntoChunks(longText,
                DocumentIndexingService.DEFAULT_CHUNK_SIZE, DocumentIndexingService.DEFAULT_CHUNK_OVERLAP);
        assertTrue(chunks.size() > 1);
        chunks.forEach(c -> assertTrue(c.length() <= DocumentIndexingService.DEFAULT_CHUNK_SIZE));
    }

    private void addPage(PDDocument doc, String text) throws Exception {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(72, 700);
            cs.showText(text);
            cs.endText();
        }
    }
}
