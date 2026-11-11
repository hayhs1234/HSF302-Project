package com.studyhub;

import com.studyhub.model.User;
import com.studyhub.repository.UserRepository;
import com.studyhub.service.UserService;
import com.studyhub.config.PasswordConfig;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class UserAuthTest {

    private UserRepository userRepository;
    private UserService userService;
    private final PasswordEncoder passwordEncoder = PasswordConfig.createPasswordEncoder();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Đăng ký thành công chỉ với Họ tên, Gmail và Mật khẩu")
    void testRegisterSuccess() {
        when(userRepository.existsByEmailIgnoreCase("student@gmail.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        User user = userService.register("Nguyễn Văn An", "student@gmail.com", "123456");
        assertNotNull(user);
        assertEquals("Nguyễn Văn An", user.getFullName());
        assertEquals("student@gmail.com", user.getEmail());
        assertTrue(user.getPassword().startsWith("{bcrypt}"), "Mật khẩu mới phải được băm bằng BCrypt");
        assertTrue(passwordEncoder.matches("123456", user.getPassword()));
        assertEquals("LOCAL", user.getAuthProvider());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Chặn đăng ký khi họ tên để trống")
    void testRegisterBlankName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.register("   ", "student@gmail.com", "123456")
        );
        assertTrue(ex.getMessage().contains("họ và tên"));
    }

    @Test
    @DisplayName("Chặn đăng ký khi email không hợp lệ")
    void testRegisterInvalidEmail() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.register("Nguyễn Văn An", "email-khong-hop-le", "123456")
        );
        assertTrue(ex.getMessage().contains("email"));
    }

    @Test
    @DisplayName("Chặn đăng ký khi email đã tồn tại")
    void testRegisterDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("student@gmail.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.register("Nguyễn Văn An", "student@gmail.com", "123456")
        );
        assertTrue(ex.getMessage().contains("đã được đăng ký"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Chặn đăng ký khi mật khẩu dưới 6 ký tự")
    void testRegisterShortPassword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.register("Nguyễn Văn An", "student@gmail.com", "123")
        );
        assertTrue(ex.getMessage().contains("6 ký tự"));
    }
}
