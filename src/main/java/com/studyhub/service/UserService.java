package com.studyhub.service;

import com.studyhub.model.User;
import com.studyhub.repository.UserRepository;
import com.studyhub.util.PasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Transactional
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

        String hashedPassword = PasswordUtil.hash(password.trim());
        User user = new User(fullName.trim(), normalizedEmail, hashedPassword);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> authenticate(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            return Optional.empty();
        }

        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (PasswordUtil.matches(password.trim(), user.getPassword())) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
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
            User user = new User(fullName.trim(), normalizedEmail, PasswordUtil.hash(rawPassword.trim()));
            return userRepository.save(user);
        }
    }

    @Transactional(readOnly = true)
    public long count() {
        return userRepository.count();
    }
}

