package com.studyhub.ai.service;

import com.studyhub.ai.config.AiProvider;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.ChatTurnDto;

import java.util.Collections;
import java.util.List;

/**
 * Giao diện chung (Strategy Pattern) cho các nền tảng AI Chat (Gemini, Groq Llama 3)
 */
public interface AiChatService {

    AiProvider getProvider();

    default String generateResponse(String userMessage, String systemPrompt, AiSessionConfig config) throws Exception {
        return generateResponse(Collections.emptyList(), userMessage, systemPrompt, config);
    }

    String generateResponse(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt, AiSessionConfig config) throws Exception;

    boolean testConnection(AiSessionConfig config);
}
