package com.studyhub.ai.service;

/**
 * Nhà cung cấp AI đang quá tải / hết hạn mức tạm thời (HTTP 429, 500, 503).
 * Đây là lỗi nên thử lại hoặc chuyển sang mô hình / nhà cung cấp khác.
 */
public class AiOverloadedException extends RuntimeException {

    private final int statusCode;

    public AiOverloadedException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public static boolean isRetryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }
}
