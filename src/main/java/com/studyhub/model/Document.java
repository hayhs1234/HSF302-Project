package com.studyhub.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @NotBlank(message = "Tiêu đề tài liệu không được để trống")
    @Column(nullable = false, length = 255)
    private String title;

    @Nationalized
    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Nationalized
    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "file_extension", length = 10)
    private String fileExtension = "PDF";

    @Column(name = "file_size_formatted", length = 20)
    private String fileSizeFormatted = "1.0 MB";

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes = 0L;

    @Nationalized
    @NotBlank(message = "Danh mục không được để trống")
    @Column(nullable = false, length = 100)
    private String category = "Giáo trình & Bài giảng";

    @Nationalized
    @Column(length = 150)
    private String author = "Chưa rõ tác giả";

    @Nationalized
    @Column(length = 4000)
    private String description;

    @Nationalized
    @Column(length = 255)
    private String tags;

    @Column(name = "is_favorite")
    private Boolean isFavorite = false;

    @Column(name = "download_count")
    private Integer downloadCount = 0;

    @Column(name = "view_count")
    private Integer viewCount = 0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "is_shared")
    private Boolean isShared = false;

    @Column(name = "shared_at")
    private LocalDateTime sharedAt;

    @Nationalized
    @Column(name = "shared_by_name", length = 150)
    private String sharedByName;

    @Nationalized
    @Column(name = "shared_note", length = 500)
    private String sharedNote;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Document() {
    }

    public Document(String title, String fileName, String fileExtension, String fileSizeFormatted,
                    Long fileSizeBytes, String category, String author, String description,
                    String tags, Boolean isFavorite, Integer downloadCount, Integer viewCount,
                    Subject subject) {
        this.title = title;
        this.fileName = fileName;
        this.fileExtension = fileExtension;
        this.fileSizeFormatted = fileSizeFormatted;
        this.fileSizeBytes = fileSizeBytes;
        this.category = category;
        this.author = author;
        this.description = description;
        this.tags = tags;
        this.isFavorite = isFavorite != null ? isFavorite : false;
        this.downloadCount = downloadCount != null ? downloadCount : 0;
        this.viewCount = viewCount != null ? viewCount : 0;
        this.subject = subject;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileSizeFormatted() {
        return fileSizeFormatted;
    }

    public void setFileSizeFormatted(String fileSizeFormatted) {
        this.fileSizeFormatted = fileSizeFormatted;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Boolean getIsFavorite() {
        return isFavorite != null && isFavorite;
    }

    public void setIsFavorite(Boolean favorite) {
        isFavorite = favorite;
    }

    public Integer getDownloadCount() {
        return downloadCount != null ? downloadCount : 0;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
    }

    public Integer getViewCount() {
        return viewCount != null ? viewCount : 0;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Boolean getIsShared() {
        return isShared != null && isShared;
    }

    public void setIsShared(Boolean shared) {
        isShared = shared;
    }

    public LocalDateTime getSharedAt() {
        return sharedAt;
    }

    public void setSharedAt(LocalDateTime sharedAt) {
        this.sharedAt = sharedAt;
    }

    public String getSharedByName() {
        return sharedByName;
    }

    public void setSharedByName(String sharedByName) {
        this.sharedByName = sharedByName;
    }

    public String getSharedNote() {
        return sharedNote;
    }

    public void setSharedNote(String sharedNote) {
        this.sharedNote = sharedNote;
    }

    public String getFormattedSharedDate() {
        if (sharedAt == null) return getFormattedDate();
        return sharedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public String getFormattedDate() {
        if (createdAt == null) return "";
        return createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public String getBadgeClass() {
        if (fileExtension == null) return "bg-secondary";
        return switch (fileExtension.toUpperCase()) {
            case "PDF" -> "bg-danger";
            case "DOC", "DOCX" -> "bg-primary";
            case "PPT", "PPTX" -> "bg-warning text-dark";
            case "XLS", "XLSX" -> "bg-success";
            case "ZIP", "RAR" -> "bg-secondary";
            default -> "bg-info text-dark";
        };
    }
}
