package com.studyhub.controller;

import com.studyhub.ai.config.AiConfigResolver;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.QuizRequestDto;
import com.studyhub.ai.service.ChatHistoryService;
import com.studyhub.dto.PracticeResultDto;
import com.studyhub.model.QuizAttempt;
import com.studyhub.model.User;
import com.studyhub.repository.DocumentRepository;
import com.studyhub.service.QuizAttemptService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Phòng thi thử (Mock Exam Room): tạo đề bằng AI, làm bài có đồng hồ đếm ngược,
 * chấm điểm trên server, xem lại bài và thống kê kết quả học tập.
 */
@Controller
public class ExamController {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(5, 10, 15, 20, 30);

    private final QuizAttemptService quizAttemptService;
    private final DocumentRepository documentRepository;
    private final ChatHistoryService chatHistoryService;
    private final AiConfigResolver aiConfigResolver;

    public ExamController(QuizAttemptService quizAttemptService,
                          DocumentRepository documentRepository,
                          ChatHistoryService chatHistoryService,
                          AiConfigResolver aiConfigResolver) {
        this.quizAttemptService = quizAttemptService;
        this.documentRepository = documentRepository;
        this.chatHistoryService = chatHistoryService;
        this.aiConfigResolver = aiConfigResolver;
    }

    /** Sảnh phòng thi: chọn tài liệu, số câu, độ khó, thời gian */
    @GetMapping("/exam")
    public String examLobby(@RequestParam(name = "docId", required = false) Long docId,
                            HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) return "redirect:/?loginRequired=true";

        model.addAttribute("documents", documentRepository.findAccessibleByUser(user.getId()));
        model.addAttribute("selectedDocId", docId);
        model.addAttribute("recentAttempts", quizAttemptService.getHistory(user.getId()).stream().limit(5).toList());
        model.addAttribute("stats", quizAttemptService.getStatistics(user.getId()));
        return "exam/lobby";
    }

    @PostMapping("/exam/start")
    public String startExam(@RequestParam(required = false) Long documentId,
                            @RequestParam(required = false) String topic,
                            @RequestParam(defaultValue = "10") int questionCount,
                            @RequestParam(defaultValue = "MEDIUM") String difficulty,
                            @RequestParam(defaultValue = "10") int durationMinutes,
                            HttpSession session,
                            RedirectAttributes redirect) {
        User user = currentUser(session);
        if (user == null) return "redirect:/?loginRequired=true";

        AiSessionConfig config = aiConfigResolver.getOrCreateSessionConfig(session);
        QuizRequestDto request = new QuizRequestDto(documentId,
                topic != null ? topic.trim() : null,
                Math.max(3, Math.min(questionCount, 10)),
                Set.of("EASY", "MEDIUM", "HARD").contains(difficulty) ? difficulty : "MEDIUM");
        int duration = ALLOWED_DURATIONS.contains(durationMinutes) ? durationMinutes : 10;

        QuizAttemptService.StartResult result = quizAttemptService.startExam(user.getId(), request, duration, config);
        if (!result.ok()) {
            redirect.addFlashAttribute("errorMessage", result.errorMessage());
            return "redirect:/exam" + (documentId != null ? "?docId=" + documentId : "");
        }
        return "redirect:/exam/" + result.attempt().getId();
    }

    @PostMapping("/exam/retry-wrong")
    public String retryWrong(@RequestParam(defaultValue = "10") int questionCount,
                             HttpSession session, RedirectAttributes redirect) {
        User user = currentUser(session);
        if (user == null) return "redirect:/?loginRequired=true";

        int count = Math.max(3, Math.min(questionCount, 20));
        QuizAttemptService.StartResult result = quizAttemptService.startRetryWrong(user.getId(), count, Math.max(5, count));
        if (!result.ok()) {
            redirect.addFlashAttribute("errorMessage", result.errorMessage());
            return "redirect:/exam/history";
        }
        return "redirect:/exam/" + result.attempt().getId();
    }

    /** Phòng làm bài. Nếu đã nộp hoặc hết giờ thì chuyển sang trang kết quả. */
    @GetMapping("/exam/{id}")
    public String examRoom(@PathVariable Long id, HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) return "redirect:/?loginRequired=true";

        Optional<QuizAttempt> opt = quizAttemptService.getAttempt(id, user.getId());
        if (opt.isEmpty()) return "redirect:/exam";
        QuizAttempt attempt = opt.get();

        if (attempt.isSubmitted()) {
            return "redirect:/exam/" + id + "/result";
        }
        if (attempt.getRemainingSeconds() == 0) {
            // Hết giờ khi người dùng không ở trang: tự nộp với các đáp án đã tự động lưu
            quizAttemptService.submit(id, user.getId(), null, null);
            return "redirect:/exam/" + id + "/result";
        }

        model.addAttribute("attempt", attempt);
        return "exam/room";
    }

    /** Tự động lưu đáp án khi sinh viên chọn (chống mất bài khi tải lại trang) */
    @PostMapping("/api/exam/{id}/answer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> autosaveAnswer(@PathVariable Long id,
                                                              @RequestBody Map<String, Integer> payload,
                                                              HttpSession session) {
        User user = currentUser(session);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        Integer order = payload.get("order");
        if (order == null) return ResponseEntity.badRequest().body(Map.of("success", false));
        boolean ok = quizAttemptService.saveAnswer(id, user.getId(), order, payload.get("selected"));
        return ResponseEntity.ok(Map.of("success", ok));
    }

    @PostMapping("/exam/{id}/submit")
    public String submitExam(@PathVariable Long id,
                             @RequestParam Map<String, String> params,
                             HttpSession session) {
        User user = currentUser(session);
        if (user == null) return "redirect:/?loginRequired=true";

        Map<Integer, Integer> answers = new HashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("q_")) {
                try {
                    answers.put(Integer.parseInt(key.substring(2)), Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        Integer tabSwitches = null;
        try {
            tabSwitches = params.get("tabSwitches") != null ? Integer.parseInt(params.get("tabSwitches")) : null;
        } catch (NumberFormatException ignored) {
        }

        quizAttemptService.submit(id, user.getId(), answers, tabSwitches);
        return "redirect:/exam/" + id + "/result";
    }

    @GetMapping("/exam/{id}/result")
    public String examResult(@PathVariable Long id, HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) return "redirect:/?loginRequired=true";

        Optional<QuizAttempt> opt = quizAttemptService.getAttempt(id, user.getId());
        if (opt.isEmpty()) return "redirect:/exam/history";
        if (!opt.get().isSubmitted()) return "redirect:/exam/" + id;

        model.addAttribute("attempt", opt.get());
        return "exam/result";
    }

    /** Kết quả & thống kê: tiến bộ điểm số, tài liệu yếu, sổ tay câu sai, chất lượng câu trả lời AI */
    @GetMapping("/exam/history")
    public String history(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) return "redirect:/?loginRequired=true";

        model.addAttribute("attempts", quizAttemptService.getHistory(user.getId()));
        model.addAttribute("stats", quizAttemptService.getStatistics(user.getId()));
        model.addAttribute("feedbackStats", chatHistoryService.getFeedbackStats(user.getId()));
        return "exam/history";
    }

    /** Lưu kết quả Quiz luyện nhanh trong khung chat AI */
    @PostMapping("/api/quiz/practice-results")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> savePractice(@RequestBody PracticeResultDto dto, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
        QuizAttempt saved = quizAttemptService.savePractice(user.getId(), dto);
        return ResponseEntity.ok(Map.of("success", true, "attemptId", saved.getId()));
    }

    private User currentUser(HttpSession session) {
        return session == null ? null : (User) session.getAttribute("currentUser");
    }
}
