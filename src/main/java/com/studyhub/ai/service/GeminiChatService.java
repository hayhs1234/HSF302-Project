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
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GeminiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatService.class);
    private static final String DEFAULT_API_BASE = "https://generativelanguage.googleapis.com";
    private static final String GENERATE_PATH = "/v1beta/models/%s:generateContent?key=%s";
    private static final String STREAM_PATH = "/v1beta/models/%s:streamGenerateContent?alt=sse&key=%s";
    private static final String FALLBACK_MODEL = "gemini-3.5-flash-lite";
    private static final int MAX_ATTEMPTS_PER_MODEL = 2;
    /** Soạn đề 10 câu kèm giải thích có thể mất khá lâu, nên chờ lâu hơn chat thường */
    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 90;

    /** Có thể đổi trong kiểm thử để trỏ tới máy chủ giả lập */
    private String apiBase = DEFAULT_API_BASE;
    private Duration requestTimeout = Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS);
    private long backoffMillis = 1200L;

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

    /** Chỉ dùng cho kiểm thử: trỏ tới máy chủ giả lập và rút ngắn thời gian chờ */
    public void configureForTesting(String apiBase, Duration requestTimeout, long backoffMillis) {
        this.apiBase = apiBase;
        this.requestTimeout = requestTimeout;
        this.backoffMillis = backoffMillis;
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

        String jsonBody = buildRequestJson(historyTurns, userMessage, systemPrompt);
        // Thử model đã chọn trước, rồi tới model dự phòng; mỗi model thử lại khi Google báo quá tải
        int lastStatus = 0;
        String lastBody = null;
        candidates:
        for (String candidate : candidateModels(model)) {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(String.format(apiBase + GENERATE_PATH, candidate, apiKey)))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .timeout(requestTimeout)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response;
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                } catch (java.net.http.HttpTimeoutException te) {
                    // Model phản hồi quá chậm (thường do quá tải): không thử lại cùng model, chuyển ngay sang model kế tiếp
                    log.warn("Gemini model '{}' quá thời gian chờ {}s, chuyển sang phương án dự phòng", candidate, requestTimeout.getSeconds());
                    lastStatus = 504;
                    continue candidates;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Yêu cầu tới Google Gemini đã bị hủy.");
                } catch (Exception e) {
                    log.error("Lỗi mạng khi gọi Gemini API (model {}): {}", candidate, e.getMessage());
                    throw new RuntimeException("Không thể kết nối tới dịch vụ Google Gemini. Vui lòng kiểm tra kết nối mạng và thử lại.");
                }

                int status = response.statusCode();
                if (status == 200) {
                    String extracted = extractReplyText(response.body());
                    if (extracted != null && !extracted.isBlank()) {
                        if (!candidate.equals(model)) {
                            log.info("Gemini: đã trả lời bằng model dự phòng {} thay cho {}", candidate, model);
                        }
                        return extracted;
                    }
                    throw new RuntimeException("Google Gemini không trả về nội dung phản hồi hợp lệ.");
                }

                lastStatus = status;
                lastBody = response.body();
                if (!AiOverloadedException.isRetryableStatus(status)) {
                    log.error("Gemini API Error [{}] on model {}: {}", status, candidate, lastBody);
                    throw new RuntimeException(parseFriendlyErrorMessage(lastBody, status));
                }
                log.warn("Gemini model '{}' quá tải (mã {}), lần thử {}/{}", candidate, status, attempt, MAX_ATTEMPTS_PER_MODEL);
                if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                    sleepBackoff(attempt);
                }
            }
        }

        log.error("Gemini quá tải trên mọi model đã thử. Mã cuối {}: {}", lastStatus, lastBody);
        throw new AiOverloadedException(overloadMessage(lastStatus), lastStatus);
    }

    /** Model người dùng chọn, sau đó là model dự phòng (nếu khác) */
    private List<String> candidateModels(String model) {
        return FALLBACK_MODEL.equals(model) ? List.of(model) : List.of(model, FALLBACK_MODEL);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(backoffMillis * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String overloadMessage(int status) {
        if (status == 504) {
            return "Google Gemini phản hồi quá chậm (quá thời gian chờ) trên mọi mô hình đã thử. Vui lòng thử lại sau ít phút.";
        }
        return status == 429
                ? "Khóa API Google Gemini đang chạm giới hạn số lượt gọi miễn phí. Hệ thống đã tự thử lại nhưng chưa được, vui lòng đợi khoảng 30 giây rồi thử lại."
                : "Máy chủ Google Gemini đang quá tải (kể cả mô hình dự phòng). Hệ thống đã tự thử lại nhiều lần nhưng chưa được, vui lòng thử lại sau ít phút hoặc chuyển sang Groq.";
    }

    /**
     * Dựng JSON yêu cầu gửi Gemini (system instruction + lịch sử hội thoại + câu hỏi hiện tại).
     */
    private String buildRequestJson(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt) throws Exception {
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


        return objectMapper.writeValueAsString(rootNode);
    }

    @Override
    public String streamResponse(List<ChatTurnDto> historyTurns, String userMessage, String systemPrompt,
                                 AiSessionConfig config, Consumer<String> onDelta) throws Exception {
        String apiKey = config.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Dịch vụ Google Gemini chưa được cấu hình khóa kết nối trên hệ thống. Vui lòng liên hệ quản trị viên.");
        }

        String model = config.getGeminiModel();
        String jsonBody = buildRequestJson(historyTurns, userMessage, systemPrompt);

        // Lỗi quá tải được báo ngay ở mã trạng thái, trước khi có chữ nào được gửi -> thử lại / đổi model an toàn
        HttpResponse<Stream<String>> response = null;
        int status = 0;
        outer:
        for (String candidate : candidateModels(model)) {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
                response = openStream(candidate, apiKey, jsonBody);
                status = response.statusCode();
                if (status == 200) break outer;

                String errorBody;
                try (Stream<String> body = response.body()) {
                    errorBody = body.collect(Collectors.joining("\n"));
                }
                if (!AiOverloadedException.isRetryableStatus(status)) {
                    log.error("Gemini streaming API Error [{}] on model {}: {}", status, candidate, errorBody);
                    throw new RuntimeException(parseFriendlyErrorMessage(errorBody, status));
                }
                log.warn("Gemini streaming model '{}' quá tải (mã {}), lần thử {}/{}", candidate, status, attempt, MAX_ATTEMPTS_PER_MODEL);
                if (attempt < MAX_ATTEMPTS_PER_MODEL) sleepBackoff(attempt);
            }
        }
        if (status != 200) {
            throw new AiOverloadedException(overloadMessage(status), status);
        }

        StringBuilder full = new StringBuilder();
        try (Stream<String> body = response.body()) {
            Iterator<String> it = body.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                String piece = extractStreamPiece(data);
                if (piece != null && !piece.isEmpty()) {
                    full.append(piece);
                    onDelta.accept(piece);
                }
            }
        }

        if (full.isEmpty()) {
            throw new RuntimeException("Google Gemini không trả về nội dung phản hồi hợp lệ.");
        }
        return full.toString();
    }

    private HttpResponse<Stream<String>> openStream(String model, String apiKey, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(apiBase + STREAM_PATH, model, apiKey)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(35))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (java.net.http.HttpTimeoutException te) {
            // Chưa có chữ nào gửi tới người dùng -> báo quá tải để chuyển sang nhà cung cấp dự phòng
            log.warn("Gemini streaming quá thời gian chờ cho model {}: {}", model, te.getMessage());
            throw new AiOverloadedException(overloadMessage(504), 504);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Yêu cầu tới Google Gemini đã bị hủy.");
        } catch (Exception e) {
            log.error("Lỗi mạng khi streaming Gemini API (model {}): {}", model, e.getMessage());
            throw new RuntimeException("Không thể kết nối tới dịch vụ Google Gemini. Vui lòng kiểm tra kết nối mạng và thử lại.");
        }
    }

    /**
     * Lấy phần văn bản của một sự kiện SSE (bỏ qua các phần "thought" nội bộ của mô hình).
     */
    private String extractStreamPiece(String eventJson) {
        try {
            JsonNode parts = objectMapper.readTree(eventJson).path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray()) return null;
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) continue;
                if (part.has("text")) sb.append(part.get("text").asText());
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Không đọc được sự kiện streaming từ Gemini: {}", e.getMessage());
            return null;
        }
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
            return "Máy chủ Google Gemini đang quá tải tạm thời. Vui lòng thử lại sau ít phút hoặc chuyển sang Groq.";
        }
        if (statusCode >= 500) {
            return "Máy chủ Google Gemini hiện đang quá tải tạm thời. Vui lòng thử lại sau vài giây.";
        }
        return "Không thể xử lý yêu cầu từ Google Gemini (Mã trạng thái: " + statusCode + ").";
    }
}
