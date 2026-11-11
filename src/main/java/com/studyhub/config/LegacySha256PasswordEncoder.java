package com.studyhub.config;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Chỉ dùng để KIỂM TRA mật khẩu của tài khoản tạo trước khi chuyển sang BCrypt (SHA-256 hex, không salt).
 * Khi người dùng đăng nhập thành công, Spring Security tự băm lại sang {bcrypt}, nên định dạng cũ dần biến mất.
 * Không bao giờ dùng để băm mật khẩu mới.
 */
public class LegacySha256PasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        throw new UnsupportedOperationException("Không băm mật khẩu mới bằng SHA-256; hãy dùng BCrypt.");
    }

    @Override
    public boolean matches(CharSequence rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.length() != 64) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256Hex(rawPassword.toString()).getBytes(StandardCharsets.US_ASCII),
                storedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return true;
    }

    static String sha256Hex(String raw) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }
}
