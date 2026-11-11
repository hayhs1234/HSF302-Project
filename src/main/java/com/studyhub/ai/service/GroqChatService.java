package com.studyhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyhub.ai.config.AiProvider;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.ChatTurnDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GroqChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(GroqChatService.class);
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqChatService() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.GROQ;
    }

    @Override
    public String generateResponse(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt, AiSessionConfig config) throws Exception {
        String apiKey = config.getGroqApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Dịch vụ Groq chưa được cấu hình khóa kết nối trên hệ thống. Vui lòng liên hệ quản trị viên.");
        }

        String model = config.getGroqModel();
        if (model == null || model.isBlank()) {
            model = "qwen/qwen3.8-27b";
        }

        ArrayNode messages = buildGroqMessages(historyTurns, userMessage, systemPrompt);
        String jsonBody = buildRequestBody(model, messages, false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Groq quá thời gian chờ cho model {}: {}", model, e.getMessage());
            throw new AiOverloadedException("Groq phản hồi quá chậm (quá thời gian chờ). Vui lòng thử lại sau ít phút.", 504);
        } catch (Exception e) {
            log.error("Lỗi mạng khi gọi Groq API: {}", e.getMessage());
            throw new RuntimeException("Không thể kết nối tới dịch vụ Groq. Vui lòng kiểm tra kết nối mạng và thử lại.");
        }

        if (response.statusCode() != 200) {
            log.error("Groq API Error [{}]: {}", response.statusCode(), response.body());
            String errorMessage = parseFriendlyErrorMessage(response.body(), response.statusCode());
            if (AiOverloadedException.isRetryableStatus(response.statusCode())) {
                throw new AiOverloadedException(errorMessage, response.statusCode());
            }
            throw new RuntimeException(errorMessage);
        }

        String content = extractChoiceContent(response.body());
        if (content != null && !content.isBlank()) {
            return content;
        }

        throw new RuntimeException("Groq không trả về nội dung hợp lệ.");
    }

    @Override
    public String streamResponse(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt,
                                 AiSessionConfig config, Consumer<String> onDelta) throws Exception {
        String apiKey = config.getGroqApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Dịch vụ Groq chưa được cấu hình khóa kết nối trên hệ thống. Vui lòng liên hệ quản trị viên.");
        }
        String model = config.getGroqModel();
        if (model == null || model.isBlank()) {
            model = "qwen/qwen3.8-27b";
        }

        String jsonBody = buildRequestBody(model, buildGroqMessages(historyTurns, userMessage, systemPrompt), true);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<Stream<String>> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Groq quá thời gian chờ cho model {}: {}", model, e.getMessage());
            throw new AiOverloadedException("Groq phản hồi quá chậm (quá thời gian chờ). Vui lòng thử lại sau ít phút.", 504);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Yêu cầu tới Groq đã bị hủy.");
        } catch (Exception e) {
            log.error("Lỗi mạng khi streaming Groq API: {}", e.getMessage());
            throw new RuntimeException("Không thể kết nối tới dịch vụ Groq. Vui lòng kiểm tra kết nối mạng và thử lại.");
        }

        if (response.statusCode() != 200) {
            String errorBody;
            try (Stream<String> body = response.body()) {
                errorBody = body.collect(Collectors.joining("\n"));
            }
            log.error("Groq streaming API Error [{}]: {}", response.statusCode(), errorBody);
            String errorMessage = parseFriendlyErrorMessage(errorBody, response.statusCode());
            if (AiOverloadedException.isRetryableStatus(response.statusCode())) {
                throw new AiOverloadedException(errorMessage, response.statusCode());
            }
            throw new RuntimeException(errorMessage);
        }

        StringBuilder full = new StringBuilder();
        try (Stream<String> body = response.body()) {
            Iterator<String> it = body.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if ("[DONE]".equals(data)) break;
                String piece = extractDeltaContent(data);
                if (piece != null && !piece.isEmpty()) {
                    full.append(piece);
                    onDelta.accept(piece);
                }
            }
        }

        if (full.isEmpty()) {
            throw new RuntimeException("Groq không trả về nội dung hợp lệ.");
        }
        return full.toString();
    }

    private String extractDeltaContent(String eventJson) {
        try {
            JsonNode delta = objectMapper.readTree(eventJson).path("choices").path(0).path("delta");
            JsonNode content = delta.get("content");
            return (content != null && !content.isNull()) ? content.asText() : null;
        } catch (Exception e) {
            log.warn("Không đọc được sự kiện streaming từ Groq: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean testConnection(AiSessionConfig config) {
        try {
            String testReply = generateResponse(Collections.emptyList(), "Xin chào!", "Bạn là trợ lý AI StudyHub. Hãy trả lời ngắn gọn 1 câu.", config);
            return testReply != null && !testReply.isBlank();
        } catch (Exception e) {
            log.warn("Test connection to Groq failed: {}", e.getMessage());
            return false;
        }
    }

    private ArrayNode buildGroqMessages(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt) {
        ArrayNode messages = objectMapper.createArrayNode();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }

        if (historyTurns != null && !historyTurns.isEmpty()) {
            for (ChatTurnDto turn : historyTurns) {
                if (turn == null || turn.getContent() == null || turn.getContent().isBlank()) continue;
                ObjectNode turnMsg = messages.addObject();
                String role = "user".equalsIgnoreCase(turn.getRole()) ? "user" : "assistant";
                turnMsg.put("role", role);
                turnMsg.put("content", turn.getContent());
            }
        }

        ObjectNode current = messages.addObject();
        current.put("role", "user");
        current.put("content", userMessage);

        return messages;
    }

    private String buildRequestBody(String model, ArrayNode messages, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            root.set("messages", messages);
            root.put("temperature", 0.5);
            root.put("max_tokens", 2500);
            if (stream) {
                root.put("stream", true);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo dữ liệu yêu cầu Groq: " + e.getMessage(), e);
        }
    }

    private String extractChoiceContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).path("message");
                if (message.has("content")) {
                    return message.get("content").asText();
                }
            }
        } catch (Exception e) {
            log.error("Lỗi phân tích phản hồi JSON từ Groq: {}", e.getMessage());
        }
        return null;
    }

    private String parseFriendlyErrorMessage(String responseBody, int statusCode) {
        if (statusCode == 401) {
            return "Khóa Groq API Key không hợp lệ hoặc đã bị thu hồi. Vui lòng kiểm tra lại khóa API bắt đầu bằng gsk_...";
        }
        if (statusCode == 429) {
            return "Hạn mức yêu cầu Groq đã vượt quá giới hạn (Rate limit). Vui lòng đợi vài giây và thử lại.";
        }
        if (statusCode == 503 || statusCode == 500) {
            return "Máy chủ Groq đang bận. Vui lòng thử lại sau ít phút.";
        }

        try {
            if (responseBody != null && !responseBody.isBlank()) {
                JsonNode node = objectMapper.readTree(responseBody);
                if (node.has("error") && node.get("error").has("message")) {
                    return "Lỗi từ Groq: " + node.get("error").get("message").asText();
                }
            }
        } catch (Exception ignored) {
        }

        return "Lỗi kết nối Groq (mã " + statusCode + "). Vui lòng thử lại.";
    }
}
