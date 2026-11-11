package com.studyhub.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Thuộc tính dùng chung cho mọi trang Thymeleaf.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final SecurityConfig securityConfig;

    public GlobalModelAdvice(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    /** Chỉ hiện nút "Đăng nhập với Google" khi đã cấu hình GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET */
    @ModelAttribute("googleLoginEnabled")
    public boolean googleLoginEnabled() {
        return securityConfig.isGoogleLoginEnabled();
    }
}
