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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class GeminiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiChatService() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.GEMINI;
    }

    @Override
    public String generateResponse(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt, AiSessionConfig config) throws Exception {
        String apiKey = config.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Dịch vụ Google Gemini chưa được cấu hình khóa kết nối trên hệ thống. Vui lòng liên hệ quản trị viên.");
        }

        String model = config.getGeminiModel();

        ObjectNode rootNode = objectMapper.createObjectNode();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sysInstruction = rootNode.putObject("system_instruction");
            ArrayNode sysParts = sysInstruction.putArray("parts");
            sysParts.addObject().put("text", systemPrompt);
        }

        ArrayNode contents = rootNode.putArray("contents");
        String lastRole = null;

        if (historyTurns != null && !historyTurns.isEmpty()) {
            for (ChatTurnDto turn : historyTurns) {
                if (turn == null || turn.getContent() == null || turn.getContent().isBlank()) continue;
                String geminiRole = "user".equalsIgnoreCase(turn.getRole()) ? "user" : "model";
                if (lastRole == null && !"user".equals(geminiRole)) {
                    continue;
                }
                if (geminiRole.equals(lastRole) && !contents.isEmpty()) {
                    ObjectNode prevTurn = (ObjectNode) contents.get(contents.size() - 1);
                    ArrayNode prevParts = (ArrayNode) prevTurn.path("parts");
                    prevParts.addObject().put("text", turn.getContent());
                } else {
                    ObjectNode turnNode = contents.addObject();
                    turnNode.put("role", geminiRole);
                    turnNode.putArray("parts").addObject().put("text", turn.getContent());
                    lastRole = geminiRole;
                }
            }
        }

        if ("user".equals(lastRole) && !contents.isEmpty()) {
            ObjectNode prevTurn = (ObjectNode) contents.get(contents.size() - 1);
            ArrayNode prevParts = (ArrayNode) prevTurn.path("parts");
            prevParts.addObject().put("text", userMessage);
        } else {
            ObjectNode userTurn = contents.addObject();
            userTurn.put("role", "user");
            userTurn.putArray("parts").addObject().put("text", userMessage);
        }

        ObjectNode genConfig = rootNode.putObject("generationConfig");
        genConfig.put("temperature", 0.6);
        genConfig.put("maxOutputTokens", 4096);

        String jsonBody = objectMapper.writeValueAsString(rootNode);
        String requestUrl = String.format(GEMINI_API_URL, model, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(35))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException te) {
            log.error("Gemini API Timeout cho model {}: {}", model, te.getMessage());
            throw new RuntimeException("Không thể nhận phản hồi từ Google Gemini do quá thời gian chờ. Vui lòng thử lại.");
        } catch (Exception e) {
            log.error("Lỗi mạng khi gọi Gemini API (model {}): {}", model, e.getMessage());
            throw new RuntimeException("Không thể kết nối tới dịch vụ Google Gemini. Vui lòng kiểm tra kết nối mạng và thử lại.");
        }

        int status = response.statusCode();
        if (status == 200) {
            String extracted = extractReplyText(response.body());
            if (extracted != null && !extracted.isBlank()) {
                return extracted;
            }
            throw new RuntimeException("Google Gemini không trả về nội dung phản hồi hợp lệ.");
        }

        if (status == 503 && !"gemini-3.5-flash-lite".equals(model)) {
            log.warn("Gemini model '{}' đang quá tải (503 High Demand), tự động chuyển sang gemini-3.5-flash-lite...", model);
            String fallbackUrl = String.format(GEMINI_API_URL, "gemini-3.5-flash-lite", apiKey);
            HttpRequest fbReq = HttpRequest.newBuilder()
                    .uri(URI.create(fallbackUrl))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> fbResp = httpClient.send(fbReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (fbResp.statusCode() == 200) {
                    String extracted = extractReplyText(fbResp.body());
                    if (extracted != null && !extracted.isBlank()) {
                        return extracted;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        log.error("Gemini API Error [{}] on model {}: {}", status, model, response.body());
        throw new RuntimeException(parseFriendlyErrorMessage(response.body(), status));
    }

    private String extractReplyText(String responseBody) throws Exception {
        JsonNode responseJson = objectMapper.readTree(responseBody);
        JsonNode candidates = responseJson.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode firstCandidate = candidates.get(0);
            JsonNode parts = firstCandidate.path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                StringBuilder reply = new StringBuilder();
                for (JsonNode part : parts) {
                    if (part.path("thought").asBoolean(false)) {
                        continue;
                    }
                    if (part.has("text")) {
                        reply.append(part.get("text").asText());
                    }
                }
                if (reply.length() > 0) {
                    return reply.toString();
                }
                if (parts.get(0).has("text")) {
                    return parts.get(0).get("text").asText();
                }
            }
        }
        return null;
    }

    @Override
    public boolean testConnection(AiSessionConfig config) {
        try {
            String testReply = generateResponse(Collections.emptyList(), "Xin chào!", "Bạn là trợ lý AI StudyHub. Hãy trả lời ngắn gọn 1 câu.", config);
            return testReply != null && !testReply.isBlank();
        } catch (Exception e) {
            log.warn("Test connection to Gemini failed: {}", e.getMessage());
            return false;
        }
    }

    private String parseFriendlyErrorMessage(String body, int statusCode) {
        if (statusCode == 400) {
            if (body != null && body.contains("API_KEY_INVALID")) {
                return "Khóa kết nối Google Gemini của hệ thống không hợp lệ hoặc đã hết hạn. Vui lòng liên hệ quản trị viên.";
            }
            return "Yêu cầu gửi tới Google Gemini không hợp lệ hoặc khóa API sai định dạng.";
        }
        if (statusCode == 401 || statusCode == 403) {
            return "Khóa API Google Gemini không có quyền truy cập hoặc đã bị vô hiệu hóa.";
        }
        if (statusCode == 429) {
            return "Khóa API Google Gemini đang tạm chạm giới hạn số lượt gọi miễn phí trong phút. Vui lòng đợi khoảng 15-30 giây rồi thử lại.";
        }
        if (statusCode == 503) {
            return "Máy chủ mô hình này hiện đang quá tải tạm thời (High demand từ Google). Bạn có thể chọn mô hình 'Gemini 3.5 Flash-Lite' để tiếp tục trò chuyện ngay lập tức.";
        }
        if (statusCode >= 500) {
            return "Máy chủ Google Gemini hiện đang quá tải tạm thời. Vui lòng thử lại sau vài giây.";
        }
        return "Không thể xử lý yêu cầu từ Google Gemini (Mã trạng thái: " + statusCode + ").";
    }
}
