package com.studyhub.ai.service;

import com.studyhub.model.Document;
import com.studyhub.model.DocumentChunk;
import com.studyhub.repository.DocumentChunkRepository;
import com.studyhub.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIndexingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    public static final int DEFAULT_CHUNK_SIZE = 750;
    public static final int DEFAULT_CHUNK_OVERLAP = 120;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentTextExtractor documentTextExtractor;

    @Value("${studyhub.upload.dir:./uploads/}")
    private String uploadDir;

    public DocumentIndexingService(DocumentRepository documentRepository,
                                   DocumentChunkRepository documentChunkRepository,
                                   DocumentTextExtractor documentTextExtractor) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentTextExtractor = documentTextExtractor;
    }

    /**
     * Lập chỉ mục (Trích xuất văn bản -> Chia đoạn -> Lưu vào bảng document_chunks) cho 1 tài liệu
     */
    @Transactional
    public int indexDocument(Document doc) {
        if (doc == null || doc.getId() == null) {
            return 0;
        }

        try {
            documentChunkRepository.deleteByDocumentId(doc.getId());

            if (!documentTextExtractor.isSupportedExtension(doc.getFileExtension())) {
                doc.setIndexStatus("NO_CONTENT");
                doc.setIndexedAt(LocalDateTime.now());
                documentRepository.save(doc);
                return 0;
            }

            String extractedText = "";
            File physicalFile = resolvePhysicalFile(doc);
            if (physicalFile != null && physicalFile.exists()) {
                extractedText = documentTextExtractor.extractText(physicalFile, doc.getFileExtension());
            }

            // Nếu tài liệu không có file vật lý nhưng có mô tả chi tiết, dùng mô tả làm nội dung tối thiểu
            if ((extractedText == null || extractedText.isBlank())
                    && doc.getDescription() != null && doc.getDescription().trim().length() >= 30) {
                extractedText = doc.getDescription().trim();
            }

            if (extractedText == null || extractedText.isBlank()) {
                doc.setIndexStatus("NO_CONTENT");
                doc.setIndexedAt(LocalDateTime.now());
                documentRepository.save(doc);
                return 0;
            }

            List<String> textChunks = splitIntoChunks(extractedText, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
            if (textChunks.isEmpty()) {
                doc.setIndexStatus("NO_CONTENT");
                doc.setIndexedAt(LocalDateTime.now());
                documentRepository.save(doc);
                return 0;
            }

            List<DocumentChunk> entities = new ArrayList<>(textChunks.size());
            int idx = 1;
            for (String chunkText : textChunks) {
                int tokenCount = countApproxTokens(chunkText);
                entities.add(new DocumentChunk(doc, idx++, chunkText, tokenCount));
            }
            documentChunkRepository.saveAll(entities);

            doc.setIndexStatus("INDEXED");
            doc.setIndexedAt(LocalDateTime.now());
            documentRepository.save(doc);

            log.info("Indexed document ID={} '{}' -> {} chunks", doc.getId(), doc.getTitle(), entities.size());
            return entities.size();
        } catch (Exception e) {
            log.error("Lỗi lập chỉ mục tài liệu ID={} '{}': {}", doc.getId(), doc.getTitle(), e.getMessage(), e);
            doc.setIndexStatus("FAILED");
            doc.setIndexedAt(LocalDateTime.now());
            documentRepository.save(doc);
            return 0;
        }
    }

    @Transactional
    public void deleteDocumentChunks(Long documentId) {
        if (documentId != null) {
            documentChunkRepository.deleteByDocumentId(documentId);
        }
    }

    public boolean isDocumentIndexed(Long documentId) {
        if (documentId == null) {
            return false;
        }
        return documentChunkRepository.countByDocumentId(documentId) > 0;
    }

    @Transactional
    public int indexAllPendingDocuments() {
        List<Document> allDocs = documentRepository.findAll();
        int indexedDocs = 0;
        for (Document doc : allDocs) {
            long existingChunks = documentChunkRepository.countByDocumentId(doc.getId());
            boolean needsIndex = doc.getIndexStatus() == null
                    || "PENDING".equalsIgnoreCase(doc.getIndexStatus())
                    || ("INDEXED".equalsIgnoreCase(doc.getIndexStatus()) && existingChunks == 0);
            if (needsIndex) {
                int count = indexDocument(doc);
                if (count > 0) {
                    indexedDocs++;
                }
            }
        }
        return indexedDocs;
    }

    /**
     * Chia văn bản thành các đoạn (Chunks) ưu tiên cắt tại ranh giới đoạn văn (\n\n), dòng (\n) hoặc câu (. )
     */
    public static List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String cleaned = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        if (cleaned.length() <= chunkSize) {
            chunks.add(cleaned);
            return chunks;
        }

        int start = 0;
        int minBreakOffset = Math.max(chunkSize / 2, 200);

        while (start < cleaned.length()) {
            int end = Math.min(start + chunkSize, cleaned.length());

            if (end < cleaned.length()) {
                int paraBreak = cleaned.lastIndexOf("\n\n", end);
                if (paraBreak > start + minBreakOffset) {
                    end = paraBreak;
                } else {
                    int lineBreak = cleaned.lastIndexOf('\n', end);
                    if (lineBreak > start + minBreakOffset) {
                        end = lineBreak;
                    } else {
                        int sentenceBreak = cleaned.lastIndexOf(". ", end);
                        if (sentenceBreak > start + minBreakOffset) {
                            end = sentenceBreak + 1;
                        } else {
                            int spaceBreak = cleaned.lastIndexOf(' ', end);
                            if (spaceBreak > start + minBreakOffset) {
                                end = spaceBreak;
                            }
                        }
                    }
                }
            }

            String piece = cleaned.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }

            if (end >= cleaned.length()) {
                break;
            }
            int nextStart = Math.max(end - overlap, start + 1);
            // Căn chỉnh điểm bắt đầu của đoạn tiếp theo vào đầu từ/dòng để không cắt ngang chữ
            if (nextStart > start + 1 && nextStart < cleaned.length()) {
                int nextNewline = cleaned.indexOf('\n', nextStart);
                if (nextNewline != -1 && nextNewline - nextStart <= 60) {
                    nextStart = nextNewline + 1;
                } else {
                    int nextSpace = cleaned.indexOf(' ', nextStart);
                    if (nextSpace != -1 && nextSpace - nextStart <= 30) {
                        nextStart = nextSpace + 1;
                    }
                }
            }
            start = nextStart;
        }

        return chunks;
    }

    public File resolvePhysicalFile(Document doc) {
        try {
            if (doc.getFilePath() != null && !doc.getFilePath().isBlank()) {
                File f = new File(doc.getFilePath());
                if (f.exists() && f.isFile()) return f;

                Path p = Paths.get(uploadDir, doc.getFilePath());
                if (Files.exists(p) && Files.isRegularFile(p)) return p.toFile();
            }

            if (doc.getFileName() != null && !doc.getFileName().isBlank()) {
                Path p = Paths.get(uploadDir, doc.getFileName());
                if (Files.exists(p) && Files.isRegularFile(p)) return p.toFile();

                File uploadsFolder = new File("uploads");
                if (uploadsFolder.exists()) {
                    File directFile = new File(uploadsFolder, doc.getFileName());
                    if (directFile.exists() && directFile.isFile()) return directFile;
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tìm tệp vật lý cho tài liệu ID {}: {}", doc.getId(), e.getMessage());
        }
        return null;
    }

    private int countApproxTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}
