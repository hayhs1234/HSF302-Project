package com.studyhub.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_doc_chunks_doc_id", columnList = "document_id")
})
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Nationalized
    @Column(name = "content", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String content;

    @Column(name = "token_count")
    private int tokenCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public DocumentChunk() {
    }

    public DocumentChunk(Document document, int chunkIndex, String content, int tokenCount) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
