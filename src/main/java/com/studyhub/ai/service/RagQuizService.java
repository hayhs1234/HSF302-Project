package com.studyhub.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.QuizQuestionDto;
import com.studyhub.ai.dto.QuizRequestDto;
import com.studyhub.ai.dto.QuizResponseDto;
import com.studyhub.model.Document;
import com.studyhub.model.DocumentChunk;
import com.studyhub.repository.DocumentChunkRepository;
import com.studyhub.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagQuizService {

    private static final Logger log = LoggerFactory.getLogger(RagQuizService.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\[\\{][\\s\\S]*?[\\]\\}])\\s*```");

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final Bm25Retriever bm25Retriever;
    private final AiServiceFactory aiServiceFactory;
    private final ObjectMapper objectMapper;

    public RagQuizService(DocumentRepository documentRepository,
                          DocumentChunkRepository documentChunkRepository,
                          Bm25Retriever bm25Retriever,
                          AiServiceFactory aiServiceFactory) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.bm25Retriever = bm25Retriever;
        this.aiServiceFactory = aiServiceFactory;
        this.objectMapper = new ObjectMapper();
    }

    public QuizResponseDto generateQuiz(QuizRequestDto request, Long userId, AiSessionConfig config) {
        if (config == null || !config.hasActiveKey()) {
            return QuizResponseDto.error("Dịch vụ AI hiện chưa sẵn sàng trên hệ thống. Vui lòng liên hệ quản trị viên.",
                    config != null ? config.getProvider().getDisplayName() : "AI");
        }

        try {
            List<Document> accessibleDocs = documentRepository.findAccessibleByUser(userId);
            if (accessibleDocs == null || accessibleDocs.isEmpty()) {
                return QuizResponseDto.error("Bạn chưa có tài liệu nào trong kho học liệu để tạo bài trắc nghiệm.", config.getProvider().getDisplayName());
            }

            Document targetDoc = null;
            List<DocumentChunk> candidateChunks;
            String quizTitle;
            String documentTitle;

            if (request.getDocumentId() != null) {
                targetDoc = documentRepository.findAccessibleByIdAndUser(request.getDocumentId(), userId).orElse(null);
                if (targetDoc == null) {
                    return QuizResponseDto.error("Không tìm thấy tài liệu được yêu cầu hoặc bạn không có quyền truy cập.", config.getProvider().getDisplayName());
                }
                documentTitle = targetDoc.getTitle();
                quizTitle = "Ôn tập: " + targetDoc.getTitle();

                candidateChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(targetDoc.getId());
                if (candidateChunks.isEmpty() && targetDoc.getDescription() != null && !targetDoc.getDescription().isBlank()) {
                    candidateChunks = List.of(new DocumentChunk(targetDoc, 1, targetDoc.getDescription(), 50));
                }

                if (request.getTopic() != null && !request.getTopic().isBlank() && candidateChunks.size() > 4) {
                    List<Bm25Retriever.ScoredChunk> scored = bm25Retriever.retrieveTopK(request.getTopic(), candidateChunks, 6);
                    if (!scored.isEmpty()) {
                        candidateChunks = scored.stream().map(Bm25Retriever.ScoredChunk::getChunk).collect(Collectors.toList());
                    }
                }
            } else {
                documentTitle = "Toàn bộ kho học liệu";
                quizTitle = (request.getTopic() != null && !request.getTopic().isBlank())
                        ? "Ôn tập chuyên đề: " + request.getTopic().trim()
                        : "Ôn tập tổng hợp kiến thức học phần";

                List<Long> docIds = accessibleDocs.stream().map(Document::getId).collect(Collectors.toList());
                List<DocumentChunk> allChunks = documentChunkRepository.findByDocumentIdIn(docIds);

                if (request.getTopic() != null && !request.getTopic().isBlank()) {
                    List<Bm25Retriever.ScoredChunk> scored = bm25Retriever.retrieveTopK(request.getTopic(), allChunks, 8);
                    if (!scored.isEmpty()) {
                        candidateChunks = scored.stream().map(Bm25Retriever.ScoredChunk::getChunk).collect(Collectors.toList());
                    } else {
                        candidateChunks = allChunks.stream().limit(8).collect(Collectors.toList());
                    }
                } else {
                    candidateChunks = allChunks.stream().limit(8).collect(Collectors.toList());
                }
            }

            if (candidateChunks.isEmpty()) {
                return QuizResponseDto.error("Tài liệu được chọn chưa có nội dung văn bản được lập chỉ mục để tạo câu hỏi ôn tập.", config.getProvider().getDisplayName());
            }

            int count = Math.min(Math.max(request.getQuestionCount(), 3), 10);
            String difficultyName = switch (request.getDifficulty().toUpperCase()) {
                case "EASY" -> "Cơ bản (Nhận biết & Thông hiểu)";
                case "HARD" -> "Nâng cao (Phân tích, Đánh giá & Vận dụng cao)";
                default -> "Vận dụng (Tình huống thực tế & Tính toán/Áp dụng)";
            };

            StringBuilder contextBuilder = new StringBuilder();
            int chunkSeq = 1;
            for (DocumentChunk chunk : candidateChunks) {
                contextBuilder.append("--- [ĐOẠN TRÍCH ")
                        .append(chunkSeq++)
                        .append(" - Tài liệu: ")
                        .append(chunk.getDocument() != null ? chunk.getDocument().getTitle() : "Tài liệu học tập")
                        .append("] ---\n")
                        .append(chunk.getContent())
                        .append("\n\n");
            }

            String systemPrompt = """
                    Bạn là Chuyên gia Khảo thí và Giảng viên Đại học cao cấp.
                    Nhiệm vụ của bạn là đọc kỹ dữ liệu trích xuất từ tài liệu học tập và tạo ra một bộ câu hỏi trắc nghiệm khách quan chuẩn sư phạm.
                    
                    QUY TẮC BẮT BUỘC:
                    1. Câu hỏi và 4 phương án phải hoàn toàn dựa trên nội dung thực tế trong các đoạn trích được cung cấp. Không hỏi những kiến thức ngoài tài liệu.
                    2. Mỗi câu hỏi phải có đúng 4 phương án lựa chọn A, B, C, D (mảng options có đúng 4 phần tử chuỗi).
                    3. Chỉ có DUY NHẤT 1 phương án đúng. Chỉ số correctIndex bắt buộc là số nguyên từ 0 đến 3 (0 tương ứng lựa chọn thứ nhất, 1 là thứ hai, 2 là thứ ba, 3 là thứ tư).
                    4. Phần giải thích (explanation) phải trình bày chi tiết tại sao đáp án đó đúng, trích dẫn ngắn gọn ý cốt lõi từ tài liệu.
                    5. Phần tham chiếu (sourceReference) ghi rõ tên tài liệu hoặc số đoạn trích làm căn cứ.
                    6. Định dạng đầu ra BẮT BUỘC là một JSON Array hợp lệ, không bọc thêm lời chào hay giải thích nào khác ngoài chuỗi JSON.
                    
                    CẤU TRÚC JSON MẪU:
                    [
                      {
                        "id": 1,
                        "question": "Theo tài liệu, chiến lược 4P trong Marketing căn bản bao gồm những yếu tố nào?",
                        "options": [
                          "Product, Price, Place, Promotion",
                          "People, Process, Physical Evidence, Philosophy",
                          "Plan, Produce, Promote, Profit",
                          "Position, Price, Packaging, Performance"
                        ],
                        "correctIndex": 0,
                        "explanation": "Chiến lược Marketing 4P căn bản bao gồm Product (Sản phẩm), Price (Giá cả), Place (Phân phối) và Promotion (Xúc tiến thương mại).",
                        "sourceReference": "Slide_Chien_luoc_Marketing_4P_Can_ban.pptx"
                      }
                    ]
                    """;

            String userPrompt = String.format("""
                    DỮ LIỆU TÀI LIỆU HỌC TẬP THỰC TẾ:
                    %s
                    
                    YÊU CẦU:
                    Hãy soạn đúng %d câu hỏi trắc nghiệm trích xuất từ dữ liệu trên.
                    Mức độ khó: %s.
                    %s
                    Chỉ trả về duy nhất mảng JSON (JSON Array) chứa %d câu hỏi theo cấu trúc quy định.
                    """, contextBuilder, count, difficultyName,
                    (request.getTopic() != null && !request.getTopic().isBlank() ? "Trọng tâm chủ đề: " + request.getTopic().trim() : ""),
                    count);

            AiChatService aiService = aiServiceFactory.getService(config.getProvider());
            String rawAiResponse = aiService.generateResponse(Collections.emptyList(), userPrompt, systemPrompt, config);

            List<QuizQuestionDto> questions = parseQuizJson(rawAiResponse);
            if (questions.isEmpty()) {
                log.warn("Không parse được JSON Quiz từ phản hồi AI: {}", rawAiResponse);
                return QuizResponseDto.error("Không thể cấu trúc hóa bộ câu hỏi trắc nghiệm từ AI. Vui lòng bấm tạo lại.", config.getProvider().getDisplayName());
            }

            // Gán lại số thứ tự id chuẩn 1..N
            for (int i = 0; i < questions.size(); i++) {
                questions.get(i).setId(i + 1);
            }

            return QuizResponseDto.success(
                    quizTitle,
                    documentTitle,
                    targetDoc != null ? targetDoc.getId() : null,
                    config.getProvider().getDisplayName(),
                    config.getActiveModel(),
                    questions
            );

        } catch (Exception e) {
            log.error("Lỗi tạo Quiz RAG: {}", e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : "Đã xảy ra lỗi không xác định khi tạo bài trắc nghiệm.";
            return QuizResponseDto.error(message, config.getProvider().getDisplayName());
        }
    }

    public List<QuizQuestionDto> parseQuizJson(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Collections.emptyList();
        }

        String jsonCandidate = rawText.trim();

        // 1. Thử bóc tách nếu AI bọc trong ```json ... ```
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(jsonCandidate);
        if (matcher.find()) {
            jsonCandidate = matcher.group(1).trim();
        } else {
            // 2. Tìm vị trí dấu ngoặc vuông đầu tiên và cuối cùng [ ... ]
            int firstBracket = jsonCandidate.indexOf('[');
            int lastBracket = jsonCandidate.lastIndexOf(']');
            if (firstBracket != -1 && lastBracket > firstBracket) {
                jsonCandidate = jsonCandidate.substring(firstBracket, lastBracket + 1).trim();
            }
        }

        try {
            JsonNode root = objectMapper.readTree(jsonCandidate);
            if (!root.isArray()) {
                // Trường hợp AI trả về object dạng {"questions": [...]}
                if (root.has("questions") && root.get("questions").isArray()) {
                    root = root.get("questions");
                } else {
                    return Collections.emptyList();
                }
            }

            List<QuizQuestionDto> result = new ArrayList<>();
            for (JsonNode qNode : root) {
                String question = qNode.path("question").asText("").trim();
                if (question.isEmpty()) continue;

                List<String> options = new ArrayList<>();
                JsonNode optionsNode = qNode.path("options");
                if (optionsNode.isArray()) {
                    for (JsonNode opt : optionsNode) {
                        options.add(opt.asText("").trim());
                    }
                }

                if (options.size() < 2) continue;

                int correctIndex = qNode.path("correctIndex").asInt(0);
                if (correctIndex < 0 || correctIndex >= options.size()) {
                    correctIndex = 0;
                }

                String explanation = qNode.path("explanation").asText("").trim();
                String sourceReference = qNode.path("sourceReference").asText("Tài liệu học tập").trim();

                result.add(new QuizQuestionDto(
                        result.size() + 1,
                        question,
                        options,
                        correctIndex,
                        explanation,
                        sourceReference
                ));
            }

            return result;
        } catch (Exception e) {
            log.warn("Lỗi phân tích cú pháp JSON câu hỏi Quiz: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
