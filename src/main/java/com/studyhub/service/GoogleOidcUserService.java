package com.studyhub.service;

import com.studyhub.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Đăng nhập với Google (OpenID Connect): sau khi Google xác thực, ánh xạ sang tài khoản StudyHub.
 *  - Đã từng đăng nhập Google -> tìm theo "sub".
 *  - Email (đã được Google xác minh) trùng tài khoản có sẵn -> liên kết vào tài khoản đó.
 *  - Chưa có -> tạo tài khoản mới.
 * Tên đăng nhập (principal name) là email, giống đăng nhập bằng mật khẩu.
 */
@Service
public class GoogleOidcUserService extends OidcUserService {

    private final UserService userService;

    public GoogleOidcUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser googleUser = super.loadUser(userRequest);
        return mapToStudyHubUser(googleUser);
    }

    /** Tách riêng để kiểm thử không cần gọi Google */
    public OidcUser mapToStudyHubUser(OidcUser googleUser) {
        String email = googleUser.getEmail();
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(googleUser.getEmailVerified())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified",
                    "Tài khoản Google chưa xác minh email nên không thể đăng nhập StudyHub.", null));
        }

        User user = userService.findOrCreateGoogleUser(
                googleUser.getSubject(), email, googleUser.getFullName(), googleUser.getPicture());

        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_" + StudyHubUserDetailsService.roleOf(user))),
                googleUser.getIdToken(),
                googleUser.getUserInfo(),
                "email");
    }
}
