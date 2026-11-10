package com.studyhub.ai.dto;

public class AiConfigFormDto {
    private String provider; // "GEMINI" hoặc "GROQ"
    private String geminiModel;
    private String groqModel;

    public AiConfigFormDto() {
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    public String getGroqModel() {
        return groqModel;
    }

    public void setGroqModel(String groqModel) {
        this.groqModel = groqModel;
    }
}
