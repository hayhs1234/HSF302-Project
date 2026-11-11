package com.studyhub.ai.service;

import com.studyhub.ai.config.AiProvider;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.ChatTurnDto;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Giao diện chung (Strategy Pattern) cho các nền tảng AI Chat (Gemini, Groq Llama 3)
 */
public interface AiChatService {

    AiProvider getProvider();

    default String generateResponse(String userMessage, String systemPrompt, AiSessionConfig config) throws Exception {
        return generateResponse(Collections.emptyList(), userMessage, systemPrompt, config);
    }

    String generateResponse(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt, AiSessionConfig config) throws Exception;

    /**
     * Sinh câu trả lời ở chế độ streaming: gọi onDelta cho từng mảnh văn bản mới, trả về toàn bộ văn bản khi xong.
     * Mặc định (nhà cung cấp chưa hỗ trợ streaming) thì trả về cả câu trả lời trong một lần.
     */
    default String streamResponse(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt,
                                  AiSessionConfig config, Consumer<String> onDelta) throws Exception {
        String full = generateResponse(historyTurns, userMessage, systemPrompt, config);
        if (onDelta != null && full != null) {
            onDelta.accept(full);
        }
        return full;
    }

    boolean testConnection(AiSessionConfig config);
}
