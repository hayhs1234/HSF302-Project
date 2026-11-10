package com.studyhub.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.substring(contextPath.length());

        // Các đường dẫn công khai (không cần đăng nhập)
        if (isPublicPath(path)) {
            return true;
        }

        // Kiểm tra phiên đăng nhập
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("currentUser") != null) {
            return true;
        }

        // Xử lý khi chưa đăng nhập
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || path.startsWith("/api/")
                || path.contains("-ajax")
                || (accept != null && accept.contains("application/json"))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"reply\":\"Phiên làm việc đã hết hạn hoặc bạn chưa đăng nhập. Vui lòng đăng nhập lại.\",\"message\":\"Phiên làm việc đã hết hạn hoặc bạn chưa đăng nhập. Vui lòng đăng nhập lại.\"}");
            return false;
        }

        // Chuyển hướng người dùng về trang giới thiệu kèm cờ yêu cầu đăng nhập
        response.sendRedirect(contextPath + "/?loginRequired=true");
        return false;
    }

    private boolean isPublicPath(String path) {
        return path.equals("/")
                || path.equals("/landing")
                || path.equals("/login")
                || path.equals("/register")
                || path.equals("/logout")
                || path.equals("/error")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/webjars/")
                || path.equals("/favicon.ico");
    }
}

