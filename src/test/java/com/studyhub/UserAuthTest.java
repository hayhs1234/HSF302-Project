package com.studyhub;

import com.studyhub.model.User;
import com.studyhub.repository.UserRepository;
import com.studyhub.service.UserService;
import com.studyhub.util.PasswordUtil;
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

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
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
        assertTrue(PasswordUtil.matches("123456", user.getPassword()));
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

    @Test
    @DisplayName("Xác thực đăng nhập đúng email và mật khẩu")
    void testAuthenticateSuccess() {
        String hashedPassword = PasswordUtil.hash("123456");
        User mockUser = new User("Nguyễn Văn An", "student@gmail.com", hashedPassword);
        when(userRepository.findByEmailIgnoreCase("student@gmail.com")).thenReturn(Optional.of(mockUser));

        Optional<User> result = userService.authenticate("student@gmail.com", "123456");
        assertTrue(result.isPresent());
        assertEquals("Nguyễn Văn An", result.get().getFullName());
    }

    @Test
    @DisplayName("Xác thực đăng nhập thất bại khi sai mật khẩu")
    void testAuthenticateWrongPassword() {
        String hashedPassword = PasswordUtil.hash("123456");
        User mockUser = new User("Nguyễn Văn An", "student@gmail.com", hashedPassword);
        when(userRepository.findByEmailIgnoreCase("student@gmail.com")).thenReturn(Optional.of(mockUser));

        Optional<User> result = userService.authenticate("student@gmail.com", "sai_mat_khau");
        assertFalse(result.isPresent());
    }
}

