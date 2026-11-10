package com.studyhub.controller;

import com.studyhub.dto.DocumentFormDto;
import com.studyhub.model.Document;
import com.studyhub.model.Subject;
import com.studyhub.model.User;
import com.studyhub.service.DocumentService;
import com.studyhub.service.SubjectService;
import com.studyhub.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class HomeController {

    private final DocumentService documentService;
    private final SubjectService subjectService;
    private final UserService userService;

    public HomeController(DocumentService documentService, SubjectService subjectService, UserService userService) {
        this.documentService = documentService;
        this.subjectService = subjectService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String landing(
            @RequestParam(required = false) Boolean loginRequired,
            @RequestParam(required = false) Boolean loginError,
            @RequestParam(required = false) Boolean registerError,
            @RequestParam(required = false) Boolean logoutSuccess,
            HttpSession session,
            Model model) {

        // Nếu người dùng đã đăng nhập từ trước, cho phép chuyển sang dashboard nếu họ muốn
        User currentUser = (session != null) ? (User) session.getAttribute("currentUser") : null;
        model.addAttribute("currentUser", currentUser);

        if (Boolean.TRUE.equals(loginRequired)) {
            model.addAttribute("loginRequiredMessage", "Vui lòng đăng nhập tài khoản của bạn để truy cập kho tài liệu học tập!");
        }

        model.addAttribute("totalDocs", documentService.countTotal());
        model.addAttribute("totalSubjects", subjectService.count());
        model.addAttribute("subjects", subjectService.getAllSubjects());
        return "landing";
    }

    @GetMapping("/landing")
    public String landingPage(
            @RequestParam(required = false) Boolean loginRequired,
            @RequestParam(required = false) Boolean loginError,
            @RequestParam(required = false) Boolean registerError,
            @RequestParam(required = false) Boolean logoutSuccess,
            HttpSession session,
            Model model) {
        return landing(loginRequired, loginError, registerError, logoutSuccess, session, model);
    }

    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false, defaultValue = "false") Boolean favoriteOnly,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            HttpSession session,
            Model model) {

        User currentUser = (User) session.getAttribute("currentUser");
        model.addAttribute("currentUser", currentUser);
        Long userId = (currentUser != null) ? currentUser.getId() : 1L;

        List<Document> documents = documentService.searchPersonalDocuments(userId, keyword, subjectId, category, favoriteOnly, sortBy);
        List<Subject> subjects = subjectService.getAllSubjects();

        // Dashboard Stats (Dành riêng cho kho cá nhân của sinh viên hiện tại)
        long totalDocs = documentService.countPersonalTotal(userId);
        long totalSubjects = documentService.countPersonalSubjects(userId);
        long favoriteCount = documentService.countPersonalFavorites(userId);

        model.addAttribute("documents", documents);
        model.addAttribute("subjects", subjects);
        model.addAttribute("totalDocs", totalDocs);
        model.addAttribute("totalSubjects", totalSubjects);
        model.addAttribute("favoriteCount", favoriteCount);

        // Filter state
        model.addAttribute("currentKeyword", keyword);
        model.addAttribute("currentSubjectId", subjectId);
        model.addAttribute("currentCategory", category);
        model.addAttribute("currentFavoriteOnly", favoriteOnly);
        model.addAttribute("currentSortBy", sortBy);

        // Model object for quick upload modal
        model.addAttribute("newDocument", new DocumentFormDto());

        return "index";
    }

    @GetMapping("/favorites")
    public String favorites(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false, defaultValue = "newest") String sortBy) {
        return "redirect:/dashboard?favoriteOnly=true" +
                (keyword != null ? "&keyword=" + keyword : "") +
                (subjectId != null ? "&subjectId=" + subjectId : "") +
                (!category.equals("all") ? "&category=" + category : "");
    }

    @GetMapping("/login")
    public String showLoginPage(HttpSession session) {
        if (session != null && session.getAttribute("currentUser") != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/?loginRequired=true";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            redirectAttributes.addFlashAttribute("loginErrorMessage", "Vui lòng nhập đầy đủ Email và Mật khẩu!");
            return "redirect:/?loginError=true";
        }

        Optional<User> userOpt = userService.authenticate(email, password);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            session.setAttribute("currentUser", user);
            redirectAttributes.addFlashAttribute("successMessage", "Đăng nhập thành công! Chào mừng " + user.getFullName() + " đến với StudyHub.");
            return "redirect:/dashboard";
        } else {
            redirectAttributes.addFlashAttribute("loginErrorMessage", "Email hoặc mật khẩu không chính xác! Vui lòng thử lại.");
            return "redirect:/?loginError=true";
        }
    }

    @GetMapping("/register")
    public String showRegisterPage(HttpSession session) {
        if (session != null && session.getAttribute("currentUser") != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/?registerRequired=true";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            User user = userService.register(fullName, email, password);
            session.setAttribute("currentUser", user);
            redirectAttributes.addFlashAttribute("successMessage", "Đăng ký tài khoản thành công! Chào mừng " + user.getFullName() + " đến với StudyHub.");
            return "redirect:/dashboard";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("registerErrorMessage", ex.getMessage());
            return "redirect:/?registerError=true";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        if (session != null) {
            session.invalidate();
        }
        redirectAttributes.addFlashAttribute("logoutMessage", "Bạn đã đăng xuất tài khoản thành công khỏi StudyHub.");
        return "redirect:/?logoutSuccess=true";
    }
}
