package com.studyhub.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả một bài Quiz luyện nhanh trong khung chat AI, gửi về để lưu vào lịch sử làm bài.
 */
public class PracticeResultDto {

    private Long documentId;
    private String documentTitle;
    private String quizTitle;
    private String topic;
    private String difficulty;
    private String provider;
    private String model;
    private List<Item> questions = new ArrayList<>();

    public static class Item {
        private String question;
        private List<String> options = new ArrayList<>();
        private int correctIndex;
        private Integer selectedIndex;
        private String explanation;
        private String sourceReference;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public List<String> getOptions() {
            return options;
        }

        public void setOptions(List<String> options) {
            this.options = options;
        }

        public int getCorrectIndex() {
            return correctIndex;
        }

        public void setCorrectIndex(int correctIndex) {
            this.correctIndex = correctIndex;
        }

        public Integer getSelectedIndex() {
            return selectedIndex;
        }

        public void setSelectedIndex(Integer selectedIndex) {
            this.selectedIndex = selectedIndex;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public String getSourceReference() {
            return sourceReference;
        }

        public void setSourceReference(String sourceReference) {
            this.sourceReference = sourceReference;
        }
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

    public String getQuizTitle() {
        return quizTitle;
    }

    public void setQuizTitle(String quizTitle) {
        this.quizTitle = quizTitle;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
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

    public List<Item> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Item> questions) {
        this.questions = questions;
    }
}
