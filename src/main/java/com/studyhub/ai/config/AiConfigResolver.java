package com.studyhub.ai.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lấy cấu hình AI của phiên đăng nhập và tự điền khóa API hệ thống (biến môi trường / application.properties).
 * Dùng chung cho Trợ lý AI và Phòng thi thử.
 */
@Component
public class AiConfigResolver {

    public static final String SESSION_AI_CONFIG = "AI_SESSION_CONFIG";

    @Value("${studyhub.ai.gemini.api-key:}")
    private String defaultGeminiApiKey;

    @Value("${studyhub.ai.groq.api-key:}")
    private String defaultGroqApiKey;

    public String resolveGroqKey() {
        return firstNonBlank(defaultGroqApiKey, System.getenv("GROQ_API_KEY"), System.getProperty("GROQ_API_KEY"));
    }

    public String resolveGeminiKey() {
        return firstNonBlank(defaultGeminiApiKey, System.getenv("GEMINI_API_KEY"), System.getProperty("GEMINI_API_KEY"));
    }

    public AiSessionConfig getOrCreateSessionConfig(HttpSession session) {
        AiSessionConfig config = (AiSessionConfig) session.getAttribute(SESSION_AI_CONFIG);
        if (config == null) {
            config = new AiSessionConfig();
            session.setAttribute(SESSION_AI_CONFIG, config);
        }

        String sysGroq = resolveGroqKey();
        String sysGemini = resolveGeminiKey();
        if (config.getGroqApiKey().isBlank() && !sysGroq.isBlank()) {
            config.setGroqApiKey(sysGroq);
        }
        if (config.getGeminiApiKey().isBlank() && !sysGemini.isBlank()) {
            config.setGeminiApiKey(sysGemini);
        }
        if (config.getProvider() == null) {
            config.setProvider(!sysGroq.isBlank() ? AiProvider.GROQ : AiProvider.GEMINI);
        }
        return config;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }
}
