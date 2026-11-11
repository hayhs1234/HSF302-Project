package com.studyhub.ai.dto;

import java.util.ArrayList;
import java.util.List;

public class QuizQuestionDto {
    private int id;
    private String question;
    private List<String> options = new ArrayList<>();
    private int correctIndex;
    private String explanation;
    private String sourceReference;

    public QuizQuestionDto() {
    }

    public QuizQuestionDto(int id, String question, List<String> options, int correctIndex, String explanation, String sourceReference) {
        this.id = id;
        this.question = question;
        this.options = options != null ? options : new ArrayList<>();
        this.correctIndex = correctIndex;
        this.explanation = explanation;
        this.sourceReference = sourceReference;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

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
