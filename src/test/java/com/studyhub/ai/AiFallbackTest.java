package com.studyhub.ai;

import com.studyhub.ai.config.AiProvider;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.ChatTurnDto;
import com.studyhub.ai.service.AiChatService;
import com.studyhub.ai.service.AiOverloadedException;
import com.studyhub.ai.service.AiServiceFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Khi nhà cung cấp chính quá tải thì tự chuyển sang nhà cung cấp còn lại (nếu có khóa API).
 */
class AiFallbackTest {

    /** Service giả: hoặc luôn quá tải, hoặc trả lời một câu cố định */
    private static AiChatService fake(AiProvider provider, boolean overloaded, String reply, List<String> calls) {
        return new AiChatService() {
            @Override
            public AiProvider getProvider() {
                return provider;
            }

            @Override
            public String generateResponse(List<ChatTurnDto> h, String msg, String sys, AiSessionConfig c) {
                calls.add(provider.name());
                if (overloaded) throw new AiOverloadedException("quá tải", 503);
                return reply;
            }

            @Override
            public String streamResponse(List<ChatTurnDto> h, String msg, String sys, AiSessionConfig c, Consumer<String> onDelta) {
                calls.add(provider.name());
                if (overloaded) throw new AiOverloadedException("quá tải", 503);
                onDelta.accept(reply);
                return reply;
            }

            @Override
            public boolean testConnection(AiSessionConfig config) {
                return true;
            }
        };
    }

    /** Factory với thời gian thử lại rút ngắn để test chạy nhanh */
    private static AiServiceFactory fastFactory(List<AiChatService> services) {
        AiServiceFactory f = new AiServiceFactory(services);
        f.configureRetryForTesting(300, 10);
        return f;
    }

    private static AiSessionConfig geminiConfig(boolean withGroqKey) {
        AiSessionConfig c = new AiSessionConfig();
        c.setProvider(AiProvider.GEMINI);
        c.setGeminiApiKey("test-gemini-key");
        c.setGroqApiKey(withGroqKey ? "test-groq-key" : "");
        return c;
    }

    @Test
    void geminiOverloaded_switchesToGroq() throws Exception {
        List<String> calls = new ArrayList<>();
        AiServiceFactory factory = fastFactory(List.of(
                fake(AiProvider.GEMINI, true, null, calls),
                fake(AiProvider.GROQ, false, "[{\"question\":\"Q\"}]", calls)));

        AiServiceFactory.AiResult result = factory.generateWithFallback(Collections.emptyList(), "tạo đề", "sys", geminiConfig(true));

        assertEquals("[{\"question\":\"Q\"}]", result.text());
        assertEquals(AiProvider.GROQ, result.usedConfig().getProvider());
        assertEquals(List.of("GEMINI", "GROQ"), calls);
    }

    @Test
    void noBackupKey_keepsOriginalOverloadError() {
        List<String> calls = new ArrayList<>();
        AiServiceFactory factory = fastFactory(List.of(
                fake(AiProvider.GEMINI, true, null, calls),
                fake(AiProvider.GROQ, false, "ok", calls)));

        assertThrows(AiOverloadedException.class,
                () -> factory.generateWithFallback(Collections.emptyList(), "tạo đề", "sys", geminiConfig(false)));
        assertFalse(calls.contains("GROQ"), "Không có khóa Groq thì không được gọi Groq");
        assertTrue(calls.size() > 1, "Không có dự phòng thì vẫn phải kiên nhẫn thử lại Gemini");
    }

    @Test
    void primaryHealthy_doesNotCallBackup() throws Exception {
        List<String> calls = new ArrayList<>();
        AiServiceFactory factory = fastFactory(List.of(
                fake(AiProvider.GEMINI, false, "gemini ok", calls),
                fake(AiProvider.GROQ, false, "groq ok", calls)));

        AiServiceFactory.AiResult result = factory.generateWithFallback(Collections.emptyList(), "hi", "sys", geminiConfig(true));

        assertEquals("gemini ok", result.text());
        assertEquals(List.of("GEMINI"), calls);
    }

    @Test
    void streaming_switchesProviderBeforeAnyTextIsSent() throws Exception {
        List<String> calls = new ArrayList<>();
        List<String> received = new ArrayList<>();
        AiServiceFactory factory = fastFactory(List.of(
                fake(AiProvider.GEMINI, true, null, calls),
                fake(AiProvider.GROQ, false, "xin chào", calls)));

        AiServiceFactory.AiResult result = factory.streamWithFallback(Collections.emptyList(), "hi", "sys",
                geminiConfig(true), received::add);

        assertEquals(List.of("xin chào"), received);
        assertEquals(AiProvider.GROQ, result.usedConfig().getProvider());
    }

    @Test
    void bothBusyAtFirst_retriesUntilOneRecovers() throws Exception {
        List<String> calls = new ArrayList<>();
        int[] geminiCalls = {0};
        AiChatService flakyGemini = new AiChatService() {
            public AiProvider getProvider() { return AiProvider.GEMINI; }
            public String generateResponse(List<ChatTurnDto> h, String m, String s, AiSessionConfig c) {
                calls.add("GEMINI");
                if (++geminiCalls[0] < 3) throw new AiOverloadedException("quá tải", 503);
                return "gemini hồi phục";
            }
            public boolean testConnection(AiSessionConfig c) { return true; }
        };
        AiServiceFactory factory = fastFactory(List.of(flakyGemini, fake(AiProvider.GROQ, true, null, calls)));

        AiServiceFactory.AiResult result = factory.generateWithFallback(Collections.emptyList(), "tạo đề", "sys", geminiConfig(true));

        assertEquals("gemini hồi phục", result.text());
        assertEquals(List.of("GEMINI", "GROQ", "GEMINI", "GROQ", "GEMINI"), calls);
    }

    @Test
    void bothFailForGood_errorMentionsBackupReason() {
        List<String> calls = new ArrayList<>();
        AiChatService brokenGroq = new AiChatService() {
            public AiProvider getProvider() { return AiProvider.GROQ; }
            public String generateResponse(List<ChatTurnDto> h, String m, String s, AiSessionConfig c) {
                calls.add("GROQ");
                throw new RuntimeException("Lỗi từ Groq: model không tồn tại");
            }
            public boolean testConnection(AiSessionConfig c) { return true; }
        };
        AiServiceFactory factory = fastFactory(List.of(fake(AiProvider.GEMINI, true, null, calls), brokenGroq));

        AiOverloadedException ex = assertThrows(AiOverloadedException.class,
                () -> factory.generateWithFallback(Collections.emptyList(), "tạo đề", "sys", geminiConfig(true)));
        assertTrue(ex.getMessage().contains("model không tồn tại"), ex.getMessage());
        assertEquals(1, calls.stream().filter("GROQ"::equals).count(), "Groq lỗi cấu hình thì không gọi lại nữa");
    }
}
