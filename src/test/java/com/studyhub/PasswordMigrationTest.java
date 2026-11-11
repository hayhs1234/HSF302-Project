package com.studyhub;

import com.studyhub.config.PasswordConfig;
import com.studyhub.model.User;
import com.studyhub.repository.UserRepository;
import com.studyhub.service.GoogleOidcUserService;
import com.studyhub.service.StudyHubUserDetailsService;
import com.studyhub.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Đăng nhập qua Spring Security: BCrypt, nâng cấp hash SHA-256 cũ, từ chối mật khẩu lưu dạng chữ thường,
 * và liên kết tài khoản khi đăng nhập bằng Google.
 */
class PasswordMigrationTest {

    private UserRepository userRepository;
    private UserService userService;
    private DaoAuthenticationProvider provider;
    private final PasswordEncoder encoder = PasswordConfig.createPasswordEncoder();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        userService = new UserService(userRepository, encoder);
        StudyHubUserDetailsService uds = new StudyHubUserDetailsService(userService);
        provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        provider.setUserDetailsPasswordService(uds);
    }

    private User existingUser(String storedPassword) {
        User u = new User("Nguyễn Văn An", "student@gmail.com", storedPassword);
        when(userRepository.findByEmailIgnoreCase("student@gmail.com")).thenReturn(Optional.of(u));
        return u;
    }

    private Authentication login(String password) {
        return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("student@gmail.com", password));
    }

    private static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void legacySha256Password_canLogin_andIsUpgradedToBcrypt() throws Exception {
        User user = existingUser(sha256("123456"));

        Authentication auth = login("123456");

        assertTrue(auth.isAuthenticated());
        assertTrue(user.getPassword().startsWith("{bcrypt}"), "Hash cũ phải được nâng cấp lên BCrypt sau khi đăng nhập");
        assertTrue(encoder.matches("123456", user.getPassword()));
    }

    @Test
    void plaintextStoredPassword_isRejected() {
        existingUser("123456"); // trước đây PasswordUtil chấp nhận cả mật khẩu lưu dạng chữ thường
        assertThrows(BadCredentialsException.class, () -> login("123456"));
    }

    @Test
    void wrongPassword_isRejected() throws Exception {
        existingUser(sha256("123456"));
        assertThrows(BadCredentialsException.class, () -> login("sai_mat_khau"));
    }

    @Test
    void bcryptPassword_logsIn_withoutRehashing() {
        existingUser(encoder.encode("123456"));
        clearInvocations(userRepository);

        assertTrue(login("123456").isAuthenticated());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unknownEmail_isRejected() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> login("123456"));
    }

    // ===== Đăng nhập với Google =====

    private OidcUser googleUser(String sub, String email, boolean verified) {
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", sub, "email", email, "email_verified", verified, "name", "Trần Mai Anh",
                        "picture", "https://lh3.googleusercontent.com/a/avatar"));
        return new DefaultOidcUser(List.of(), idToken, "email");
    }

    @Test
    void googleLogin_withExistingEmail_linksToThatAccount() {
        User existing = existingUser(encoder.encode("123456"));
        when(userRepository.findByGoogleSub("google-123")).thenReturn(Optional.empty());

        OidcUser result = new GoogleOidcUserService(userService)
                .mapToStudyHubUser(googleUser("google-123", "student@gmail.com", true));

        assertEquals("student@gmail.com", result.getName(), "Principal name phải là email");
        assertEquals("google-123", existing.getGoogleSub());
        assertEquals("LOCAL", existing.getAuthProvider(), "Tài khoản cũ giữ nguyên, chỉ thêm liên kết Google");
        assertEquals("Nguyễn Văn An", existing.getFullName());
    }

    @Test
    void googleLogin_newEmail_createsGoogleAccount() {
        when(userRepository.findByGoogleSub("google-999")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("maianh.new@gmail.com")).thenReturn(Optional.empty());

        new GoogleOidcUserService(userService).mapToStudyHubUser(googleUser("google-999", "maianh.new@gmail.com", true));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("GOOGLE", saved.getValue().getAuthProvider());
        assertEquals("Trần Mai Anh", saved.getValue().getFullName());
        assertTrue(saved.getValue().getPassword().startsWith("{bcrypt}"), "Mật khẩu ngẫu nhiên vẫn phải được băm");
        assertNotNull(saved.getValue().getAvatarUrl());
    }

    @Test
    void googleLogin_unverifiedEmail_isRejected() {
        assertThrows(OAuth2AuthenticationException.class, () -> new GoogleOidcUserService(userService)
                .mapToStudyHubUser(googleUser("google-1", "student@gmail.com", false)));
        verify(userRepository, never()).save(any(User.class));
    }
}
