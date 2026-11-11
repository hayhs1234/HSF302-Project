package com.studyhub.service;

import com.studyhub.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cầu nối giữa bảng users và Spring Security (đăng nhập bằng email + mật khẩu).
 * Implement UserDetailsPasswordService để Spring Security tự nâng cấp hash cũ (SHA-256) lên BCrypt
 * ngay khi người dùng đăng nhập thành công.
 */
@Service
public class StudyHubUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserService userService;

    public StudyHubUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));
        return toUserDetails(user, user.getPassword());
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newEncodedPassword) {
        userService.updateEncodedPassword(user.getUsername(), newEncodedPassword);
        return org.springframework.security.core.userdetails.User.withUserDetails(user)
                .password(newEncodedPassword)
                .build();
    }

    public static UserDetails toUserDetails(User user, String encodedPassword) {
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(encodedPassword)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + roleOf(user))))
                .build();
    }

    public static String roleOf(User user) {
        return user.getRole() != null && !user.getRole().isBlank() ? user.getRole().toUpperCase() : "STUDENT";
    }
}
