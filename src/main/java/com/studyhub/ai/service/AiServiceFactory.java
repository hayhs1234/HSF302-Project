package com.studyhub.ai.service;

import com.studyhub.ai.config.AiProvider;
import com.studyhub.ai.config.AiSessionConfig;
import com.studyhub.ai.dto.ChatTurnDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
public class AiServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(AiServiceFactory.class);

    /** Kiên nhẫn tối đa 3 phút khi mọi mô hình AI đều quá tải trước khi báo lỗi */
    private static final long DEFAULT_RETRY_BUDGET_MILLIS = 180_000L;
    private static final long MAX_PAUSE_MILLIS = 20_000L;

    private long retryBudgetMillis = DEFAULT_RETRY_BUDGET_MILLIS;
    private long basePauseMillis = 3_000L;

    private final Map<AiProvider, AiChatService> serviceMap = new EnumMap<>(AiProvider.class);

    public AiServiceFactory(List<AiChatService> services) {
        for (AiChatService service : services) {
            serviceMap.put(service.getProvider(), service);
        }
    }

    /** Kết quả gọi AI kèm cấu hình thực sự đã trả lời (có thể là nhà cung cấp dự phòng) */
    public record AiResult(String text, AiSessionConfig usedConfig) {
    }

    /**
     * Gọi AI; nếu nhà cung cấp chính quá tải (sau khi đã tự thử lại) thì chuyển sang nhà cung cấp còn lại nếu có khóa.
     */
    public AiResult generateWithFallback(List<ChatTurnDto> history, String userMessage, String systemPrompt,
                                         AiSessionConfig config) throws Exception {
        return streamWithFallback(history, userMessage, systemPrompt, config, null);
    }

    /**
     * Như trên nhưng streaming. Chỉ chuyển nhà cung cấp khi chưa có chữ nào gửi tới người dùng,
     * để câu trả lời không bị trộn giữa hai mô hình. onDelta == null nghĩa là gọi thường (không streaming).
     */
    public AiResult streamWithFallback(List<ChatTurnDto> history, String userMessage, String systemPrompt,
                                       AiSessionConfig config, Consumer<String> onDelta) throws Exception {
        AtomicBoolean emitted = new AtomicBoolean(false);
        Consumer<String> trackingDelta = onDelta == null ? null : piece -> {
            emitted.set(true);
            onDelta.accept(piece);
        };

        AiProvider other = config.getProvider() == AiProvider.GEMINI ? AiProvider.GROQ : AiProvider.GEMINI;
        AiSessionConfig backup = config.copyWithProvider(other);
        boolean backupUsable = backup.hasActiveKey() && serviceMap.containsKey(other);

        long deadline = System.currentTimeMillis() + retryBudgetMillis;
        AiOverloadedException primaryError = null;
        String backupErrorMessage = null;

        // Thử luân phiên nhà cung cấp chính -> dự phòng; nếu cả hai cùng quá tải thì nghỉ một lúc rồi thử lại,
        // cho tới khi hết ngân sách thời gian (mặc định 3 phút).
        for (int round = 1; ; round++) {
            try {
                return new AiResult(call(config, history, userMessage, systemPrompt, trackingDelta), config);
            } catch (AiOverloadedException e) {
                primaryError = e;
            }
            if (emitted.get()) throw primaryError; // đã hiện chữ cho người dùng, không đổi mô hình giữa chừng

            if (backupUsable) {
                log.warn("{} quá tải ({}), chuyển sang {} (vòng {})", config.getProvider().getDisplayName(),
                        primaryError.getStatusCode(), other.getDisplayName(), round);
                try {
                    return new AiResult(call(backup, history, userMessage, systemPrompt, trackingDelta), backup);
                } catch (AiOverloadedException e) {
                    backupErrorMessage = e.getMessage();
                    log.warn("Nhà cung cấp dự phòng {} cũng quá tải: {}", other.getDisplayName(), e.getMessage());
                } catch (Exception e) {
                    // Lỗi không phải quá tải (sai model, sai khóa...) -> không thử lại dự phòng nữa
                    backupErrorMessage = e.getMessage();
                    backupUsable = false;
                    log.warn("Nhà cung cấp dự phòng {} lỗi, ngừng dùng dự phòng: {}", other.getDisplayName(), e.getMessage());
                }
                if (emitted.get()) throw primaryError;
            }

            long pause = Math.min(MAX_PAUSE_MILLIS, basePauseMillis * (1L << Math.min(round - 1, 4)));
            if (System.currentTimeMillis() + pause >= deadline) break;
            log.info("Tất cả mô hình AI đang bận, chờ {} giây rồi thử lại (vòng {})", pause / 1000.0, round + 1);
            Thread.sleep(pause);
        }

        String message = primaryError.getMessage();
        if (backupErrorMessage != null) {
            message += " Nhà cung cấp dự phòng " + other.getDisplayName() + " cũng không phản hồi được: " + backupErrorMessage;
        }
        throw new AiOverloadedException(message, primaryError.getStatusCode());
    }

    /** Chỉ dùng cho kiểm thử: rút ngắn thời gian chờ */
    public void configureRetryForTesting(long retryBudgetMillis, long basePauseMillis) {
        this.retryBudgetMillis = retryBudgetMillis;
        this.basePauseMillis = basePauseMillis;
    }

    private String call(AiSessionConfig cfg, List<ChatTurnDto> history, String userMessage, String systemPrompt,
                        Consumer<String> onDelta) throws Exception {
        AiChatService service = getService(cfg.getProvider());
        return onDelta == null
                ? service.generateResponse(history, userMessage, systemPrompt, cfg)
                : service.streamResponse(history, userMessage, systemPrompt, cfg, onDelta);
    }

    /**
     * Lấy Service tương ứng với nhà cung cấp đã chọn (Strategy Pattern)
     */
    public AiChatService getService(AiProvider provider) {
        AiChatService service = serviceMap.get(provider);
        if (service == null) {
            // Mặc định fallback về Gemini nếu provider chưa được hỗ trợ
            return serviceMap.get(AiProvider.GEMINI);
        }
        return service;
    }
}
