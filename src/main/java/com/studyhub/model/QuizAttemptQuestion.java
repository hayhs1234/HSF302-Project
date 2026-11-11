package com.studyhub.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;

import java.util.Collections;
import java.util.List;

/**
 * Một câu hỏi trong lượt làm bài, lưu kèm đáp án đúng, lựa chọn của sinh viên và lời giải thích.
 * Đáp án đúng chỉ nằm trên server cho tới khi nộp bài (phòng thi thử).
 */
@Entity
@Table(name = "quiz_attempt_questions", indexes = {
        @Index(name = "idx_quiz_q_attempt", columnList = "attempt_id")
})
public class QuizAttemptQuestion {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] LETTERS = {"A", "B", "C", "D", "E", "F"};

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Nationalized
    @Column(name = "question_text", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String questionText;

    /** Danh sách phương án dạng JSON array */
    @Nationalized
    @Column(name = "options_json", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String optionsJson;

    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(name = "selected_index")
    private Integer selectedIndex;

    @Column(name = "is_correct")
    private Boolean correct;

    @Nationalized
    @Column(name = "explanation", columnDefinition = "NVARCHAR(MAX)")
    private String explanation;

    @Nationalized
    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    public QuizAttemptQuestion() {
    }

    public QuizAttemptQuestion(String questionText, List<String> options, int correctIndex,
                               String explanation, String sourceReference) {
        this.questionText = questionText;
        setOptions(options);
        this.correctIndex = correctIndex;
        this.explanation = explanation;
        this.sourceReference = sourceReference != null && sourceReference.length() > 500
                ? sourceReference.substring(0, 500) : sourceReference;
    }

    @Transient
    public List<String> getOptions() {
        if (optionsJson == null || optionsJson.isBlank()) return Collections.emptyList();
        try {
            return JSON.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void setOptions(List<String> options) {
        try {
            this.optionsJson = JSON.writeValueAsString(options != null ? options : List.of());
        } catch (Exception e) {
            this.optionsJson = "[]";
        }
    }

    public static String letterOf(Integer index) {
        if (index == null || index < 0 || index >= LETTERS.length) return "-";
        return LETTERS[index];
    }

    @Transient
    public String getCorrectLetter() {
        return letterOf(correctIndex);
    }

    @Transient
    public String getSelectedLetter() {
        return letterOf(selectedIndex);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public QuizAttempt getAttempt() {
        return attempt;
    }

    public void setAttempt(QuizAttempt attempt) {
        this.attempt = attempt;
    }

    public int getQuestionOrder() {
        return questionOrder;
    }

    public void setQuestionOrder(int questionOrder) {
        this.questionOrder = questionOrder;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
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

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
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
