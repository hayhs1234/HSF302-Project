package com.studyhub.controller;

import com.studyhub.ai.service.DocumentIndexingService;
import com.studyhub.dto.DocumentFormDto;
import com.studyhub.model.User;
import com.studyhub.repository.DocumentRepository;
import jakarta.servlet.http.HttpSession;
import com.studyhub.model.Document;
import com.studyhub.model.Subject;
import com.studyhub.service.DocumentService;
import com.studyhub.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final SubjectService subjectService;
    private final DocumentRepository documentRepository;
    private final DocumentIndexingService documentIndexingService;

    public DocumentController(DocumentService documentService, SubjectService subjectService,
                              DocumentRepository documentRepository, DocumentIndexingService documentIndexingService) {
        this.documentService = documentService;
        this.subjectService = subjectService;
        this.documentRepository = documentRepository;
        this.documentIndexingService = documentIndexingService;
    }

    @GetMapping
    public String list() {
        return "redirect:/dashboard";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        List<Subject> subjects = subjectService.getAllSubjects();
        model.addAttribute("documentDto", new DocumentFormDto());
        model.addAttribute("subjects", subjects);
        model.addAttribute("isEdit", false);
        return "documents/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Document doc = documentService.getDocumentById(id).orElse(null);
        if (doc == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài liệu!");
            return "redirect:/dashboard";
        }

        DocumentFormDto dto = new DocumentFormDto();
        dto.setId(doc.getId());
        dto.setTitle(doc.getTitle());
        dto.setCategory(doc.getCategory());
        dto.setAuthor(doc.getAuthor());
        dto.setDescription(doc.getDescription());
        dto.setTags(doc.getTags());
        dto.setFileUrl(doc.getFileUrl());
        dto.setIsFavorite(doc.getIsFavorite());
        dto.setFileExtension(doc.getFileExtension());
        if (doc.getSubject() != null) {
            dto.setSubjectId(doc.getSubject().getId());
        }

        model.addAttribute("documentDto", dto);
        model.addAttribute("subjects", subjectService.getAllSubjects());
        model.addAttribute("isEdit", true);
        return "documents/form";
    }

    @PostMapping("/save")
    public String saveDocument(
            @Valid @ModelAttribute("documentDto") DocumentFormDto dto,
            BindingResult bindingResult,
            jakarta.servlet.http.HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("subjects", subjectService.getAllSubjects());
            model.addAttribute("isEdit", dto.getId() != null);
            return "documents/form";
        }

        try {
            com.studyhub.model.User currentUser = (session != null) ? (com.studyhub.model.User) session.getAttribute("currentUser") : null;
            if (dto.getId() != null) {
                documentService.updateDocument(dto.getId(), dto);
                redirectAttributes.addFlashAttribute("successMessage", "Cập nhật tài liệu thành công!");
            } else {
                documentService.createDocument(dto, currentUser);
                redirectAttributes.addFlashAttribute("successMessage", "Thêm mới tài liệu thành công vào kho riêng của bạn!");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/dashboard";
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi lưu file: " + e.getMessage());
            return "redirect:/dashboard";
        }

        return "redirect:/dashboard";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getDocumentDetailJson(@PathVariable Long id) {
        Document doc = documentService.getDocumentById(id).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Không tìm thấy tài liệu với ID: " + id));
        }

        documentService.incrementViewCount(id);

        Map<String, Object> response = new HashMap<>();
        response.put("id", doc.getId());
        response.put("title", doc.getTitle());
        response.put("fileName", doc.getFileName());
        response.put("fileExtension", doc.getFileExtension());
        response.put("fileSize", doc.getFileSizeFormatted());
        response.put("category", doc.getCategory());
        response.put("author", doc.getAuthor());
        response.put("description", doc.getDescription());
        response.put("tags", doc.getTags());
        response.put("fileUrl", doc.getFileUrl());
        response.put("isFavorite", doc.getIsFavorite());
        response.put("downloadCount", doc.getDownloadCount());
        response.put("viewCount", doc.getViewCount() + 1);
        response.put("createdAt", doc.getFormattedDate());
        response.put("badgeClass", doc.getBadgeClass());
        if (doc.getSubject() != null) {
            response.put("subjectName", doc.getSubject().getName());
            response.put("subjectCode", doc.getSubject().getCode());
        } else {
            response.put("subjectName", "Chưa phân loại");
            response.put("subjectCode", "CHUNG");
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/favorite/{id}")
    public String toggleFavorite(@PathVariable Long id,
                                 @RequestHeader(value = "Referer", required = false) String referer,
                                 RedirectAttributes redirectAttributes) {
        try {
            documentService.toggleFavorite(id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + (referer != null ? referer : "/");
    }

    @PostMapping("/favorite-ajax/{id}")
    @ResponseBody
    public ResponseEntity<?> toggleFavoriteAjax(@PathVariable Long id) {
        try {
            boolean newStatus = documentService.toggleFavorite(id);
            return ResponseEntity.ok(Map.of("success", true, "isFavorite", newStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteDocument(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        boolean deleted = documentService.deleteDocument(id);
        if (!deleted) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài liệu với ID: " + id + " để xóa!");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa tài liệu thành công!");
        }
        return "redirect:/dashboard";
    }

    /**
     * Mở tài liệu ngay trong trình duyệt (inline) để trích dẫn [n] trong chat có thể nhảy tới đúng trang: /documents/view/{id}#page=N
     */
    @GetMapping("/view/{id}")
    public ResponseEntity<Resource> viewDocumentInline(@PathVariable Long id, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        Document doc = documentRepository.findAccessibleByIdAndUser(id, currentUser.getId()).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        File physicalFile = documentIndexingService.resolvePhysicalFile(doc);
        if (physicalFile == null || !physicalFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        String ext = doc.getFileExtension() != null ? doc.getFileExtension().trim().toLowerCase() : "";
        MediaType type = switch (ext) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt", "md", "sql", "csv", "java", "py", "js" -> new MediaType("text", "plain", StandardCharsets.UTF_8);
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        String disposition = type.equals(MediaType.APPLICATION_OCTET_STREAM) ? "attachment" : "inline";
        String encodedName = java.net.URLEncoder.encode(physicalFile.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + encodedName)
                .contentType(type)
                .body(new FileSystemResource(physicalFile));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        Document doc = documentService.getDocumentById(id).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }

        documentService.incrementDownloadCount(id);

        String fileName = doc.getFileName() != null ? doc.getFileName() : doc.getTitle() + "." + doc.getFileExtension().toLowerCase();

        if (doc.getFilePath() != null) {
            File physicalFile = new File(doc.getFilePath());
            if (physicalFile.exists()) {
                Resource resource = new FileSystemResource(physicalFile);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            }
        }

        // Tạo nội dung mẫu nếu là dữ liệu ban đầu
        String sampleContent = "StudyHub - Tai lieu hoc tap\n"
                + "Tieu de: " + doc.getTitle() + "\n"
                + "Mon hoc: " + (doc.getSubject() != null ? doc.getSubject().getName() : "Chung") + "\n"
                + "Tac gia / Giang vien: " + doc.getAuthor() + "\n"
                + "Phan loai: " + doc.getCategory() + "\n"
                + "Mo ta: " + doc.getDescription() + "\n\n"
                + "Noi dung tai lieu dang duoc cap nhat day du tren he thong StudyHub.\n";

        ByteArrayResource resource = new ByteArrayResource(sampleContent.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}

