package com.studyhub.service;

import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.QuizQuestionDto;
import com.studyhub.ai.dto.QuizRequestDto;
import com.studyhub.ai.dto.QuizResponseDto;
import com.studyhub.ai.service.RagQuizService;
import com.studyhub.dto.PracticeResultDto;
import com.studyhub.model.QuizAttempt;
import com.studyhub.model.QuizAttemptQuestion;
import com.studyhub.repository.QuizAttemptQuestionRepository;
import com.studyhub.repository.QuizAttemptRepository;
import com.studyhub.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Nghiệp vụ Phòng thi thử: tạo đề bằng AI, lưu bài làm, chấm điểm trên server và thống kê kết quả học tập.
 */
@Service
public class QuizAttemptService {

    /** Cho phép nộp trễ vài giây do độ trễ mạng sau khi đồng hồ về 0 */
    private static final int SUBMIT_GRACE_SECONDS = 15;

    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptQuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final RagQuizService ragQuizService;

    public QuizAttemptService(QuizAttemptRepository attemptRepository,
                              QuizAttemptQuestionRepository questionRepository,
                              UserRepository userRepository,
                              RagQuizService ragQuizService) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.ragQuizService = ragQuizService;
    }

    /** Kết quả tạo đề: hoặc có bài thi, hoặc có thông báo lỗi để hiển thị */
    public record StartResult(QuizAttempt attempt, String errorMessage) {
        public boolean ok() {
            return attempt != null;
        }
    }

    /**
     * Sinh đề trắc nghiệm bằng AI từ tài liệu rồi mở một lượt thi có tính giờ.
     */
    @Transactional
    public StartResult startExam(Long userId, QuizRequestDto request, int durationMinutes, AiSessionConfig config) {
        QuizResponseDto quiz = ragQuizService.generateQuiz(request, userId, config);
        if (!quiz.isSuccess() || quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            return new StartResult(null, quiz.getErrorMessage() != null ? quiz.getErrorMessage() : "Không thể tạo đề thi.");
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setUser(userRepository.getReferenceById(userId));
        attempt.setMode(QuizAttempt.MODE_EXAM);
        attempt.setTitle(truncate(quiz.getQuizTitle(), 255));
        attempt.setDocumentId(quiz.getDocumentId());
        attempt.setDocumentTitle(truncate(quiz.getDocumentTitle(), 255));
        attempt.setTopic(truncate(request.getTopic(), 255));
        attempt.setDifficulty(request.getDifficulty());
        attempt.setProvider(quiz.getProvider());
        attempt.setModel(quiz.getModel());
        attempt.setDurationSeconds(Math.max(1, durationMinutes) * 60);
        attempt.setStartedAt(LocalDateTime.now());

        for (QuizQuestionDto q : quiz.getQuestions()) {
            attempt.addQuestion(new QuizAttemptQuestion(q.getQuestion(), q.getOptions(), q.getCorrectIndex(),
                    q.getExplanation(), q.getSourceReference()));
        }
        return new StartResult(attemptRepository.save(attempt), null);
    }

    /**
     * Tạo đề "Luyện lại câu sai" từ sổ tay câu sai, không cần gọi AI nên mở ngay lập tức.
     */
    @Transactional
    public StartResult startRetryWrong(Long userId, int maxQuestions, int durationMinutes) {
        List<QuizAttemptQuestion> wrong = questionRepository.findRecentWrong(userId, PageRequest.of(0, 60));
        Map<String, QuizAttemptQuestion> unique = new LinkedHashMap<>();
        for (QuizAttemptQuestion q : wrong) {
            unique.putIfAbsent(q.getQuestionText().trim().toLowerCase(), q);
            if (unique.size() >= maxQuestions) break;
        }
        if (unique.isEmpty()) {
            return new StartResult(null, "Bạn chưa có câu trả lời sai nào để luyện lại. Hãy làm thử một bài thi trước nhé!");
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setUser(userRepository.getReferenceById(userId));
        attempt.setMode(QuizAttempt.MODE_EXAM);
        attempt.setTitle("Luyện lại " + unique.size() + " câu đã làm sai");
        Set<String> sourceDocs = new LinkedHashSet<>();
        Set<Long> sourceDocIds = new HashSet<>();
        for (QuizAttemptQuestion src : unique.values()) {
            sourceDocs.add(Objects.toString(src.getAttempt().getDocumentTitle(), "Tổng hợp"));
            sourceDocIds.add(src.getAttempt().getDocumentId());
        }
        attempt.setDocumentTitle(sourceDocs.size() == 1 ? sourceDocs.iterator().next() : "Nhiều tài liệu");
        attempt.setDocumentId(sourceDocIds.size() == 1 ? sourceDocIds.iterator().next() : null);
        attempt.setDifficulty("MEDIUM");
        attempt.setProvider("StudyHub");
        attempt.setDurationSeconds(Math.max(1, durationMinutes) * 60);
        attempt.setStartedAt(LocalDateTime.now());
        for (QuizAttemptQuestion src : unique.values()) {
            attempt.addQuestion(new QuizAttemptQuestion(src.getQuestionText(), src.getOptions(), src.getCorrectIndex(),
                    src.getExplanation(), src.getSourceReference()));
        }
        return new StartResult(attemptRepository.save(attempt), null);
    }

    @Transactional(readOnly = true)
    public Optional<QuizAttempt> getAttempt(Long attemptId, Long userId) {
        return attemptRepository.findWithQuestions(attemptId, userId);
    }

    /**
     * Lưu tạm lựa chọn của một câu (tự động lưu khi click) để tải lại trang vẫn giữ bài làm.
     */
    @Transactional
    public boolean saveAnswer(Long attemptId, Long userId, int questionOrder, Integer selectedIndex) {
        Optional<QuizAttemptQuestion> opt = questionRepository.findForAnswer(attemptId, questionOrder, userId);
        if (opt.isEmpty()) return false;
        QuizAttemptQuestion q = opt.get();
        QuizAttempt attempt = q.getAttempt();
        if (attempt.isSubmitted() || isPastDeadline(attempt)) return false;
        if (selectedIndex != null && (selectedIndex < 0 || selectedIndex >= q.getOptions().size())) return false;
        q.setSelectedIndex(selectedIndex);
        questionRepository.save(q);
        return true;
    }

    /**
     * Nộp bài và chấm điểm trên server. answers: thứ tự câu -> chỉ số phương án đã chọn.
     * Nếu đã quá hạn nộp, chỉ chấm các lựa chọn đã được tự động lưu trước hạn.
     */
    @Transactional
    public Optional<QuizAttempt> submit(Long attemptId, Long userId, Map<Integer, Integer> answers, Integer tabSwitches) {
        Optional<QuizAttempt> opt = attemptRepository.findWithQuestions(attemptId, userId);
        if (opt.isEmpty()) return Optional.empty();
        QuizAttempt attempt = opt.get();
        if (attempt.isSubmitted()) return Optional.of(attempt);

        boolean late = isPastDeadline(attempt);
        for (QuizAttemptQuestion q : attempt.getQuestions()) {
            if (!late && answers != null && answers.containsKey(q.getQuestionOrder())) {
                Integer sel = answers.get(q.getQuestionOrder());
                q.setSelectedIndex(sel != null && sel >= 0 && sel < q.getOptions().size() ? sel : null);
            }
        }
        if (tabSwitches != null && tabSwitches >= 0) {
            attempt.setTabSwitchCount(tabSwitches);
        }
        grade(attempt);

        long spent = Duration.between(attempt.getStartedAt(), LocalDateTime.now()).getSeconds();
        if (attempt.getDurationSeconds() != null) {
            spent = Math.min(spent, attempt.getDurationSeconds());
        }
        attempt.setTimeSpentSeconds((int) Math.max(0, spent));
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setStatus(QuizAttempt.STATUS_SUBMITTED);
        return Optional.of(attemptRepository.save(attempt));
    }

    /**
     * Lưu kết quả bài Quiz luyện nhanh trong khung chat (mode PRACTICE).
     */
    @Transactional
    public QuizAttempt savePractice(Long userId, PracticeResultDto dto) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setUser(userRepository.getReferenceById(userId));
        attempt.setMode(QuizAttempt.MODE_PRACTICE);
        attempt.setTitle(truncate(dto.getQuizTitle() != null ? dto.getQuizTitle() : "Quiz luyện nhanh", 255));
        attempt.setDocumentId(dto.getDocumentId());
        attempt.setDocumentTitle(truncate(dto.getDocumentTitle(), 255));
        attempt.setTopic(truncate(dto.getTopic(), 255));
        attempt.setDifficulty(dto.getDifficulty() != null ? dto.getDifficulty() : "MEDIUM");
        attempt.setProvider(dto.getProvider());
        attempt.setModel(dto.getModel());
        attempt.setStartedAt(LocalDateTime.now());
        if (dto.getQuestions() != null) {
            for (PracticeResultDto.Item item : dto.getQuestions()) {
                if (item.getQuestion() == null || item.getOptions() == null || item.getOptions().size() < 2) continue;
                int correct = Math.max(0, Math.min(item.getCorrectIndex(), item.getOptions().size() - 1));
                QuizAttemptQuestion q = new QuizAttemptQuestion(item.getQuestion(), item.getOptions(), correct,
                        item.getExplanation(), item.getSourceReference());
                q.setSelectedIndex(item.getSelectedIndex());
                attempt.addQuestion(q);
            }
        }
        grade(attempt);
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setStatus(QuizAttempt.STATUS_SUBMITTED);
        return attemptRepository.save(attempt);
    }

    @Transactional(readOnly = true)
    public List<QuizAttempt> getHistory(Long userId) {
        return attemptRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    /**
     * Tổng hợp số liệu cho trang Kết quả & Thống kê.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        List<QuizAttempt> submitted = attemptRepository.findSubmittedChronological(userId);

        int totalQuestions = submitted.stream().mapToInt(QuizAttempt::getTotalQuestions).sum();
        int totalCorrect = submitted.stream().mapToInt(QuizAttempt::getCorrectCount).sum();
        double avgScore = submitted.stream().filter(a -> a.getScore() != null).mapToDouble(QuizAttempt::getScore).average().orElse(0);
        double bestScore = submitted.stream().filter(a -> a.getScore() != null).mapToDouble(QuizAttempt::getScore).max().orElse(0);

        stats.put("attemptCount", submitted.size());
        stats.put("examCount", submitted.stream().filter(QuizAttempt::isExamMode).count());
        stats.put("totalQuestions", totalQuestions);
        stats.put("totalCorrect", totalCorrect);
        stats.put("accuracy", totalQuestions == 0 ? 0 : Math.round(totalCorrect * 100.0 / totalQuestions));
        stats.put("avgScore", String.format("%.1f", avgScore));
        stats.put("bestScore", String.format("%.1f", bestScore));
        stats.put("wrongCount", questionRepository.countWrong(userId));

        // Biểu đồ tiến bộ: 15 bài gần nhất
        List<QuizAttempt> recent = submitted.subList(Math.max(0, submitted.size() - 15), submitted.size());
        List<String> trendLabels = new ArrayList<>();
        List<Double> trendScores = new ArrayList<>();
        for (QuizAttempt a : recent) {
            trendLabels.add(a.getSubmittedAt() != null
                    ? a.getSubmittedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                    : "");
            trendScores.add(a.getScore() != null ? Math.round(a.getScore() * 10) / 10.0 : 0);
        }
        stats.put("trendLabels", trendLabels);
        stats.put("trendScores", trendScores);

        // Độ chính xác theo tài liệu (yếu nhất lên đầu)
        List<Map<String, Object>> byDoc = new ArrayList<>();
        for (Object[] row : attemptRepository.statsByDocument(userId)) {
            long questions = row[2] != null ? ((Number) row[2]).longValue() : 0;
            long correct = row[3] != null ? ((Number) row[3]).longValue() : 0;
            Map<String, Object> m = new HashMap<>();
            m.put("documentTitle", row[0]);
            m.put("attempts", ((Number) row[1]).longValue());
            m.put("questions", questions);
            m.put("correct", correct);
            m.put("wrong", questions - correct);
            m.put("accuracy", questions == 0 ? 0L : Math.round(correct * 100.0 / questions));
            m.put("avgScore", row[4] != null ? String.format("%.1f", ((Number) row[4]).doubleValue()) : "--");
            byDoc.add(m);
        }
        byDoc.sort(Comparator.comparingLong(m -> (Long) m.get("accuracy")));
        stats.put("byDocument", byDoc);

        // Độ chính xác theo mức độ khó
        Map<String, Long> byDifficulty = new LinkedHashMap<>();
        byDifficulty.put("EASY", -1L);
        byDifficulty.put("MEDIUM", -1L);
        byDifficulty.put("HARD", -1L);
        for (Object[] row : attemptRepository.statsByDifficulty(userId)) {
            String diff = row[0] != null ? row[0].toString().toUpperCase() : "MEDIUM";
            long questions = row[1] != null ? ((Number) row[1]).longValue() : 0;
            long correct = row[2] != null ? ((Number) row[2]).longValue() : 0;
            if (questions > 0) {
                byDifficulty.put(diff, Math.round(correct * 100.0 / questions));
            }
        }
        stats.put("byDifficulty", byDifficulty);

        stats.put("frequentWrong", getFrequentWrong(userId, 12));
        return stats;
    }

    /**
     * Câu hay sai: gộp các lần sai của cùng một câu hỏi, sắp theo số lần sai giảm dần rồi tới mức độ gần đây.
     * Mỗi phần tử: {question: QuizAttemptQuestion (lần sai gần nhất), wrongTimes: số lần sai}
     */
    private List<Map<String, Object>> getFrequentWrong(Long userId, int limit) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (QuizAttemptQuestion q : questionRepository.findRecentWrong(userId, PageRequest.of(0, 200))) {
            String key = q.getQuestionText().trim().toLowerCase();
            Map<String, Object> entry = grouped.computeIfAbsent(key, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("question", q); // danh sách đã sắp mới nhất trước
                m.put("wrongTimes", 0);
                return m;
            });
            entry.put("wrongTimes", (Integer) entry.get("wrongTimes") + 1);
        }
        List<Map<String, Object>> result = new ArrayList<>(grouped.values());
        result.sort((a, b) -> Integer.compare((Integer) b.get("wrongTimes"), (Integer) a.get("wrongTimes")));
        return result.subList(0, Math.min(limit, result.size()));
    }

    private void grade(QuizAttempt attempt) {
        int correct = 0;
        for (QuizAttemptQuestion q : attempt.getQuestions()) {
            boolean ok = q.getSelectedIndex() != null && q.getSelectedIndex() == q.getCorrectIndex();
            q.setCorrect(q.getSelectedIndex() == null ? null : ok);
            if (ok) correct++;
        }
        attempt.setTotalQuestions(attempt.getQuestions().size());
        attempt.setCorrectCount(correct);
        double score = attempt.getTotalQuestions() == 0 ? 0 : correct * 10.0 / attempt.getTotalQuestions();
        attempt.setScore(Math.round(score * 10) / 10.0);
    }

    private boolean isPastDeadline(QuizAttempt attempt) {
        return attempt.getDeadline() != null
                && LocalDateTime.now().isAfter(attempt.getDeadline().plusSeconds(SUBMIT_GRACE_SECONDS));
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
