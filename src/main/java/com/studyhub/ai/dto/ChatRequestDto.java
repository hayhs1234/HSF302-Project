package com.studyhub.ai.dto;

public class ChatRequestDto {
    private String message;
    private String mode = "GENERAL"; // "GENERAL" hoặc "RAG"
    private Long documentId;         // Có thể null (nếu tra cứu trên toàn bộ kho tài liệu hoặc trò chuyện tự do)
    private Long conversationId;     // ID cuộc hội thoại hiện tại (nếu có)

    public ChatRequestDto() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMode() {
        return mode != null ? mode.toUpperCase() : "GENERAL";
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

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public boolean isRagMode() {
        return "RAG".equalsIgnoreCase(this.mode);
    }
}
