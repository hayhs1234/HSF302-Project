package com.studyhub.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/dashboard";
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Tham số yêu cầu không hợp lệ hoặc không đúng định dạng!");
        return "redirect:/dashboard";
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                           HttpServletRequest request,
                                           RedirectAttributes redirectAttributes) {
        String uri = request != null ? request.getRequestURI() : "";
        if (uri.startsWith("/api/")) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(Map.of("success", false, "message", "Phương thức " + ex.getMethod() + " không được hỗ trợ cho endpoint này."));
        }
        redirectAttributes.addFlashAttribute("errorMessage", "Đường dẫn không hỗ trợ phương thức " + ex.getMethod() + "!");
        return "redirect:/dashboard";
    }
}

