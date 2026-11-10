package com.studyhub.controller;

import com.studyhub.ai.config.AiProvider;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.*;
import com.studyhub.ai.service.ChatHistoryService;
import com.studyhub.ai.service.RagQuizService;
import com.studyhub.ai.service.RagService;
import com.studyhub.model.ChatConversation;
import com.studyhub.model.Document;
import com.studyhub.model.User;
import com.studyhub.repository.DocumentRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);
    private static final String SESSION_AI_CONFIG = "AI_SESSION_CONFIG";
    private static final int MAX_CONTEXT_MESSAGES = 10;

    private final RagService ragService;
    private final DocumentRepository documentRepository;
    private final ChatHistoryService chatHistoryService;
    private final RagQuizService ragQuizService;

    @Value("${studyhub.ai.gemini.api-key:}")
    private String defaultGeminiApiKey;

    @Value("${studyhub.ai.groq.api-key:}")
    private String defaultGroqApiKey;

    public AiChatController(RagService ragService,
                            DocumentRepository documentRepository,
                            ChatHistoryService chatHistoryService,
                            RagQuizService ragQuizService) {
        this.ragService = ragService;
        this.documentRepository = documentRepository;
        this.chatHistoryService = chatHistoryService;
        this.ragQuizService = ragQuizService;
    }

    @PostConstruct
    public void init() {
        String groq = resolveGroqKey();
        String gemini = resolveGeminiKey();
        log.info("[StudyHub AI] Khoi dong AI Service - GROQ_API_KEY: {}", 
                groq.isBlank() ? "CHUA CO" : "DA NHAN (***" + groq.substring(Math.max(0, groq.length() - 4)) + ")");
        log.info("[StudyHub AI] Khoi dong AI Service - GEMINI_API_KEY: {}", 
                gemini.isBlank() ? "CHUA CO" : "DA NHAN (***" + gemini.substring(Math.max(0, gemini.length() - 4)) + ")");
    }

    private String resolveGroqKey() {
        if (defaultGroqApiKey != null && !defaultGroqApiKey.isBlank()) {
            return defaultGroqApiKey.trim();
        }
        String env = System.getenv("GROQ_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty("GROQ_API_KEY");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        return "";
    }

    private String resolveGeminiKey() {
        if (defaultGeminiApiKey != null && !defaultGeminiApiKey.isBlank()) {
            return defaultGeminiApiKey.trim();
        }
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty("GEMINI_API_KEY");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        return "";
    }

    @GetMapping("/ai/chat")
    public String showChatPage(@RequestParam(name = "docId", required = false) Long docId,
                               @RequestParam(name = "convId", required = false) Long convId,
                               HttpSession session,
                               Model model) {
        User currentUser = getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/?loginRequired=true";
        }

        AiSessionConfig config = getOrCreateSessionConfig(session);
        List<Document> accessibleDocs = documentRepository.findAccessibleByUser(currentUser.getId());
        List<ChatConversation> conversations = chatHistoryService.getUserConversations(currentUser.getId());

        model.addAttribute("documents", accessibleDocs);
        model.addAttribute("conversations", conversations);
        model.addAttribute("aiConfig", config);
        model.addAttribute("providers", AiProvider.values());

        if (docId != null) {
            Optional<Document> selectedDoc = documentRepository.findAccessibleByIdAndUser(docId, currentUser.getId());
            selectedDoc.ifPresent(doc -> {
                model.addAttribute("selectedDoc", doc);
                model.addAttribute("selectedDocId", doc.getId());
            });
        }

        if (convId != null) {
            chatHistoryService.getConversationForUser(convId, currentUser.getId()).ifPresent(conv -> {
                model.addAttribute("activeConversationId", conv.getId());
                if (docId == null && conv.getDocumentId() != null) {
                    documentRepository.findAccessibleByIdAndUser(conv.getDocumentId(), currentUser.getId())
                            .ifPresent(doc -> {
                                model.addAttribute("selectedDoc", doc);
                                model.addAttribute("selectedDocId", doc.getId());
                            });
                }
            });
        } else if (docId == null && !conversations.isEmpty()) {
            model.addAttribute("activeConversationId", conversations.get(0).getId());
        }

        return "ai/chat";
    }

    @PostMapping("/api/ai/chat")
    @ResponseBody
    public ResponseEntity<ChatResponseDto> handleChat(@RequestBody ChatRequestDto request,
                                                      HttpSession session) {
        User currentUser = getCurrentUser(session);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ChatResponseDto.error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "Hệ thống"));
        }

        AiSessionConfig config = getOrCreateSessionConfig(session);

        if (!config.hasActiveKey()) {
            String gKey = resolveGroqKey();
            String gmKey = resolveGeminiKey();
            if (config.getProvider() == AiProvider.GROQ && !gKey.isBlank()) {
                config.setGroqApiKey(gKey);
            } else if (config.getProvider() == AiProvider.GEMINI && !gmKey.isBlank()) {
                config.setGeminiApiKey(gmKey);
            }
        }

        if (!config.hasActiveKey()) {
            return ResponseEntity.ok(ChatResponseDto.error(
                    "Dịch vụ AI " + config.getProvider().getDisplayName() + " hiện chưa sẵn sàng trên hệ thống. Vui lòng liên hệ quản trị viên.",
                    config.getProvider().getDisplayName()
            ));
        }

        List<ChatTurnDto> historyTurns = request.getConversationId() != null
                ? chatHistoryService.getRecentTurns(request.getConversationId(), MAX_CONTEXT_MESSAGES)
                : Collections.emptyList();

        ChatResponseDto response;
        if (request.isRagMode()) {
            response = ragService.askRag(currentUser.getId(), request.getDocumentId(), request.getMessage(), historyTurns, config);
        } else {
            response = ragService.askGeneral(request.getMessage(), historyTurns, config);
        }

        if (response.isSuccess()) {
            ChatConversation conv = chatHistoryService.getOrCreateConversation(
                    request.getConversationId(),
                    currentUser,
                    request.getMessage(),
                    response.getMode(),
                    response.getDocumentId(),
                    response.getDocumentTitle()
            );
            chatHistoryService.saveExchange(conv, request.getMessage(), response);
            response.setConversationId(conv.getId());
            response.setConversationTitle(conv.getTitle());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/ai/conversations")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listConversations(HttpSession session) {
        User currentUser = getCurrentUser(session);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        }
        List<ChatConversation> convs = chatHistoryService.getUserConversations(currentUser.getId());
        List<Map<String, Object>> items = new ArrayList<>(convs.size());
        for (ChatConversation c : convs) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("title", c.getTitle());
            m.put("mode", c.getMode());
            m.put("documentId", c.getDocumentId());
            m.put("documentTitle", c.getDocumentTitle());
            m.put("updatedAt", c.getFormattedTime());
            items.add(m);
        }
        return ResponseEntity.ok(Map.of("success", true, "conversations", items));
    }

    @GetMapping("/api/ai/conversations/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable Long id, HttpSession session) {
        User currentUser = getCurrentUser(session);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        }
        Optional<ChatConversation> opt = chatHistoryService.getConversationForUser(id, currentUser.getId());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Không tìm thấy cuộc trò chuyện."));
        }
        ChatConversation conv = opt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", conv.getId());
        result.put("title", conv.getTitle());
        result.put("mode", conv.getMode());
        result.put("documentId", conv.getDocumentId());
        result.put("documentTitle", conv.getDocumentTitle());
        result.put("messages", chatHistoryService.getMessagesForDisplay(conv.getId()));
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/api/ai/conversations/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteConversation(@PathVariable Long id, HttpSession session) {
        User currentUser = getCurrentUser(session);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        }
        boolean deleted = chatHistoryService.deleteConversation(id, currentUser.getId());
        return ResponseEntity.ok(Map.of("success", deleted));
    }

    @PatchMapping("/api/ai/conversations/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> renameConversation(@PathVariable Long id,
                                                                  @RequestBody Map<String, String> payload,
                                                                  HttpSession session) {
        User currentUser = getCurrentUser(session);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        }
        String newTitle = payload != null ? payload.get("title") : null;
        Optional<ChatConversation> updated = chatHistoryService.renameConversation(id, currentUser.getId(), newTitle);
        if (updated.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Không thể đổi tên cuộc trò chuyện."));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", updated.get().getId(),
                "title", updated.get().getTitle()
        ));
    }

    @PostMapping("/api/ai/config")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody AiConfigFormDto form,
                                                            HttpSession session) {
        AiSessionConfig config = getOrCreateSessionConfig(session);
        Map<String, Object> result = new HashMap<>();

        if (form.getProvider() != null && !form.getProvider().isBlank()) {
            try {
                config.setProvider(AiProvider.valueOf(form.getProvider().toUpperCase()));
            } catch (Exception e) {
                config.setProvider(AiProvider.GROQ);
            }
        }

        if (form.getGeminiModel() != null && !form.getGeminiModel().isBlank()) {
            config.setGeminiModel(form.getGeminiModel().trim());
        }

        if (form.getGroqModel() != null && !form.getGroqModel().isBlank()) {
            config.setGroqModel(form.getGroqModel().trim());
        }

        if (config.getGroqApiKey().isBlank()) {
            config.setGroqApiKey(resolveGroqKey());
        }
        if (config.getGeminiApiKey().isBlank()) {
            config.setGeminiApiKey(resolveGeminiKey());
        }

        session.setAttribute(SESSION_AI_CONFIG, config);

        result.put("success", true);
        result.put("message", "Đã cập nhật mô hình AI thành công!");
        result.put("provider", config.getProvider().name());
        result.put("providerName", config.getProvider().getDisplayName());
        result.put("hasKey", config.hasActiveKey());
        result.put("activeModel", config.getActiveModel());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/ai/config")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getConfig(HttpSession session) {
        AiSessionConfig config = getOrCreateSessionConfig(session);
        Map<String, Object> result = new HashMap<>();

        result.put("provider", config.getProvider().name());
        result.put("providerName", config.getProvider().getDisplayName());
        result.put("geminiModel", config.getGeminiModel());
        result.put("groqModel", config.getGroqModel());
        result.put("hasKey", config.hasActiveKey());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/ai/quiz")
    @ResponseBody
    public ResponseEntity<QuizResponseDto> generateQuiz(@RequestBody(required = false) QuizRequestDto request,
                                                        HttpSession session) {
        User currentUser = getCurrentUser(session);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(QuizResponseDto.error("Vui lòng đăng nhập để tạo bài trắc nghiệm ôn tập.", "Hệ thống"));
        }

        if (request == null) {
            request = new QuizRequestDto();
        }

        AiSessionConfig config = getOrCreateSessionConfig(session);
        QuizResponseDto response = ragQuizService.generateQuiz(request, currentUser.getId(), config);
        return ResponseEntity.ok(response);
    }

    private User getCurrentUser(HttpSession session) {
        if (session == null) return null;
        return (User) session.getAttribute("currentUser");
    }

    private AiSessionConfig getOrCreateSessionConfig(HttpSession session) {
        AiSessionConfig config = (AiSessionConfig) session.getAttribute(SESSION_AI_CONFIG);
        if (config == null) {
            config = new AiSessionConfig();
            session.setAttribute(SESSION_AI_CONFIG, config);
        }

        String sysGroq = resolveGroqKey();
        String sysGemini = resolveGeminiKey();
        if ((config.getGroqApiKey() == null || config.getGroqApiKey().isBlank()) && !sysGroq.isBlank()) {
            config.setGroqApiKey(sysGroq);
        }
        if ((config.getGeminiApiKey() == null || config.getGeminiApiKey().isBlank()) && !sysGemini.isBlank()) {
            config.setGeminiApiKey(sysGemini);
        }

        if (config.getProvider() == null) {
            config.setProvider(!sysGroq.isBlank() ? AiProvider.GROQ : AiProvider.GEMINI);
        }

        return config;
    }
}
