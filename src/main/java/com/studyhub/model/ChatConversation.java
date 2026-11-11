package com.studyhub.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "chat_conversations", indexes = {
        @Index(name = "idx_chat_conv_user", columnList = "user_id, updated_at")
})
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Nationalized
    @Column(nullable = false, length = 255)
    private String title = "Cuộc trò chuyện mới";

    @Column(length = 20)
    private String mode = "GENERAL";

    @Column(name = "document_id")
    private Long documentId;

    @Nationalized
    @Column(name = "document_title", length = 255)
    private String documentTitle;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ChatConversation() {
    }

    public ChatConversation(User user, String title, String mode, Long documentId, String documentTitle) {
        this.user = user;
        this.title = title;
        this.mode = mode != null ? mode : "GENERAL";
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFormattedTime() {
        LocalDateTime t = updatedAt != null ? updatedAt : createdAt;
        if (t == null) return "";
        return t.format(DateTimeFormatter.ofPattern("HH:mm dd/MM"));
    }
}
