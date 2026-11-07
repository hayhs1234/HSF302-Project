package com.studyhub.controller;

import com.studyhub.model.Document;
import com.studyhub.model.Subject;
import com.studyhub.model.User;
import com.studyhub.service.DocumentService;
import com.studyhub.service.SubjectService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/shared-documents")
public class SharedDocumentController {

    private final DocumentService documentService;
    private final SubjectService subjectService;

    public SharedDocumentController(DocumentService documentService, SubjectService subjectService) {
        this.documentService = documentService;
        this.subjectService = subjectService;
    }

    @GetMapping
    public String sharedDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            HttpSession session,
            Model model) {

        User currentUser = (User) session.getAttribute("currentUser");
        model.addAttribute("currentUser", currentUser);

        List<Document> sharedDocs = documentService.searchSharedDocuments(keyword, subjectId, category, sortBy);
        List<Subject> subjects = subjectService.getAllSubjects();

        // Danh sách tài liệu cá nhân của người dùng để chọn chia sẻ lên kho chung
        List<Document> myDocs = (currentUser != null) ? documentService.getPersonalDocuments(currentUser.getId()) : List.of();
        Set<String> myDocTitles = myDocs.stream().map(Document::getTitle).collect(Collectors.toSet());

        model.addAttribute("sharedDocs", sharedDocs);
        model.addAttribute("myDocs", myDocs);
        model.addAttribute("myDocTitles", myDocTitles);
        model.addAttribute("subjects", subjects);
        model.addAttribute("totalSharedCount", documentService.countSharedTotal());

        // Filter state
        model.addAttribute("currentKeyword", keyword);
        model.addAttribute("currentSubjectId", subjectId);
        model.addAttribute("currentCategory", category);
        model.addAttribute("currentSortBy", sortBy);

        return "shared-documents";
    }

    @PostMapping("/share")
    public String shareDocument(
            @RequestParam Long documentId,
            @RequestParam(required = false) String note,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("currentUser");
        try {
            Document doc = documentService.shareDocument(documentId, currentUser, note);
            redirectAttributes.addFlashAttribute("successMessage", "Đã chia sẻ tài liệu '" + doc.getTitle() + "' lên kho chung thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/shared-documents";
    }

    @PostMapping("/unshare/{id}")
    public String unshareDocument(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("currentUser");
        try {
            Document doc = documentService.unshareDocument(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy chia sẻ tài liệu '" + doc.getTitle() + "' khỏi kho chung.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/shared-documents";
    }

    @PostMapping("/pull/{id}")
    public String pullDocument(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("currentUser");
        try {
            Document copy = documentService.pullDocumentToPersonal(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã kéo tài liệu '" + copy.getTitle() + "' về kho riêng của bạn thành công! Bạn có thể xem trong trang Tổng quan.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/shared-documents";
    }
}

