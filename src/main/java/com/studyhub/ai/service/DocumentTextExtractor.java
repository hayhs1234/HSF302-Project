package com.studyhub.ai.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
            "txt", "md", "sql", "csv", "json", "xml", "java", "py", "js", "html", "css", "properties", "yml", "yaml"
    );

    private static final Set<String> UNSUPPORTED_BINARY_EXTENSIONS = Set.of(
            "zip", "rar", "7z", "tar", "gz", "exe", "dll", "png", "jpg", "jpeg", "gif", "webp", "mp4", "mp3", "iso"
    );

    public boolean isSupportedExtension(String fileExtension) {
        if (fileExtension == null || fileExtension.isBlank()) return false;
        String ext = fileExtension.trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) ext = ext.substring(1);
        if (UNSUPPORTED_BINARY_EXTENSIONS.contains(ext)) return false;
        return ext.equals("pdf") || ext.equals("docx") || ext.equals("pptx") || PLAIN_TEXT_EXTENSIONS.contains(ext);
    }

    /**
     * Một trang (PDF) hoặc một slide (PPTX) đã trích xuất văn bản.
     * pageNumber = null với định dạng không có khái niệm trang (DOCX, TXT...).
     */
    public record PageText(Integer pageNumber, String text) {
    }

    /**
     * Trích xuất văn bản theo từng trang/slide để có thể trích dẫn chính xác vị trí trong tài liệu gốc.
     */
    public List<PageText> extractPages(File file, String fileExtension) {
        if (file == null || !file.exists() || !file.canRead()) {
            return Collections.emptyList();
        }
        String ext = (fileExtension != null && !fileExtension.isBlank()
                ? fileExtension
                : getExtensionFromName(file.getName())).trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) ext = ext.substring(1);

        try {
            if (ext.equals("pdf")) {
                return extractPdfPages(file);
            }
            if (ext.equals("pptx")) {
                return extractPptxSlides(file);
            }
        } catch (Exception e) {
            log.error("Lỗi trích xuất theo trang từ tệp {}: {}", file.getName(), e.getMessage());
            return Collections.emptyList();
        }

        String whole = extractText(file, ext);
        return whole.isBlank() ? Collections.emptyList() : List.of(new PageText(null, whole));
    }

    private List<PageText> extractPdfPages(File file) throws Exception {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file)) {
            if (document.isEncrypted()) {
                log.warn("Tệp PDF bị khóa mật khẩu: {}", file.getName());
                return pages;
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int total = document.getNumberOfPages();
            for (int p = 1; p <= total; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = cleanExtractedText(stripper.getText(document));
                if (!text.isBlank()) {
                    pages.add(new PageText(p, text));
                }
            }
        }
        return pages;
    }

    private List<PageText> extractPptxSlides(File file) throws Exception {
        List<PageText> slides = new ArrayList<>();
        try (InputStream is = new FileInputStream(file);
             XMLSlideShow ppt = new XMLSlideShow(is)) {
            int slideNum = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                StringBuilder slideText = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            slideText.append(text.trim()).append("\n");
                        }
                    }
                }
                String cleaned = cleanExtractedText(slideText.toString());
                if (!cleaned.isBlank()) {
                    slides.add(new PageText(slideNum, cleaned));
                }
                slideNum++;
            }
        }
        return slides;
    }

    /**
     * Trích xuất toàn bộ nội dung văn bản từ một tệp vật lý trên đĩa
     */
    public String extractText(File file, String fileExtension) {
        if (file == null || !file.exists() || !file.canRead()) {
            log.warn("Tệp không tồn tại hoặc không thể đọc: {}", file != null ? file.getAbsolutePath() : "null");
            return "";
        }

        String ext = (fileExtension != null && !fileExtension.isBlank()
                ? fileExtension
                : getExtensionFromName(file.getName())).trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) ext = ext.substring(1);

        if (UNSUPPORTED_BINARY_EXTENSIONS.contains(ext)) {
            return "";
        }

        try {
            return switch (ext) {
                case "pdf" -> extractFromPdf(file);
                case "docx" -> extractFromDocx(file);
                case "pptx" -> extractFromPptx(file);
                default -> {
                    if (PLAIN_TEXT_EXTENSIONS.contains(ext)) {
                        yield extractFromPlainText(file.toPath());
                    }
                    yield "";
                }
            };
        } catch (Exception e) {
            log.error("Lỗi trích xuất văn bản từ tệp {}: {}", file.getName(), e.getMessage());
            return "";
        }
    }

    private String extractFromPdf(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            if (document.isEncrypted()) {
                log.warn("Tệp PDF bị khóa mật khẩu: {}", file.getName());
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return cleanExtractedText(stripper.getText(document));
        } catch (Exception e) {
            log.error("Lỗi đọc PDF [{}]: {}", file.getName(), e.getMessage());
            return "";
        }
    }

    private String extractFromDocx(File file) {
        try (InputStream is = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return cleanExtractedText(extractor.getText());
        } catch (Exception e) {
            log.error("Lỗi đọc DOCX [{}]: {}", file.getName(), e.getMessage());
            return "";
        }
    }

    private String extractFromPptx(File file) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = new FileInputStream(file);
             XMLSlideShow ppt = new XMLSlideShow(is)) {
            int slideNum = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                StringBuilder slideText = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            slideText.append(text.trim()).append("\n");
                        }
                    }
                }
                if (!slideText.isEmpty()) {
                    sb.append("[Slide ").append(slideNum).append("]\n")
                      .append(slideText).append("\n");
                }
                slideNum++;
            }
            return cleanExtractedText(sb.toString());
        } catch (Exception e) {
            log.error("Lỗi đọc PPTX [{}]: {}", file.getName(), e.getMessage());
            return "";
        }
    }

    private String extractFromPlainText(Path path) {
        try {
            return cleanExtractedText(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception e) {
            try {
                return cleanExtractedText(Files.readString(path, StandardCharsets.ISO_8859_1));
            } catch (Exception ex) {
                log.error("Lỗi đọc file văn bản thuần [{}]: {}", path, ex.getMessage());
                return "";
            }
        }
    }

    private String cleanExtractedText(String raw) {
        if (raw == null) return "";
        return raw.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String getExtensionFromName(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
