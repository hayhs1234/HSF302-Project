package com.studyhub.ai.dto;

import java.util.ArrayList;
import java.util.List;

public class QuizResponseDto {
    private boolean success;
    private String quizTitle;
    private String documentTitle;
    private Long documentId;
    private String provider;
    private String model;
    private List<QuizQuestionDto> questions = new ArrayList<>();
    private String errorMessage;

    public QuizResponseDto() {
    }

    public static QuizResponseDto success(String quizTitle, String documentTitle, Long documentId,
                                          String provider, String model, List<QuizQuestionDto> questions) {
        QuizResponseDto dto = new QuizResponseDto();
        dto.setSuccess(true);
        dto.setQuizTitle(quizTitle);
        dto.setDocumentTitle(documentTitle);
        dto.setDocumentId(documentId);
        dto.setProvider(provider);
        dto.setModel(model);
        dto.setQuestions(questions != null ? questions : new ArrayList<>());
        return dto;
    }

    public static QuizResponseDto error(String errorMessage, String provider) {
        QuizResponseDto dto = new QuizResponseDto();
        dto.setSuccess(false);
        dto.setErrorMessage(errorMessage);
        dto.setProvider(provider);
        return dto;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getQuizTitle() {
        return quizTitle;
    }

    public void setQuizTitle(String quizTitle) {
        this.quizTitle = quizTitle;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
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

    public List<QuizQuestionDto> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuizQuestionDto> questions) {
        this.questions = questions;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
