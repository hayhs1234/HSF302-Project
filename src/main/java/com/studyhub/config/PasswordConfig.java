package com.studyhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Mã hóa mật khẩu:
 *  - Mật khẩu mới: BCrypt, lưu dạng "{bcrypt}$2a$...".
 *  - Mật khẩu cũ (SHA-256 hex, không có tiền tố): vẫn đăng nhập được, rồi được nâng cấp lên BCrypt.
 *  - Mật khẩu lưu dạng chữ thường KHÔNG còn được chấp nhận.
 */
@Configuration
public class PasswordConfig {

    public static PasswordEncoder createPasswordEncoder() {
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt",
                Map.of("bcrypt", new BCryptPasswordEncoder()));
        encoder.setDefaultPasswordEncoderForMatches(new LegacySha256PasswordEncoder());
        return encoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return createPasswordEncoder();
    }
}
