package com.studyhub.ai.dto;

public class QuizRequestDto {
    private Long documentId;
    private String topic;
    private int questionCount = 5;
    private String difficulty = "MEDIUM";

    public QuizRequestDto() {
    }

    public QuizRequestDto(Long documentId, String topic, int questionCount, String difficulty) {
        this.documentId = documentId;
        this.topic = topic;
        this.questionCount = questionCount;
        this.difficulty = difficulty;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getQuestionCount() {
        return questionCount <= 0 ? 5 : questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public String getDifficulty() {
        return difficulty != null && !difficulty.isBlank() ? difficulty : "MEDIUM";
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
