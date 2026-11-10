package com.studyhub.ai.config;

public enum AiProvider {
    GROQ("Groq Cloud", "qwen/qwen3.8-27b"),
    GEMINI("Google Gemini", "gemini-3.8-flash");

    private final String displayName;
    private final String defaultModel;

    AiProvider(String displayName, String defaultModel) {
        this.displayName = displayName;
        this.defaultModel = defaultModel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultModel() {
        return defaultModel;
    }
}
