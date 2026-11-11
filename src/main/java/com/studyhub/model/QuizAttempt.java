package com.studyhub.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Một lượt làm bài trắc nghiệm của sinh viên.
 * mode = EXAM (phòng thi thử có tính giờ, chấm trên server) hoặc PRACTICE (luyện nhanh trong khung chat).
 */
@Entity
@Table(name = "quiz_attempts", indexes = {
        @Index(name = "idx_quiz_attempt_user", columnList = "user_id, started_at")
})
public class QuizAttempt {

    public static final String MODE_EXAM = "EXAM";
    public static final String MODE_PRACTICE = "PRACTICE";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SUBMITTED = "SUBMITTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Nationalized
    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "document_id")
    private Long documentId;

    @Nationalized
    @Column(name = "document_title", length = 255)
    private String documentTitle;

    @Nationalized
    @Column(length = 255)
    private String topic;

    @Column(length = 10)
    private String difficulty = "MEDIUM";

    @Column(length = 10, nullable = false)
    private String mode = MODE_EXAM;

    @Column(length = 15, nullable = false)
    private String status = STATUS_IN_PROGRESS;

    @Column(name = "total_questions")
    private int totalQuestions;

    @Column(name = "correct_count")
    private int correctCount;

    /** Điểm thang 10 */
    @Column(name = "score")
    private Double score;

    /** Thời gian cho phép (giây); null = không giới hạn */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    /** Số lần rời khỏi tab trong lúc làm bài (giám sát thi) */
    @Column(name = "tab_switch_count")
    private Integer tabSwitchCount = 0;

    @Nationalized
    @Column(length = 60)
    private String provider;

    @Column(length = 80)
    private String model;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("questionOrder ASC")
    private List<QuizAttemptQuestion> questions = new ArrayList<>();

    public QuizAttempt() {
    }

    public void addQuestion(QuizAttemptQuestion q) {
        q.setAttempt(this);
        q.setQuestionOrder(questions.size() + 1);
        questions.add(q);
        totalQuestions = questions.size();
    }

    // ===== Thuộc tính tính toán dùng cho giao diện =====

    public boolean isSubmitted() {
        return STATUS_SUBMITTED.equals(status);
    }

    public boolean isExamMode() {
        return MODE_EXAM.equals(mode);
    }

    public LocalDateTime getDeadline() {
        return durationSeconds == null ? null : startedAt.plusSeconds(durationSeconds);
    }

    /** Số giây còn lại (>= 0); -1 nếu không giới hạn thời gian */
    public long getRemainingSeconds() {
        if (durationSeconds == null) return -1;
        long left = Duration.between(LocalDateTime.now(), getDeadline()).getSeconds();
        return Math.max(0, left);
    }

    public long getAnsweredCount() {
        return questions.stream().filter(q -> q.getSelectedIndex() != null).count();
    }

    public long getWrongCount() {
        return questions.stream().filter(q -> q.getSelectedIndex() != null && !Boolean.TRUE.equals(q.getCorrect())).count();
    }

    public long getBlankCount() {
        return questions.stream().filter(q -> q.getSelectedIndex() == null).count();
    }

    public int getScorePercent() {
        return totalQuestions == 0 ? 0 : (int) Math.round(correctCount * 100.0 / totalQuestions);
    }

    public String getFormattedScore() {
        return score == null ? "--" : String.format("%.1f", score);
    }

    public String getFormattedTimeSpent() {
        if (timeSpentSeconds == null) return "--";
        return String.format("%d phút %02d giây", timeSpentSeconds / 60, timeSpentSeconds % 60);
    }

    public String getFormattedStartedAt() {
        return startedAt == null ? "" : startedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public String getDifficultyLabel() {
        if (difficulty == null) return "Trung bình";
        return switch (difficulty.toUpperCase()) {
            case "EASY" -> "Dễ";
            case "HARD" -> "Khó";
            default -> "Trung bình";
        };
    }

    // ===== Getter / Setter =====

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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(Integer timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public Integer getTabSwitchCount() {
        return tabSwitchCount;
    }

    public void setTabSwitchCount(Integer tabSwitchCount) {
        this.tabSwitchCount = tabSwitchCount;
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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public List<QuizAttemptQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuizAttemptQuestion> questions) {
        this.questions = questions;
    }
}
