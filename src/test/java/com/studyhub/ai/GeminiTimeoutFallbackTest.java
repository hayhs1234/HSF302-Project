package com.studyhub.ai;

import com.studyhub.ai.config.AiProvider;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.ChatTurnDto;
import com.studyhub.ai.service.AiChatService;
import com.studyhub.ai.service.AiOverloadedException;
import com.studyhub.ai.service.AiServiceFactory;
import com.studyhub.ai.service.GeminiChatService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini trả lời quá chậm (timeout) phải được coi là quá tải: chuyển sang model dự phòng,
 * rồi sang nhà cung cấp khác — không được báo lỗi ngay như trước.
 * Dùng máy chủ HTTP giả lập chạy cục bộ, không gọi Google thật.
 */
class GeminiTimeoutFallbackTest {

    private static final String OK_BODY = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Đề thi từ model dự phòng\"}]}}]}";

    private HttpServer server;
    /** Hành vi của từng model: "slow" = treo quá thời gian chờ, "ok" = trả lời ngay */
    private final Map<String, String> behaviour = new ConcurrentHashMap<>();
    private final Map<String, Integer> hits = new ConcurrentHashMap<>();
    private GeminiChatService gemini;

    @BeforeEach
    void startFakeGemini() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v1beta/models/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String model = path.substring("/v1beta/models/".length(), path.indexOf(':'));
            hits.merge(model, 1, Integer::sum);
            try {
                if ("slow".equals(behaviour.get(model))) {
                    Thread.sleep(3000);
                }
                byte[] body = OK_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (Exception ignored) {
                // client đã ngắt do quá thời gian chờ
            } finally {
                exchange.close();
            }
        });
        server.start();

        gemini = new GeminiChatService();
        gemini.configureForTesting("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofMillis(500), 10);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AiSessionConfig config(String model, boolean withGroqKey) {
        AiSessionConfig c = new AiSessionConfig();
        c.setProvider(AiProvider.GEMINI);
        c.setGeminiApiKey("test-key");
        c.setGeminiModel(model);
        c.setGroqApiKey(withGroqKey ? "test-groq-key" : "");
        return c;
    }

    @Test
    void timeoutOnSelectedModel_usesFallbackModel() throws Exception {
        behaviour.put("gemini-3.1-pro", "slow");
        behaviour.put("gemini-3.5-flash-lite", "ok");

        String reply = gemini.generateResponse(Collections.emptyList(), "tạo đề", "sys", config("gemini-3.1-pro", false));

        assertEquals("Đề thi từ model dự phòng", reply);
        assertEquals(1, hits.get("gemini-3.1-pro"), "Timeout thì không thử lại cùng model");
        assertEquals(1, hits.get("gemini-3.5-flash-lite"));
    }

    @Test
    void timeoutOnAllGeminiModels_isReportedAsOverload() {
        behaviour.put("gemini-3.5-flash-lite", "slow");

        AiOverloadedException ex = assertThrows(AiOverloadedException.class,
                () -> gemini.generateResponse(Collections.emptyList(), "tạo đề", "sys", config("gemini-3.5-flash-lite", false)));
        assertEquals(504, ex.getStatusCode());
    }

    @Test
    void timeoutOnGemini_switchesToGroqThroughFactory() throws Exception {
        behaviour.put("gemini-3.5-flash-lite", "slow");
        AiChatService fakeGroq = new AiChatService() {
            @Override
            public AiProvider getProvider() {
                return AiProvider.GROQ;
            }

            @Override
            public String generateResponse(List<ChatTurnDto> h, String m, String s, AiSessionConfig c) {
                return "Đề thi từ Groq";
            }

            @Override
            public String streamResponse(List<ChatTurnDto> h, String m, String s, AiSessionConfig c, Consumer<String> d) {
                return "Đề thi từ Groq";
            }

            @Override
            public boolean testConnection(AiSessionConfig config) {
                return true;
            }
        };
        AiServiceFactory factory = new AiServiceFactory(List.of(gemini, fakeGroq));
        factory.configureRetryForTesting(300, 10);

        AiServiceFactory.AiResult result = factory.generateWithFallback(Collections.emptyList(), "tạo đề", "sys",
                config("gemini-3.5-flash-lite", true));

        assertEquals("Đề thi từ Groq", result.text());
        assertEquals(AiProvider.GROQ, result.usedConfig().getProvider());
    }
}
