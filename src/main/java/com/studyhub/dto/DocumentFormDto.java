package com.studyhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class DocumentFormDto {

    private Long id;

    @NotBlank(message = "Tên tài liệu không được để trống")
    @Size(max = 255, message = "Tên tài liệu không được vượt quá 255 ký tự")
    private String title;

    @NotNull(message = "Vui lòng chọn môn học")
    private Long subjectId;

    @NotBlank(message = "Vui lòng chọn danh mục tài liệu")
    private String category = "Giáo trình & Bài giảng";

    private String author;

    private String description;

    private String tags;

    private Boolean isFavorite = false;

    private MultipartFile file;

    private String fileExtension;

    @Pattern(
        regexp = "^$|^(https?://)([a-zA-Z0-9.-]+|localhost)(:\\d+)?(/\\S*)?$",
        message = "Đường dẫn URL không hợp lệ (phải bắt đầu bằng http:// hoặc https:// và có tên miền hợp lệ)"
    )
    private String fileUrl;

    public DocumentFormDto() {
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
        this.title = (title != null) ? title.trim() : null;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
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

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = (fileUrl != null && !fileUrl.trim().isEmpty()) ? fileUrl.trim() : "";
    }
}

