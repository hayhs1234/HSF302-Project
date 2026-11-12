package com.studyhub;

import com.studyhub.dto.DocumentFormDto;
import com.studyhub.model.Document;
import com.studyhub.model.Subject;
import com.studyhub.model.User;
import com.studyhub.repository.DocumentRepository;
import com.studyhub.repository.SubjectRepository;
import com.studyhub.service.DocumentService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocumentValidationAndIdTest {

    private Validator validator;
    private DocumentRepository documentRepository;
    private SubjectRepository subjectRepository;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        documentRepository = mock(DocumentRepository.class);
        subjectRepository = mock(SubjectRepository.class);
        documentService = new DocumentService(documentRepository, subjectRepository);
    }

    @Test
    @DisplayName("Chặn tên tài liệu để trống")
    void testTitleBlankValidation() {
        DocumentFormDto dto = new DocumentFormDto();
        dto.setTitle("   ");
        dto.setSubjectId(1L);
        dto.setCategory("Giáo trình & Bài giảng");

        Set<ConstraintViolation<DocumentFormDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Phải báo lỗi khi tên tài liệu trống");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));

        assertThrows(IllegalArgumentException.class, () -> documentService.createDocument(dto));
    }

    @Test
    @DisplayName("Chặn môn học để trống hoặc null")
    void testSubjectNullValidation() {
        DocumentFormDto dto = new DocumentFormDto();
        dto.setTitle("Giao_trinh_Toan.pdf");
        dto.setSubjectId(null);
        dto.setCategory("Giáo trình & Bài giảng");

        Set<ConstraintViolation<DocumentFormDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Phải báo lỗi khi môn học trống");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("subjectId")));

        assertThrows(IllegalArgumentException.class, () -> documentService.createDocument(dto));
    }

    @Test
    @DisplayName("Chặn URL không hợp lệ (không phải http/https)")
    void testInvalidUrlValidation() {
        DocumentFormDto dto = new DocumentFormDto();
        dto.setTitle("Giao_trinh_Toan.pdf");
        dto.setSubjectId(1L);
        dto.setCategory("Giáo trình & Bài giảng");
        dto.setFileUrl("chuoi_url_linh_tinh");

        Set<ConstraintViolation<DocumentFormDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Phải báo lỗi khi URL không hợp lệ");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("fileUrl")));

        when(subjectRepository.existsById(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> documentService.createDocument(dto));
    }

    @Test
    @DisplayName("Cho phép URL hợp lệ bắt đầu bằng http:// hoặc https://")
    void testValidUrlValidation() throws IOException {
        DocumentFormDto dto = new DocumentFormDto();
        dto.setTitle("Giao_trinh_Toan.pdf");
        dto.setSubjectId(1L);
        dto.setCategory("Giáo trình & Bài giảng");
        dto.setFileUrl("https://drive.google.com/file/d/123/view");

        Set<ConstraintViolation<DocumentFormDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Không được báo lỗi khi URL hợp lệ");

        Subject subject = new Subject("MATH", "Toan hoc", "", "#000", "bi-book");
        when(subjectRepository.existsById(1L)).thenReturn(true);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document created = documentService.createDocument(dto);
        assertNotNull(created);
        assertEquals("https://drive.google.com/file/d/123/view", created.getFileUrl());
    }

    @Test
    @DisplayName("Cho phép URL để trống (tùy chọn)")
    void testEmptyUrlAllowed() {
        DocumentFormDto dto = new DocumentFormDto();
        dto.setTitle("Giao_trinh_Toan.pdf");
        dto.setSubjectId(1L);
        dto.setCategory("Giáo trình & Bài giảng");
        dto.setFileUrl("");

        Set<ConstraintViolation<DocumentFormDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "URL được phép để trống");
    }

    @Test
    @DisplayName("Xử lý ID không tồn tại khi sửa tài liệu")
    void testUpdateNonExistentId() {
        DocumentFormDto dto = new DocumentFormDto();
        dto.setTitle("Cap_nhat.pdf");
        dto.setSubjectId(1L);
        dto.setCategory("Giáo trình & Bài giảng");

        when(subjectRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findById(99999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                documentService.updateDocument(99999L, dto)
        );
        assertTrue(ex.getMessage().contains("Không tìm thấy tài liệu với ID: 99999"));
    }

    @Test
    @DisplayName("Xử lý ID không tồn tại khi xóa tài liệu")
    void testDeleteNonExistentId() {
        when(documentRepository.existsById(99999L)).thenReturn(false);

        boolean result = documentService.deleteDocument(99999L);
        assertFalse(result, "Xóa ID không tồn tại phải trả về false thay vì ném lỗi");
        verify(documentRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Xử lý xóa đúng bản ghi khi ID tồn tại")
    void testDeleteExistingId() {
        when(documentRepository.existsById(10L)).thenReturn(true);

        boolean result = documentService.deleteDocument(10L);
        assertTrue(result, "Xóa ID tồn tại phải trả về true");
        verify(documentRepository, times(1)).deleteById(10L);
    }

    @Test
    @DisplayName("Cập nhật đúng bản ghi khi sửa tài liệu hợp lệ")
    void testUpdateExistingDocument() throws IOException {
        Long docId = 5L;
        Subject oldSubject = new Subject("CSDL", "Co so du lieu", "", "#198754", "bi-database");
        oldSubject.setId(1L);
        Document existingDoc = new Document();
        existingDoc.setId(docId);
        existingDoc.setTitle("Giao trinh cu");
        existingDoc.setSubject(oldSubject);
        existingDoc.setCategory("Ghi chú & Khác");

        Subject newSubject = new Subject("CTDL", "Cau truc du lieu", "", "#0d6efd", "bi-diagram-3");
        newSubject.setId(2L);

        DocumentFormDto dto = new DocumentFormDto();
        dto.setId(docId);
        dto.setTitle("Giao trinh moi da sua");
        dto.setSubjectId(2L);
        dto.setCategory("Giáo trình & Bài giảng");
        dto.setFileUrl("https://example.com/slide.pdf");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(existingDoc));
        when(subjectRepository.existsById(2L)).thenReturn(true);
        when(subjectRepository.findById(2L)).thenReturn(Optional.of(newSubject));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document updated = documentService.updateDocument(docId, dto);
        assertNotNull(updated);
        assertEquals(docId, updated.getId(), "Phải cập nhật đúng bản ghi có ID này");
        assertEquals("Giao trinh moi da sua", updated.getTitle());
        assertEquals("CTDL", updated.getSubject().getCode());
        assertEquals("https://example.com/slide.pdf", updated.getFileUrl());
    }

    @Test
    @DisplayName("Không cho phép hủy chia sẻ tài liệu của người khác")
    void testUnshareDocument_nonOwnerRejected() {
        User owner = new User();
        owner.setId(2L);
        owner.setFullName("Trần Mai Anh");

        User otherUser = new User();
        otherUser.setId(1L);
        otherUser.setFullName("Nguyễn Văn An");

        Document sharedDoc = new Document();
        sharedDoc.setId(20L);
        sharedDoc.setTitle("De_thi_cua_Mai_Anh.docx");
        sharedDoc.setUser(owner);
        sharedDoc.setIsShared(true);

        when(documentRepository.findById(20L)).thenReturn(Optional.of(sharedDoc));

        assertThrows(IllegalArgumentException.class, () ->
                documentService.unshareDocument(20L, otherUser)
        );
        verify(documentRepository, never()).save(any(Document.class));
        assertTrue(sharedDoc.getIsShared());
    }

    @Test
    @DisplayName("Cho phép chủ sở hữu hủy chia sẻ tài liệu của chính mình")
    void testUnshareDocument_ownerAllowed() {
        User owner = new User();
        owner.setId(1L);
        owner.setFullName("Nguyễn Văn An");

        Document sharedDoc = new Document();
        sharedDoc.setId(21L);
        sharedDoc.setTitle("Tai_lieu_cua_An.pdf");
        sharedDoc.setUser(owner);
        sharedDoc.setIsShared(true);

        when(documentRepository.findById(21L)).thenReturn(Optional.of(sharedDoc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document result = documentService.unshareDocument(21L, owner);
        assertFalse(result.getIsShared());
        verify(documentRepository, times(1)).save(sharedDoc);
    }

    @Test
    @DisplayName("Không cho phép chia sẻ tài liệu thuộc sở hữu của người khác")
    void testShareDocument_nonOwnerRejected() {
        User owner = new User();
        owner.setId(2L);
        User otherUser = new User();
        otherUser.setId(1L);

        Document doc = new Document();
        doc.setId(22L);
        doc.setUser(owner);

        when(documentRepository.findById(22L)).thenReturn(Optional.of(doc));

        assertThrows(IllegalArgumentException.class, () ->
                documentService.shareDocument(22L, otherUser, "Ghi chú")
        );
        verify(documentRepository, never()).save(any(Document.class));
    }
}

