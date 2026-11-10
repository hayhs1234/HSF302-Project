package com.studyhub.config;

import com.studyhub.ai.service.DocumentIndexingService;
import com.studyhub.model.Document;
import com.studyhub.model.Subject;
import com.studyhub.model.User;
import com.studyhub.repository.DocumentRepository;
import com.studyhub.repository.SubjectRepository;
import com.studyhub.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SubjectRepository subjectRepository;
    private final DocumentRepository documentRepository;
    private final UserService userService;
    private final SampleDocumentFileGenerator sampleFileGenerator;
    private final DocumentIndexingService documentIndexingService;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(SubjectRepository subjectRepository,
                           DocumentRepository documentRepository,
                           UserService userService,
                           SampleDocumentFileGenerator sampleFileGenerator,
                           DocumentIndexingService documentIndexingService,
                           JdbcTemplate jdbcTemplate) {
        this.subjectRepository = subjectRepository;
        this.documentRepository = documentRepository;
        this.userService = userService;
        this.sampleFileGenerator = sampleFileGenerator;
        this.documentIndexingService = documentIndexingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureAccentInsensitiveCollation();

        // 1. Khởi tạo tài khoản mẫu (sinh viên có thể đăng nhập ngay hoặc đăng ký mới)
        User student = userService.getOrCreateDefaultUser("Nguyễn Văn An", "student@gmail.com", "123456");
        User secondStudent = userService.getOrCreateDefaultUser("Trần Mai Anh", "maianh@gmail.com", "123456");

        // 2. Khởi tạo các môn học đa dạng ngành học (Kinh tế, Kỹ thuật, CNTT, Ngôn ngữ, Xã hội)
        Subject ctdl = getOrCreateSubject("CTDL-GT", "Cấu trúc dữ liệu & Giải thuật", "Cây AVL, đồ thị, quy hoạch động và phân tích độ phức tạp thuật toán", "#0d6efd", "bi-diagram-3");
        Subject csdl = getOrCreateSubject("CSDL", "Cơ sở dữ liệu", "Hệ quản trị CSDL, thiết kế quan hệ, chuẩn hóa dữ liệu và SQL nâng cao", "#198754", "bi-database");
        Subject ktpm = getOrCreateSubject("KT-PM", "Kiểm thử phần mềm", "Quy trình kiểm thử, test case, kiểm thử tự động với JUnit và Selenium", "#dc3545", "bi-check2-circle");
        Subject xstk = getOrCreateSubject("XSTK", "Xác suất thống kê", "Biến ngẫu nhiên, phân phối xác suất, ước lượng tham số và hồi quy tuyến tính", "#ffc107", "bi-calculator");
        Subject mmt = getOrCreateSubject("MMT", "Mạng máy tính", "Mô hình OSI, giao thức TCP/UDP, cấu hình thiết bị định tuyến Cisco", "#0dcaf0", "bi-hdd-network");
        Subject ktmt = getOrCreateSubject("KTMT", "Kiến trúc máy tính", "Tập lệnh RISC/CISC, pipeline CPU và cấu trúc phân cấp bộ nhớ", "#6f42c1", "bi-cpu");
        Subject prj = getOrCreateSubject("PRJ301", "Lập trình Java Web", "Xây dựng ứng dụng web với Spring Boot, Hibernate JPA, REST API và Thymeleaf", "#fd7e14", "bi-code-slash");
        Subject aiml = getOrCreateSubject("AI-ML", "Trí tuệ nhân tạo & Machine Learning", "Các thuật toán học máy, mạng nơ-ron hồi quy và thị giác máy tính", "#20c997", "bi-cpu-fill");

        // Môn học đại cương và chuyên ngành Kinh tế / Xã hội / Ngôn ngữ
        Subject ktvm = getOrCreateSubject("KTVM", "Kinh tế vi mô", "Lý thuyết cung cầu, hành vi người tiêu dùng, chi phí sản xuất và cạnh tranh thị trường", "#198754", "bi-graph-up-arrow");
        Subject mkt = getOrCreateSubject("MKT101", "Marketing căn bản", "Nghiên cứu thị trường, chiến lược 4P, hành vi khách hàng và tiếp thị số", "#fd7e14", "bi-megaphone");
        Subject tacn = getOrCreateSubject("ENG201", "Tiếng Anh chuyên ngành", "Từ vựng học thuật, kỹ năng đọc hiểu tài liệu nghiên cứu và thuyết trình chuyên môn", "#0dcaf0", "bi-translate");
        Subject pldc = getOrCreateSubject("PLDC", "Pháp luật đại cương", "Hệ thống pháp luật Việt Nam, quyền công dân, quy phạm pháp luật và hợp đồng kinh tế", "#6f42c1", "bi-bank");

        // 3. Khởi tạo tài liệu học tập mẫu - Tuyệt đối không nhân đôi dữ liệu khi chạy lại
        initDocument(
                "Giao_trinh_Giai_thuat_Nang_cao.pdf",
                "Giao_trinh_Giai_thuat_Nang_cao.pdf",
                "PDF", "14.2 MB", 14889984L,
                "Giáo trình & Bài giảng", "PGS. TS. Trần Hùng",
                "Giáo trình chuyên sâu bao gồm cây cân bằng (AVL, Red-Black), đồ thị có trọng số, thuật toán Dijkstra, Floyd-Warshall, Kruskal và các bài toán quy hoạch động kinh điển.",
                "#ThuatToan #DoThi #DoPhucTap #QuyHoachDong",
                "https://drive.google.com/file/d/1A2b3C4d5E6F7g_Algorithms_Advanced/view",
                true, 342, 1250, ctdl, 1, student,
                true, "Nguyễn Văn An", "Tài liệu giải thuật nâng cao mình tổng hợp cho kỳ thi, chia sẻ cho mọi người cùng ôn tập!"
        );

        initDocument(
                "Slide_Chuong_4_Truy_van_SQL.pptx",
                "Slide_Chuong_4_Truy_van_SQL.pptx",
                "PPTX", "8.5 MB", 8912896L,
                "Slide thuyết trình", "TS. Lê Thị Mai",
                "Slide bài giảng tuần 4 môn Cơ sở dữ liệu: Các kỹ thuật JOIN nâng cao, truy vấn lồng (Subquery), Window Functions và cách đọc Query Execution Plan trong SQL Server.",
                "#SQL #Database #Subquery #JoinOptimization",
                "https://onedrive.live.com/view.aspx?cid=db_sql_lecture_week4",
                false, 185, 620, csdl, 3, student,
                true, "Nguyễn Văn An", "Slide bài giảng tuần 4 môn CSDL rất hay và dễ hiểu."
        );

        initDocument(
                "De_thi_giua_ky_Kiem_thu_phan_mem_2023.docx",
                "De_thi_giua_ky_Kiem_thu_phan_mem_2023.docx",
                "DOCX", "2.1 MB", 2202009L,
                "Đề thi & Đáp án", "Bộ môn Kỹ thuật Phần mềm",
                "Đề thi giữa kỳ môn Kiểm thử phần mềm kèm barem đáp án và ma trận đề thi phân bổ theo thang chuẩn đầu ra. Có bài tập thiết kế test case cho form thanh toán.",
                "#KiemThu #DeThi #DapAn #UnitTest #TestCase",
                "https://drive.google.com/file/d/midterm_exam_software_testing_2023/view",
                true, 412, 1530, ktpm, 5, secondStudent,
                true, "Trần Mai Anh", "Đề thi giữa kỳ môn Kiểm thử kèm đáp án chi tiết."
        );

        initDocument(
                "Tong_hop_Cong_thuc_Xac_Suat_Thong_Ke.pdf",
                "Tong_hop_Cong_thuc_Xac_Suat_Thong_Ke.pdf",
                "PDF", "4.8 MB", 5033164L,
                "Ghi chú & Khác", "CLB Học thuật Sinh viên",
                "Tài liệu tóm tắt công thức Xác suất và Thống kê trong 4 trang A4: biến ngẫu nhiên, phân phối chuẩn, Poisson, Student, khoảng tin cậy và kiểm định Z/T.",
                "#Toan #XacSuat #CongThuc #OnThiNhanh",
                "https://docs.google.com/document/d/prob_stats_cheat_sheet/view",
                true, 529, 2100, xstk, 7, student,
                true, "Nguyễn Văn An", "Bản tóm tắt công thức XSTK 4 trang A4 ôn thi siêu nhanh."
        );

        initDocument(
                "Giao_trinh_Kinh_te_Vi_mo_Dai_hoc.pdf",
                "Giao_trinh_Kinh_te_Vi_mo_Dai_hoc.pdf",
                "PDF", "11.5 MB", 12058624L,
                "Giáo trình & Bài giảng", "TS. Nguyễn Minh Châu",
                "Giáo trình toàn diện về Kinh tế vi mô dành cho sinh viên khối ngành Kinh tế, Quản trị và Kỹ thuật: Quy luật cung cầu, độ co giãn, lý thuyết hành vi người tiêu dùng.",
                "#KinhTe #ViMo #CungCau #ThiTruong",
                "https://drive.google.com/file/d/microeconomics_textbook/view",
                true, 480, 1820, ktvm, 2, secondStudent,
                true, "Trần Mai Anh", "Giáo trình Kinh tế vi mô chuẩn của trường, cực kỳ chi tiết!"
        );

        initDocument(
                "Slide_Chien_luoc_Marketing_4P_Can_ban.pptx",
                "Slide_Chien_luoc_Marketing_4P_Can_ban.pptx",
                "PPTX", "6.2 MB", 6501171L,
                "Slide thuyết trình", "ThS. Hoàng Thùy Linh",
                "Bộ slide bài giảng chi tiết về phối thức tiếp thị 4P (Product, Price, Place, Promotion) với các case study thực tế từ doanh nghiệp Việt Nam và quốc tế.",
                "#Marketing #Marketing4P #ChienLuoc #Branding",
                "https://drive.google.com/file/d/marketing_4p_slide/view",
                false, 290, 950, mkt, 4, student,
                false, null, null
        );

        initDocument(
                "De_cuong_On_tap_Phap_luat_Dai_cuong.docx",
                "De_cuong_On_tap_Phap_luat_Dai_cuong.docx",
                "DOCX", "1.8 MB", 1887436L,
                "Đề thi & Đáp án", "Bộ môn Khoa học Xã hội & Luật",
                "Bộ câu hỏi tự luận và trắc nghiệm ôn thi kết thúc học phần Pháp luật đại cương dành cho sinh viên tất cả các ngành đại học, kèm gợi ý đáp án chi tiết.",
                "#PhapLuat #DeThi #OnTap #DaiCuong",
                "https://drive.google.com/file/d/pldc_study_guide/view",
                true, 620, 2450, pldc, 6, student,
                false, null, null
        );

        initDocument(
                "Tong_hop_Tu_vung_Academic_English_Writing.pdf",
                "Tong_hop_Tu_vung_Academic_English_Writing.pdf",
                "PDF", "3.6 MB", 3774873L,
                "Ghi chú & Khác", "Ban Học tập Ngôn ngữ",
                "Sổ tay 500 từ vựng và cấu trúc câu học thuật (Academic Word List) phổ biến nhất để viết tiểu luận, khóa luận tốt nghiệp và báo cáo khoa học.",
                "#TiengAnh #English #AcademicWriting #TuVung",
                "https://docs.google.com/document/d/academic_english_cheatsheet/view",
                false, 340, 1120, tacn, 8, student,
                false, null, null
        );

        initDocument(
                "Bai_tap_lon_Mang_may_tinh_Cisco_PacketTracer.zip",
                "Bai_tap_lon_Mang_may_tinh_Cisco_PacketTracer.zip",
                "ZIP", "24.6 MB", 25794969L,
                "Ghi chú & Khác", "Nhóm SV Kỹ thuật Mạng",
                "File thiết kế topo mạng doanh nghiệp 3 tầng (Core, Distribution, Access) trên Cisco Packet Tracer kèm báo cáo word định tuyến OSPF, cấu hình VLAN và DHCP.",
                "#MangMayTinh #PacketTracer #Cisco #VLAN #OSPF",
                "https://github.com/studyhub-demo/cisco-packet-tracer-lab",
                false, 98, 410, mmt, 10, student,
                false, null, null
        );

        initDocument(
                "De_cuong_on_tap_Kien_truc_may_tinh.pdf",
                "De_cuong_on_tap_Kien_truc_may_tinh.pdf",
                "PDF", "3.4 MB", 3565158L,
                "Đề thi & Đáp án", "ThS. Hoàng Văn Bình",
                "Bộ 100 câu hỏi trắc nghiệm ôn thi kết thúc học phần Kiến trúc máy tính kèm giải thích chi tiết: tập lệnh MIPS, cơ chế pipelining, phân cấp cache nhớ L1/L2/L3.",
                "#KienTrucMayTinh #TracNghiem #DeCuong #MIPS",
                "https://drive.google.com/file/d/computer_arch_review_exam/view",
                false, 230, 890, ktmt, 12, student,
                false, null, null
        );

        initDocument(
                "Mau_Thiet_ke_Giao_dien_Nguoi_dung_Figma.pdf",
                "Mau_Thiet_ke_Giao_dien_Nguoi_dung_Figma.pdf",
                "PDF", "5.1 MB", 5347737L,
                "Ghi chú & Khác", "Trần Mai Anh - SV Thiết kế",
                "Tài liệu hướng dẫn Design System, bảng màu UI/UX, Typography, Grid 12 cột và components chuẩn trên Figma cho bài tập lớn ứng dụng web.",
                "#UIUX #Figma #DesignSystem #WebDesign",
                "https://www.figma.com/file/sample_studyhub_design_system",
                true, 380, 1420, prj, 14, student,
                false, null, null
        );

        // 4. Đảm bảo tất cả các tài liệu đều được trích xuất và lập chỉ mục RAG
        documentIndexingService.indexAllPendingDocuments();
    }

    private void ensureAccentInsensitiveCollation() {
        String[] alterSqls = {
                "ALTER TABLE documents ALTER COLUMN title NVARCHAR(255) COLLATE Latin1_General_CI_AI NOT NULL",
                "ALTER TABLE documents ALTER COLUMN author NVARCHAR(150) COLLATE Latin1_General_CI_AI NULL",
                "ALTER TABLE documents ALTER COLUMN description NVARCHAR(4000) COLLATE Latin1_General_CI_AI NULL",
                "ALTER TABLE documents ALTER COLUMN tags NVARCHAR(255) COLLATE Latin1_General_CI_AI NULL",
                "ALTER TABLE documents ALTER COLUMN shared_by_name NVARCHAR(150) COLLATE Latin1_General_CI_AI NULL",
                "ALTER TABLE documents ALTER COLUMN shared_note NVARCHAR(500) COLLATE Latin1_General_CI_AI NULL"
        };
        for (String sql : alterSqls) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception ignored) {
            }
        }
    }

    private Subject getOrCreateSubject(String code, String name, String description, String color, String icon) {
        return subjectRepository.findByCode(code).orElseGet(() -> {
            Subject s = new Subject(code, name, description, color, icon);
            return subjectRepository.save(s);
        });
    }

    private void initDocument(String title, String fileName, String ext, String sizeFormatted, long sizeBytes,
                              String category, String author, String description, String tags,
                              String fileUrl, boolean isFavorite, int downloadCount, int viewCount,
                              Subject subject, int daysAgo, User user,
                              boolean isShared, String sharedByName, String sharedNote) {
        String physicalPath = sampleFileGenerator.ensureSampleFileExists(fileName, ext);

        if (!documentRepository.existsByTitle(title)) {
            Document doc = new Document(
                    title, fileName, ext, sizeFormatted, sizeBytes,
                    category, author, description, tags,
                    isFavorite, downloadCount, viewCount, subject
            );
            doc.setUser(user);
            doc.setIsShared(isShared);
            if (isShared) {
                doc.setSharedAt(LocalDateTime.now().minusDays(daysAgo));
                doc.setSharedByName(sharedByName);
                doc.setSharedNote(sharedNote);
            }
            doc.setFileUrl(fileUrl);
            doc.setFilePath(physicalPath);
            doc.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
            doc.setUpdatedAt(LocalDateTime.now().minusDays(daysAgo));
            Document savedDoc = documentRepository.save(doc);
            documentIndexingService.indexDocument(savedDoc);
        } else {
            // Nếu tài liệu đã có sẵn từ trước, cập nhật thêm user, fileUrl và filePath nếu đang trống
            documentRepository.findFirstByTitle(title).ifPresent(existing -> {
                boolean changed = false;
                boolean needsReindex = false;
                if (existing.getUser() == null && user != null) {
                    existing.setUser(user);
                    changed = true;
                }
                if (isShared && !existing.getIsShared()) {
                    existing.setIsShared(true);
                    existing.setSharedAt(LocalDateTime.now().minusDays(daysAgo));
                    existing.setSharedByName(sharedByName);
                    existing.setSharedNote(sharedNote);
                    changed = true;
                }
                if ((existing.getFileUrl() == null || existing.getFileUrl().isEmpty()) && fileUrl != null) {
                    existing.setFileUrl(fileUrl);
                    changed = true;
                }
                if ((existing.getDescription() == null || existing.getDescription().isEmpty()) && description != null) {
                    existing.setDescription(description);
                    changed = true;
                }
                boolean hasValidFilePath = existing.getFilePath() != null
                        && !existing.getFilePath().isBlank()
                        && Files.exists(Paths.get(existing.getFilePath()));
                if (!hasValidFilePath && physicalPath != null) {
                    existing.setFilePath(physicalPath);
                    changed = true;
                    needsReindex = true;
                }
                if (changed) {
                    existing = documentRepository.save(existing);
                }
                if (needsReindex || !documentIndexingService.isDocumentIndexed(existing.getId())) {
                    documentIndexingService.indexDocument(existing);
                }
            });
        }
    }
}
