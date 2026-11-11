package com.studyhub;

import com.studyhub.config.SecurityConfig;
import com.studyhub.model.User;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Kiểm thử tích hợp cấu hình Spring Security (chạy toàn bộ ứng dụng, dùng tài khoản mẫu student@gmail.com).
 */
@SpringBootTest
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void publicPages_areAccessibleWithoutLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mvc.perform(get("/js/csrf.js")).andExpect(status().isOk());
    }

    @Test
    void protectedPage_redirectsToLandingWhenAnonymous() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?loginRequired=true"));
        mvc.perform(get("/exam"))
                .andExpect(redirectedUrl("/?loginRequired=true"));
    }

    @Test
    void api_returns401JsonWhenAnonymous() throws Exception {
        mvc.perform(get("/api/ai/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void postWithoutCsrfToken_isForbidden() throws Exception {
        mvc.perform(post("/login").param("email", "student@gmail.com").param("password", "123456"))
                .andExpect(status().isForbidden());
    }

    @Test
    void formLogin_success_setsCurrentUserAndRedirectsToDashboard() throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf())
                        .param("email", "student@gmail.com").param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);
        assertNotNull(session);
        User current = (User) session.getAttribute(SecurityConfig.SESSION_USER);
        assertNotNull(current, "Sau khi đăng nhập phải có currentUser trong session cho các controller cũ");
        assertEquals("student@gmail.com", current.getEmail());
        assertTrue(current.getPassword().startsWith("{bcrypt}"), "Mật khẩu tài khoản mẫu phải ở dạng BCrypt");
    }

    @Test
    void formLogin_wrongPassword_redirectsWithError() throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf())
                        .param("email", "student@gmail.com").param("password", "sai_mat_khau"))
                .andExpect(redirectedUrl("/?loginError=true"))
                .andReturn();

        // Thông báo lỗi được lưu dạng flash attribute trong session để trang chủ hiển thị sau khi chuyển hướng
        @SuppressWarnings("unchecked")
        List<FlashMap> flashMaps = (List<FlashMap>) result.getRequest().getSession(false)
                .getAttribute(SessionFlashMapManager.class.getName() + ".FLASH_MAPS");
        assertNotNull(flashMaps);
        assertTrue(flashMaps.stream().anyMatch(f -> f.containsKey("loginErrorMessage")));
    }

    @Test
    void loggedInUser_canOpenProtectedPages_andLogoutViaPost() throws Exception {
        MvcResult login = mvc.perform(post("/login").with(csrf())
                        .param("email", "student@gmail.com").param("password", "123456"))
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/exam").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/ai/conversations").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(redirectedUrl("/?logoutSuccess=true"));
        assertTrue(session.isInvalid(), "Đăng xuất phải hủy session");
    }
}
