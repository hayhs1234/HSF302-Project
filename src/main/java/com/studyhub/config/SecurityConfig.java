package com.studyhub.config;

import com.studyhub.model.User;
import com.studyhub.service.GoogleOidcUserService;
import com.studyhub.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;

/**
 * Cấu hình Spring Security cho StudyHub.
 *  - Đăng nhập email/mật khẩu (form có sẵn ở trang chủ) và Đăng nhập với Google (OIDC, bật khi có client-id).
 *  - Sau khi xác thực, gán session attribute "currentUser" để các controller hiện có tiếp tục hoạt động.
 *  - CSRF lưu trong cookie XSRF-TOKEN; form Thymeleaf tự chèn token, fetch() gửi header X-XSRF-TOKEN (js/csrf.js).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    public static final String SESSION_USER = "currentUser";

    private static final String[] PUBLIC_PATHS = {
            "/", "/landing", "/login", "/register", "/error",
            "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico",
            "/oauth2/**", "/login/oauth2/**"
    };

    @Value("${studyhub.google.client-id:}")
    private String googleClientId;

    @Value("${studyhub.google.client-secret:}")
    private String googleClientSecret;

    private final UserService userService;
    private final GoogleOidcUserService googleOidcUserService;

    public SecurityConfig(UserService userService, GoogleOidcUserService googleOidcUserService) {
        this.userService = userService;
        this.googleOidcUserService = googleOidcUserService;
    }

    public boolean isGoogleLoginEnabled() {
        return googleClientId != null && !googleClientId.isBlank()
                && googleClientSecret != null && !googleClientSecret.isBlank();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .securityContext(ctx -> ctx.securityContextRepository(contextRepository))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler())
                        .failureHandler(loginFailureHandler("Email hoặc mật khẩu không chính xác! Vui lòng thử lại.")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler())
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .csrf(csrf -> csrf.spa())
                // Luôn tạo token CSRF để cookie XSRF-TOKEN có sẵn cho các lệnh fetch() ngay từ trang đầu tiên
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        if (isGoogleLoginEnabled()) {
            ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleClientId.trim())
                    .clientSecret(googleClientSecret.trim())
                    .build();
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .clientRegistrationRepository(new InMemoryClientRegistrationRepository(google))
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService))
                    .successHandler(loginSuccessHandler())
                    .failureHandler(loginFailureHandler("Đăng nhập với Google không thành công. Vui lòng thử lại.")));
            log.info("[StudyHub Security] Đăng nhập với Google: ĐÃ BẬT");
        } else {
            log.info("[StudyHub Security] Đăng nhập với Google: TẮT (chưa cấu hình GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET)");
        }
        return http.build();
    }

    /** Đăng nhập thành công (mật khẩu hoặc Google): nạp User vào session như trước và chuyển về Tổng quan */
    private AuthenticationSuccessHandler loginSuccessHandler() {
        return (request, response, authentication) -> {
            User user = userService.findByEmail(authentication.getName()).orElse(null);
            if (user == null) {
                response.sendRedirect(request.getContextPath() + "/?loginError=true");
                return;
            }
            request.getSession().setAttribute(SESSION_USER, user);
            flash(request, response, "successMessage",
                    "Đăng nhập thành công! Chào mừng " + user.getFullName() + " đến với StudyHub.");
            response.sendRedirect(request.getContextPath() + "/dashboard");
        };
    }

    private AuthenticationFailureHandler loginFailureHandler(String message) {
        return (request, response, exception) -> {
            flash(request, response, "loginErrorMessage", message);
            response.sendRedirect(request.getContextPath() + "/?loginError=true");
        };
    }

    private LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            flash(request, response, "logoutMessage", "Bạn đã đăng xuất tài khoản thành công khỏi StudyHub.");
            response.sendRedirect(request.getContextPath() + "/?logoutSuccess=true");
        };
    }

    /** Chưa đăng nhập: API/AJAX nhận 401 JSON (để giao diện báo lỗi), trang thường được chuyển về trang chủ */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            if (isApiRequest(request)) {
                String msg = "Phiên làm việc đã hết hạn hoặc bạn chưa đăng nhập. Vui lòng đăng nhập lại.";
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"reply\":\"" + msg + "\",\"message\":\"" + msg + "\"}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/?loginRequired=true");
        };
    }

    static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String accept = request.getHeader("Accept");
        return path.startsWith("/api/")
                || path.contains("-ajax")
                || "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (accept != null && (accept.contains("application/json") || accept.contains("text/event-stream")));
    }

    private static void flash(HttpServletRequest request, HttpServletResponse response, String key, String message) {
        FlashMap flashMap = new FlashMap();
        flashMap.put(key, message);
        new SessionFlashMapManager().saveOutputFlashMap(flashMap, request, response);
    }

    /** Đọc token CSRF ở mỗi request để Spring Security ghi cookie XSRF-TOKEN cho JavaScript */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
