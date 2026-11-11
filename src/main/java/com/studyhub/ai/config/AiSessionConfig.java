package com.studyhub.ai.config;

import java.io.Serializable;

public class AiSessionConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private AiProvider provider = AiProvider.GROQ;
    private String geminiApiKey = "";
    private String geminiModel = "gemini-3.5-flash-lite";
    private String groqApiKey = "";
    private String groqModel = "qwen/qwen3.8-27b";

    public AiSessionConfig() {
    }

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider != null ? provider : AiProvider.GROQ;
    }

    public String getGeminiApiKey() {
        return geminiApiKey != null ? geminiApiKey.trim() : "";
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey != null ? geminiApiKey.trim() : "";
    }

    public String getGeminiModel() {
        return geminiModel != null && !geminiModel.isBlank() ? geminiModel.trim() : "gemini-3.5-flash-lite";
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel != null && !geminiModel.isBlank() ? geminiModel.trim() : "gemini-3.5-flash-lite";
    }

    public String getGroqApiKey() {
        return groqApiKey != null ? groqApiKey.trim() : "";
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey != null ? groqApiKey.trim() : "";
    }

    public String getGroqModel() {
        return groqModel != null && !groqModel.isBlank() ? groqModel.trim() : "qwen/qwen3.8-27b";
    }

    public void setGroqModel(String groqModel) {
        this.groqModel = groqModel != null && !groqModel.isBlank() ? groqModel.trim() : "qwen/qwen3.8-27b";
    }

    public String getActiveApiKey() {
        return provider == AiProvider.GROQ ? getGroqApiKey() : getGeminiApiKey();
    }

    public String getActiveModel() {
        return provider == AiProvider.GROQ ? getGroqModel() : getGeminiModel();
    }

    /** Bản sao cấu hình nhưng dùng nhà cung cấp khác (để dự phòng khi nhà cung cấp chính quá tải) */
    public AiSessionConfig copyWithProvider(AiProvider otherProvider) {
        AiSessionConfig copy = new AiSessionConfig();
        copy.setProvider(otherProvider);
        copy.setGeminiApiKey(getGeminiApiKey());
        copy.setGeminiModel(getGeminiModel());
        copy.setGroqApiKey(getGroqApiKey());
        copy.setGroqModel(getGroqModel());
        return copy;
    }

    public boolean hasActiveKey() {
        String key = getActiveApiKey();
        return key != null && !key.isBlank();
    }
}
