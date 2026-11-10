package com.studyhub.ai.service;

import com.studyhub.ai.config.AiProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AiServiceFactory {

    private final Map<AiProvider, AiChatService> serviceMap = new EnumMap<>(AiProvider.class);

    public AiServiceFactory(List<AiChatService> services) {
        for (AiChatService service : services) {
            serviceMap.put(service.getProvider(), service);
        }
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
