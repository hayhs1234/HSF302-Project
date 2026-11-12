package com.studyhub.service;

import com.studyhub.ai.service.DocumentIndexingService;
import com.studyhub.dto.DocumentFormDto;
import com.studyhub.model.Document;
import com.studyhub.model.Subject;
import com.studyhub.model.User;
import com.studyhub.repository.DocumentRepository;
import com.studyhub.repository.SubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final SubjectRepository subjectRepository;
    private final DocumentIndexingService documentIndexingService;

    @Value("${studyhub.upload.dir:./uploads/}")
    private String uploadDir;

    public DocumentService(DocumentRepository documentRepository,
                           SubjectRepository subjectRepository) {
        this(documentRepository, subjectRepository, null);
    }

    @Autowired
    public DocumentService(DocumentRepository documentRepository,
                           SubjectRepository subjectRepository,
                           DocumentIndexingService documentIndexingService) {
        this.documentRepository = documentRepository;
        this.subjectRepository = subjectRepository;
        this.documentIndexingService = documentIndexingService;
    }

    public List<Document> searchDocuments(String keyword, Long subjectId, String category, Boolean favoriteOnly, String sortBy) {
        Sort sort = resolveSort(sortBy);
        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String trimmedCategory = (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("all")) ? category.trim() : null;
        return documentRepository.searchDocuments(trimmedKeyword, subjectId, trimmedCategory, favoriteOnly, sort);
    }

    public List<Document> searchPersonalDocuments(Long userId, String keyword, Long subjectId, String category, Boolean favoriteOnly, String sortBy) {
        Sort sort = resolveSort(sortBy);
        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String trimmedCategory = (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("all")) ? category.trim() : null;
        return documentRepository.searchPersonalDocuments(userId, trimmedKeyword, subjectId, trimmedCategory, favoriteOnly, sort);
    }

    public List<Document> searchSharedDocuments(String keyword, Long subjectId, String category, String sortBy) {
        Sort sort = resolveSort(sortBy);
        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String trimmedCategory = (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("all")) ? category.trim() : null;
        return documentRepository.searchSharedDocuments(trimmedKeyword, subjectId, trimmedCategory, sort);
    }

    public List<Document> getPersonalDocuments(Long userId) {
        return documentRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Sort resolveSort(String sortBy) {
        return switch (sortBy != null ? sortBy : "newest") {
            case "downloads" -> Sort.by(Sort.Direction.DESC, "downloadCount");
            case "views" -> Sort.by(Sort.Direction.DESC, "viewCount");
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    public Document createDocument(DocumentFormDto dto) throws IOException {
        return createDocument(dto, null);
    }

    public Document createDocument(DocumentFormDto dto, User user) throws IOException {
        validateDocumentDto(dto);
        Document doc = new Document();
        updateDocumentFields(doc, dto);
        doc.setUser(user);
        doc.setIsShared(false);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        Document saved = documentRepository.save(doc);
        scheduleIndexingAfterCommit(saved);
        return saved;
    }

    public Document updateDocument(Long id, DocumentFormDto dto) throws IOException {
        if (id == null) {
            throw new IllegalArgumentException("ID tài liệu không được để trống khi cập nhật");
        }
        validateDocumentDto(dto);
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu với ID: " + id));
        updateDocumentFields(doc, dto);
        doc.setUpdatedAt(LocalDateTime.now());
        Document saved = documentRepository.save(doc);
        scheduleIndexingAfterCommit(saved);
        return saved;
    }

    public Document shareDocument(Long documentId, User user, String note) {
        if (documentId == null) {
            throw new IllegalArgumentException("Vui lòng chọn tài liệu để chia sẻ");
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu với ID: " + documentId));
        verifyDocumentOwner(doc, user, "Bạn chỉ có thể chia sẻ tài liệu do chính mình tạo ra!");

        doc.setIsShared(true);
        doc.setSharedAt(LocalDateTime.now());
        doc.setSharedByName(user.getFullName());
        doc.setSharedNote((note != null && !note.trim().isEmpty()) ? note.trim() : null);
        return documentRepository.save(doc);
    }

    public Document unshareDocument(Long documentId, User user) {
        if (documentId == null) {
            throw new IllegalArgumentException("ID tài liệu không hợp lệ");
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu với ID: " + documentId));
        verifyDocumentOwner(doc, user, "Bạn chỉ có thể hủy chia sẻ tài liệu do chính mình tạo ra!");

        doc.setIsShared(false);
        doc.setSharedAt(null);
        doc.setSharedNote(null);
        return documentRepository.save(doc);
    }

    private void verifyDocumentOwner(Document doc, User user, String forbiddenMessage) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để thực hiện thao tác này");
        }
        if (doc.getUser() == null || !user.getId().equals(doc.getUser().getId())) {
            throw new IllegalArgumentException(forbiddenMessage);
        }
    }

    public Document pullDocumentToPersonal(Long sharedDocumentId, User targetUser) {
        if (sharedDocumentId == null) {
            throw new IllegalArgumentException("ID tài liệu chia sẻ không hợp lệ");
        }
        Document sharedDoc = documentRepository.findById(sharedDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu chia sẻ với ID: " + sharedDocumentId));

        if (!sharedDoc.getIsShared()) {
            throw new IllegalArgumentException("Tài liệu này hiện không còn trong kho chia sẻ");
        }

        if (targetUser != null && targetUser.getId() != null) {
            // Kiểm tra nếu chính người này đã sở hữu tài liệu gốc
            if (sharedDoc.getUser() != null && targetUser.getId().equals(sharedDoc.getUser().getId())) {
                throw new IllegalArgumentException("Tài liệu '" + sharedDoc.getTitle() + "' là do chính bạn chia sẻ, đã có sẵn trong kho riêng!");
            }
            // Kiểm tra nếu đã kéo về kho riêng từ trước
            if (documentRepository.existsByUserIdAndTitle(targetUser.getId(), sharedDoc.getTitle())) {
                throw new IllegalArgumentException("Bạn đã có tài liệu '" + sharedDoc.getTitle() + "' trong kho riêng rồi!");
            }
        }

        // Tạo một bản sao về kho riêng của người dùng
        Document copy = new Document();
        copy.setTitle(sharedDoc.getTitle());
        copy.setFileName(sharedDoc.getFileName());
        copy.setFilePath(sharedDoc.getFilePath());
        copy.setFileUrl(sharedDoc.getFileUrl());
        copy.setFileExtension(sharedDoc.getFileExtension());
        copy.setFileSizeFormatted(sharedDoc.getFileSizeFormatted());
        copy.setFileSizeBytes(sharedDoc.getFileSizeBytes());
        copy.setCategory(sharedDoc.getCategory());
        copy.setAuthor(sharedDoc.getAuthor());
        copy.setDescription(sharedDoc.getDescription());
        copy.setTags(sharedDoc.getTags());
        copy.setSubject(sharedDoc.getSubject());
        copy.setUser(targetUser);
        copy.setIsFavorite(false);
        copy.setIsShared(false);
        copy.setDownloadCount(0);
        copy.setViewCount(0);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());

        sharedDoc.setDownloadCount(sharedDoc.getDownloadCount() + 1);
        documentRepository.save(sharedDoc);

        Document savedCopy = documentRepository.save(copy);
        scheduleIndexingAfterCommit(savedCopy);
        return savedCopy;
    }

    /**
     * Chỉ lập chỉ mục sau khi transaction lưu Document đã commit xong để tránh xung đột khóa
     * giữa transaction hiện tại và transaction độc lập (REQUIRES_NEW) trong DocumentIndexingService.
     */
    private void scheduleIndexingAfterCommit(Document saved) {
        if (documentIndexingService == null || saved == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentIndexingService.indexDocument(saved);
                }
            });
        } else {
            documentIndexingService.indexDocument(saved);
        }
    }

    public void validateDocumentDto(DocumentFormDto dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên tài liệu không được để trống");
        }
        if (dto.getSubjectId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn môn học cho tài liệu");
        }
        if (!subjectRepository.existsById(dto.getSubjectId())) {
            throw new IllegalArgumentException("Môn học với ID " + dto.getSubjectId() + " không tồn tại trong hệ thống");
        }
        if (dto.getFileUrl() != null && !dto.getFileUrl().trim().isEmpty()) {
            String url = dto.getFileUrl().trim();
            if (!isValidHttpUrl(url)) {
                throw new IllegalArgumentException("Đường dẫn URL không hợp lệ: " + url + " (phải bắt đầu bằng http:// hoặc https://)");
            }
        }
    }

    public static boolean isValidHttpUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return true;
        }
        try {
            java.net.URI uri = new java.net.URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateDocumentFields(Document doc, DocumentFormDto dto) throws IOException {
        doc.setTitle(dto.getTitle().trim());
        doc.setCategory(dto.getCategory());
        doc.setAuthor((dto.getAuthor() != null && !dto.getAuthor().trim().isEmpty()) ? dto.getAuthor().trim() : "Chưa rõ tác giả");
        doc.setDescription(dto.getDescription());
        doc.setTags(dto.getTags());
        doc.setFileUrl((dto.getFileUrl() != null && !dto.getFileUrl().trim().isEmpty()) ? dto.getFileUrl().trim() : null);
        doc.setIsFavorite(dto.getIsFavorite());

        Subject subject = subjectRepository.findById(dto.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Môn học với ID " + dto.getSubjectId() + " không tồn tại trong hệ thống"));
        doc.setSubject(subject);

        MultipartFile file = dto.getFile();
        if (file != null && !file.isEmpty()) {
            String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
            String extension = extractExtension(originalFileName);

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;
            Path targetLocation = uploadPath.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            doc.setFileName(originalFileName);
            doc.setFilePath(targetLocation.toString());
            doc.setFileExtension(extension.toUpperCase());
            doc.setFileSizeBytes(file.getSize());
            doc.setFileSizeFormatted(formatFileSize(file.getSize()));
        } else if (doc.getFileExtension() == null || doc.getFileExtension().isEmpty()) {
            String ext = (dto.getFileExtension() != null && !dto.getFileExtension().isEmpty()) ? dto.getFileExtension() : "PDF";
            doc.setFileExtension(ext.toUpperCase());
            doc.setFileName(dto.getTitle() + "." + ext.toLowerCase());
            doc.setFileSizeFormatted("2.4 MB");
            doc.setFileSizeBytes(2516582L);
        }
    }

    public boolean toggleFavorite(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu với ID: " + id));
        boolean newStatus = !doc.getIsFavorite();
        doc.setIsFavorite(newStatus);
        documentRepository.save(doc);
        return newStatus;
    }

    public void incrementDownloadCount(Long id) {
        documentRepository.findById(id).ifPresent(doc -> {
            doc.setDownloadCount(doc.getDownloadCount() + 1);
            documentRepository.save(doc);
        });
    }

    public void incrementViewCount(Long id) {
        documentRepository.findById(id).ifPresent(doc -> {
            doc.setViewCount(doc.getViewCount() + 1);
            documentRepository.save(doc);
        });
    }

    public boolean deleteDocument(Long id) {
        if (id == null || !documentRepository.existsById(id)) {
            return false;
        }
        if (documentIndexingService != null) {
            documentIndexingService.deleteDocumentChunks(id);
        }
        documentRepository.deleteById(id);
        return true;
    }

    public long countTotal() {
        return documentRepository.count();
    }

    public long countPersonalTotal(Long userId) {
        if (userId == null) return countTotal();
        return documentRepository.countByUserIdOrNull(userId);
    }

    public long countPersonalFavorites(Long userId) {
        if (userId == null) return countFavorites();
        return documentRepository.countFavoritesByUserId(userId);
    }

    public long countPersonalSubjects(Long userId) {
        if (userId == null) return countSubjectsWithDocs();
        return documentRepository.countDistinctSubjectsByUserId(userId);
    }

    public long countFavorites() {
        return documentRepository.countByIsFavoriteTrue();
    }

    public long countSharedTotal() {
        return documentRepository.countByIsSharedTrue();
    }

    public long countSubjectsWithDocs() {
        return documentRepository.countDistinctSubjectsWithDocuments();
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "PDF";
    }

    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
