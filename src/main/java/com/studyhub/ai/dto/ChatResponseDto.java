package com.studyhub.ai.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChatResponseDto {
    private boolean success;
    private String reply;
    private String provider;
    private String model;
    private String mode;
    private Long documentId;
    private String documentTitle;
    private Long conversationId;
    private String conversationTitle;
    private List<SourceChunkDto> sources = new ArrayList<>();
    private String errorMessage;
    private String timestamp;
    private Long assistantMessageId;

    public ChatResponseDto() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public static ChatResponseDto success(String reply, String provider, String model, String mode) {
        ChatResponseDto res = new ChatResponseDto();
        res.setSuccess(true);
        res.setReply(reply);
        res.setProvider(provider);
        res.setModel(model);
        res.setMode(mode);
        return res;
    }

    public static ChatResponseDto error(String errorMessage, String provider) {
        ChatResponseDto res = new ChatResponseDto();
        res.setSuccess(false);
        res.setErrorMessage(errorMessage);
        res.setReply(errorMessage);
        res.setProvider(provider);
        return res;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getConversationTitle() {
        return conversationTitle;
    }

    public void setConversationTitle(String conversationTitle) {
        this.conversationTitle = conversationTitle;
    }

    public List<SourceChunkDto> getSources() {
        return sources;
    }

    public void setSources(List<SourceChunkDto> sources) {
        this.sources = sources;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Long getAssistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(Long assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }
}
