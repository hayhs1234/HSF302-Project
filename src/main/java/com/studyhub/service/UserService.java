package com.studyhub.service;

import com.studyhub.model.User;
import com.studyhub.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Nghiệp vụ tài khoản: đăng ký, tài khoản mẫu, liên kết đăng nhập Google.
 * Việc kiểm tra mật khẩu khi đăng nhập do Spring Security đảm nhiệm (StudyHubUserDetailsService).
 */
@Service
@Transactional
public class UserService {

    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_GOOGLE = "GOOGLE";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String fullName, String email, String password) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập họ và tên của bạn.");
        }

        if (email == null || email.trim().isEmpty() || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Địa chỉ email không đúng định dạng. Vui lòng nhập đúng địa chỉ Gmail/Email của bạn.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email '" + normalizedEmail + "' đã được đăng ký trên hệ thống. Vui lòng đăng nhập hoặc dùng email khác.");
        }

        if (password == null || password.trim().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có độ dài tối thiểu từ 6 ký tự trở lên.");
        }

        User user = new User(fullName.trim(), normalizedEmail, passwordEncoder.encode(password.trim()));
        user.setAuthProvider(PROVIDER_LOCAL);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return userRepository.findByEmailIgnoreCase(email.trim().toLowerCase());
    }

    /**
     * Lưu mật khẩu đã được băm lại (Spring Security gọi khi nâng cấp hash cũ SHA-256 lên BCrypt).
     */
    public void updateEncodedPassword(String email, String newEncodedPassword) {
        findByEmail(email).ifPresent(user -> {
            user.setPassword(newEncodedPassword);
            userRepository.save(user);
        });
    }

    /**
     * Đăng nhập Google: tìm theo Google "sub" -> theo email (liên kết tài khoản sẵn có) -> tạo tài khoản mới.
     * Email truyền vào phải là email Google đã xác minh.
     */
    public User findOrCreateGoogleUser(String googleSub, String email, String fullName, String avatarUrl) {
        if (googleSub == null || googleSub.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Tài khoản Google không cung cấp đủ thông tin (email).");
        }
        String normalizedEmail = email.trim().toLowerCase();

        Optional<User> bySub = userRepository.findByGoogleSub(googleSub);
        User user = bySub.orElseGet(() -> userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null));

        if (user == null) {
            String name = (fullName != null && !fullName.isBlank()) ? fullName.trim() : normalizedEmail;
            // Mật khẩu ngẫu nhiên đã băm: tài khoản Google không đăng nhập bằng mật khẩu được
            user = new User(name, normalizedEmail, passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setAuthProvider(PROVIDER_GOOGLE);
        }

        user.setGoogleSub(googleSub);
        if (avatarUrl != null && !avatarUrl.isBlank() && avatarUrl.length() <= 500) {
            user.setAvatarUrl(avatarUrl);
        }
        return userRepository.save(user);
    }

    public User getOrCreateDefaultUser(String fullName, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent()) {
            User user = existing.get();
            // Nếu họ tên trước đó bị lưu lỗi ký tự hỏi chấm do varchar, cập nhật lại đúng tiếng Việt
            if (user.getFullName() == null || user.getFullName().contains("?") || !user.getFullName().equals(fullName.trim())) {
                user.setFullName(fullName.trim());
                return userRepository.save(user);
            }
            return user;
        } else {
            User user = new User(fullName.trim(), normalizedEmail, passwordEncoder.encode(rawPassword.trim()));
            user.setAuthProvider(PROVIDER_LOCAL);
            return userRepository.save(user);
        }
    }

    @Transactional(readOnly = true)
    public long count() {
        return userRepository.count();
    }
}
