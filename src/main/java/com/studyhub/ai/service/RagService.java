package com.studyhub.ai.service;

import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.ChatResponseDto;
import com.studyhub.ai.dto.ChatTurnDto;
import com.studyhub.ai.dto.SourceChunkDto;
import com.studyhub.model.Document;
import com.studyhub.model.DocumentChunk;
import com.studyhub.repository.DocumentChunkRepository;
import com.studyhub.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K_CHUNKS = 5;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentIndexingService documentIndexingService;
    private final Bm25Retriever bm25Retriever;
    private final AiServiceFactory aiServiceFactory;

    public RagService(DocumentRepository documentRepository,
                      DocumentChunkRepository documentChunkRepository,
                      DocumentIndexingService documentIndexingService,
                      Bm25Retriever bm25Retriever,
                      AiServiceFactory aiServiceFactory) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentIndexingService = documentIndexingService;
        this.bm25Retriever = bm25Retriever;
        this.aiServiceFactory = aiServiceFactory;
    }

    /**
     * Tra cứu tài liệu (RAG) có kiểm tra quyền truy cập của người dùng và hỗ trợ hội thoại nhiều lượt
     */
    public ChatResponseDto askRag(Long userId, Long documentId, String question,
                                  List<ChatTurnDto> historyTurns, AiSessionConfig config) {
        if (question == null || question.isBlank()) {
            return ChatResponseDto.error("Vui lòng nhập nội dung câu hỏi.", config.getProvider().getDisplayName());
        }

        try {
            Long effectiveUserId = userId != null ? userId : -1L;
            List<DocumentChunk> candidateChunks = new ArrayList<>();
            String targetDocTitle = "Tất cả tài liệu";

            if (documentId != null) {
                Optional<Document> docOpt = documentRepository.findAccessibleByIdAndUser(documentId, effectiveUserId);
                if (docOpt.isEmpty()) {
                    return ChatResponseDto.error(
                            "Không tìm thấy tài liệu hoặc bạn không có quyền truy cập tài liệu này.",
                            config.getProvider().getDisplayName()
                    );
                }
                Document doc = docOpt.get();
                targetDocTitle = doc.getTitle();

                if ("NO_CONTENT".equalsIgnoreCase(doc.getIndexStatus())) {
                    return ChatResponseDto.error(
                            "Tài liệu \"" + doc.getTitle() + "\" (" + doc.getFileExtension() + ") là tệp nén hoặc định dạng không hỗ trợ trích xuất văn bản. Vui lòng chọn tài liệu PDF, DOCX hoặc PPTX.",
                            config.getProvider().getDisplayName()
                    );
                }

                List<DocumentChunk> docChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(doc.getId());
                if (docChunks.isEmpty()) {
                    documentIndexingService.indexDocument(doc);
                    docChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(doc.getId());
                }
                candidateChunks.addAll(docChunks);
            } else {
                // Toàn bộ tài liệu mà người dùng được phép xem (của cá nhân + được chia sẻ công khai)
                List<Document> accessibleDocs = documentRepository.findAccessibleByUser(effectiveUserId);
                if (accessibleDocs.isEmpty()) {
                    return ChatResponseDto.error(
                            "Kho tài liệu của bạn hiện đang trống. Vui lòng tải tài liệu lên trước khi tra cứu.",
                            config.getProvider().getDisplayName()
                    );
                }

                List<Long> docIds = new ArrayList<>(accessibleDocs.size());
                for (Document d : accessibleDocs) {
                    if ("PENDING".equalsIgnoreCase(d.getIndexStatus())) {
                        documentIndexingService.indexDocument(d);
                    }
                    docIds.add(d.getId());
                }
                candidateChunks.addAll(documentChunkRepository.findByDocumentIdIn(docIds));
            }

            if (candidateChunks.isEmpty()) {
                return ChatResponseDto.error(
                        "Chưa có nội dung văn bản nào được trích xuất từ tài liệu đã chọn.",
                        config.getProvider().getDisplayName()
                );
            }

            List<Bm25Retriever.ScoredChunk> scoredChunks;

            // Trường hợp người dùng chọn đích danh 1 tài liệu và yêu cầu tóm tắt / tạo câu hỏi tổng quát
            if (documentId != null && Bm25Retriever.isBroadDocumentOverviewQuery(question)) {
                scoredChunks = candidateChunks.stream()
                        .limit(TOP_K_CHUNKS)
                        .map(c -> new Bm25Retriever.ScoredChunk(c, 1.0, 1))
                        .collect(Collectors.toList());
            } else {
                scoredChunks = bm25Retriever.retrieveTopK(question, candidateChunks, TOP_K_CHUNKS);

                // Nếu câu hỏi nối tiếp ("giải thích kỹ hơn ý 2") không chứa từ khóa gốc, kết hợp với câu hỏi trước đó
                if (scoredChunks.isEmpty() && historyTurns != null && !historyTurns.isEmpty()) {
                    String lastUserQuestion = findLastUserQuestion(historyTurns);
                    if (!lastUserQuestion.isBlank()) {
                        String combinedQuery = lastUserQuestion + " " + question;
                        scoredChunks = bm25Retriever.retrieveTopK(combinedQuery, candidateChunks, TOP_K_CHUNKS);
                    }
                }
            }

            // Nếu không có đoạn nào vượt ngưỡng BM25 -> Trả lời trung thực, tuyệt đối không lấy bừa 2 đoạn đầu
            if (scoredChunks.isEmpty()) {
                String scopeDesc = documentId != null
                        ? "tài liệu **" + targetDocTitle + "**"
                        : "kho tài liệu hiện có của bạn";
                String noMatchReply = "Không tìm thấy đoạn nội dung nào liên quan đến yêu cầu của bạn trong " + scopeDesc + ".\n\n"
                        + "Bạn có thể:\n"
                        + "- Thử đặt câu hỏi với từ khóa cụ thể hơn.\n"
                        + "- Chuyển sang chế độ **Trò chuyện** nếu muốn hỏi kiến thức bên ngoài tài liệu.";
                ChatResponseDto noMatchRes = ChatResponseDto.success(
                        noMatchReply,
                        config.getProvider().getDisplayName(),
                        config.getActiveModel(),
                        "RAG"
                );
                noMatchRes.setDocumentId(documentId);
                noMatchRes.setDocumentTitle(targetDocTitle);
                return noMatchRes;
            }

            StringBuilder contextBuilder = new StringBuilder();
            List<SourceChunkDto> sourceDtos = new ArrayList<>(scoredChunks.size());

            for (int i = 0; i < scoredChunks.size(); i++) {
                Bm25Retriever.ScoredChunk sc = scoredChunks.get(i);
                DocumentChunk chunk = sc.getChunk();
                Document doc = chunk.getDocument();
                int citationNum = i + 1;

                String docTitle = doc != null ? doc.getTitle() : "Tài liệu";
                String subjectInfo = (doc != null && doc.getSubject() != null)
                        ? doc.getSubject().getName() + " (" + doc.getSubject().getCode() + ")"
                        : "Chung";
                String ext = (doc != null && doc.getFileExtension() != null) ? doc.getFileExtension() : "PDF";

                contextBuilder.append(String.format(
                        "[%d] Tài liệu: \"%s\" | Môn học: %s | Đoạn %d:\n%s\n\n",
                        citationNum, docTitle, subjectInfo, chunk.getChunkIndex(), chunk.getContent()
                ));

                String snippet = chunk.getContent().length() > 240
                        ? chunk.getContent().substring(0, 240) + "…"
                        : chunk.getContent();

                sourceDtos.add(new SourceChunkDto(
                        citationNum,
                        doc != null ? doc.getId() : null,
                        docTitle,
                        ext,
                        subjectInfo,
                        chunk.getChunkIndex(),
                        snippet,
                        sc.getScore()
                ));
            }

            String systemPrompt = """
                    Bạn là trợ lý phân tích tài liệu học tập StudyHub AI.
                    Nhiệm vụ của bạn là trả lời câu hỏi dựa trên các đoạn trích tài liệu được đánh số [1], [2], [3]... trong phần ngữ cảnh.

                    QUY TẮC BẮT BUỘC:
                    1. Chỉ sử dụng thông tin có trong các đoạn trích được cung cấp. Không tự suy diễn hoặc thêm thông tin ngoài tài liệu.
                    2. Khi trích dẫn thông tin từ đoạn nào, hãy gắn ký hiệu nguồn tương ứng ngay sau câu hoặc ý đó, ví dụ: [1], [2].
                    3. Nếu người dùng hỏi tìm các tài liệu liên quan đến một chủ đề, hãy liệt kê rõ tên từng tài liệu phù hợp kèm tóm tắt ngắn gọn nội dung của tài liệu đó và gắn ký hiệu nguồn [1], [2].
                    4. Nếu các đoạn trích không chứa thông tin để trả lời câu hỏi, hãy nói rõ là tài liệu hiện tại chưa đề cập đến nội dung này.
                    5. Trình bày bằng tiếng Việt rõ ràng, mạch lạc, sử dụng Markdown (tiêu đề, danh sách, in đậm) dễ đọc. Không bọc công thức hay ký hiệu trong dấu $...$ của LaTeX (ví dụ: viết trực tiếp MR = MC, Q*, Ed thay vì $MR = MC$, $Q^*$, $E_d$).
                    """;

            String augmentedUserPrompt = String.format("""
                    DỮ LIỆU TRÍCH XUẤT TỪ TÀI LIỆU HỌC TẬP:
                    %s
                    ----------------------------------------
                    CÂU HỎI CỦA NGƯỜI DÙNG:
                    %s
                    """, contextBuilder, question);

            AiChatService aiService = aiServiceFactory.getService(config.getProvider());
            String aiReply = aiService.generateResponse(historyTurns, augmentedUserPrompt, systemPrompt, config);

            ChatResponseDto response = ChatResponseDto.success(
                    aiReply,
                    config.getProvider().getDisplayName(),
                    config.getActiveModel(),
                    "RAG"
            );
            response.setDocumentId(documentId);
            response.setDocumentTitle(targetDocTitle);
            response.setSources(sourceDtos);
            return response;

        } catch (Exception e) {
            log.error("Lỗi xử lý tra cứu tài liệu (RAG): {}", e.getMessage(), e);
            return ChatResponseDto.error(cleanErrorMessage(e.getMessage()), config.getProvider().getDisplayName());
        }
    }

    /**
     * Trò chuyện tự do (General LLM Chat) có hỗ trợ nhớ ngữ cảnh hội thoại nhiều lượt
     */
    public ChatResponseDto askGeneral(String question, List<ChatTurnDto> historyTurns, AiSessionConfig config) {
        if (question == null || question.isBlank()) {
            return ChatResponseDto.error("Vui lòng nhập nội dung câu hỏi.", config.getProvider().getDisplayName());
        }

        try {
            String systemPrompt = """
                    Bạn là StudyHub AI — trợ lý học tập và nghiên cứu đại học.
                    Bạn hỗ trợ sinh viên và giảng viên:
                    - Giải đáp kiến thức các môn Công nghệ thông tin (Java, Spring Boot, SQL Server, Cấu trúc dữ liệu, Mạng máy tính, Kiểm thử phần mềm...) cũng như Kinh tế, Marketing, Pháp luật, Ngoại ngữ.
                    - Hướng dẫn viết code, phân tích thuật toán, giải thích khái niệm, lập dàn ý báo cáo và tạo câu hỏi ôn tập.
                    Hãy trả lời bằng tiếng Việt tự nhiên, chuẩn xác, có cấu trúc Markdown rõ ràng. Không bọc ký hiệu hay công thức đơn giản trong dấu $...$ của LaTeX.
                    """;

            AiChatService aiService = aiServiceFactory.getService(config.getProvider());
            String aiReply = aiService.generateResponse(historyTurns, question, systemPrompt, config);

            return ChatResponseDto.success(
                    aiReply,
                    config.getProvider().getDisplayName(),
                    config.getActiveModel(),
                    "GENERAL"
            );
        } catch (Exception e) {
            log.error("Lỗi xử lý trò chuyện AI: {}", e.getMessage(), e);
            return ChatResponseDto.error(cleanErrorMessage(e.getMessage()), config.getProvider().getDisplayName());
        }
    }

    private String findLastUserQuestion(List<ChatTurnDto> historyTurns) {
        for (int i = historyTurns.size() - 1; i >= 0; i--) {
            ChatTurnDto t = historyTurns.get(i);
            if (t != null && "user".equalsIgnoreCase(t.getRole()) && t.getContent() != null && !t.getContent().isBlank()) {
                return t.getContent();
            }
        }
        return "";
    }

    private String cleanErrorMessage(String rawMsg) {
        if (rawMsg == null || rawMsg.isBlank()) {
            return "Đã xảy ra sự cố khi kết nối tới dịch vụ AI. Vui lòng thử lại.";
        }
        return rawMsg;
    }
}
