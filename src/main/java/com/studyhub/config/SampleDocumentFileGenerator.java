package com.studyhub.config;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;

@Component
public class SampleDocumentFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(SampleDocumentFileGenerator.class);

    @Value("${studyhub.upload.dir:./uploads/}")
    private String uploadDir;

    private static final Map<String, String[]> SAMPLE_CONTENTS = createSampleContentMap();

    /**
     * Đảm bảo tệp vật lý của tài liệu mẫu tồn tại trong thư mục uploads và trả về đường dẫn tệp.
     */
    public String ensureSampleFileExists(String fileName, String extension) {
        if (fileName == null || fileName.isBlank()) return null;
        String ext = extension != null ? extension.trim().toUpperCase(Locale.ROOT) : "PDF";
        if ("ZIP".equals(ext) || "RAR".equals(ext)) {
            return null;
        }

        try {
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path targetPath = dirPath.resolve(fileName);
            File targetFile = targetPath.toFile();
            if (targetFile.exists() && targetFile.length() > 200) {
                return targetPath.toString();
            }

            String[] sections = SAMPLE_CONTENTS.get(fileName);
            if (sections == null || sections.length == 0) {
                return null;
            }

            switch (ext) {
                case "DOCX" -> createDocxFile(targetFile, sections);
                case "PPTX" -> createPptxFile(targetFile, sections);
                case "PDF" -> createPdfFile(targetFile, sections);
                default -> Files.writeString(targetPath, String.join("\n\n", sections));
            }

            log.info("Đã khởi tạo tệp mẫu vật lý: {} ({} bytes)", targetFile.getName(), targetFile.length());
            return targetPath.toString();
        } catch (Exception e) {
            log.error("Không thể tạo tệp mẫu cho {}: {}", fileName, e.getMessage(), e);
            return null;
        }
    }

    private void createDocxFile(File file, String[] sections) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(file)) {
            for (String section : sections) {
                String[] lines = section.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun run = p.createRun();
                    if (i == 0) {
                        run.setBold(true);
                        run.setFontSize(13);
                    } else {
                        run.setFontSize(11);
                    }
                    run.setText(lines[i]);
                }
            }
            doc.write(fos);
        }
    }

    private void createPptxFile(File file, String[] sections) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(file)) {
            for (String section : sections) {
                XSLFSlide slide = ppt.createSlide();
                XSLFTextBox box = slide.createTextBox();
                box.setAnchor(new Rectangle(40, 40, 640, 440));
                String[] lines = section.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    XSLFTextParagraph p = box.addNewTextParagraph();
                    XSLFTextRun run = p.addNewTextRun();
                    run.setText(lines[i]);
                    run.setFontSize(i == 0 ? 18.0 : 13.0);
                    run.setBold(i == 0);
                }
            }
            ppt.write(fos);
        }
    }

    private void createPdfFile(File file, String[] sections) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            PDFont font = loadUnicodeFontOrFallback(pdf);
            boolean unicodeSupported = !(font instanceof PDType1Font);

            for (String section : sections) {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.beginText();
                    cs.setFont(font, 11);
                    cs.setLeading(16f);
                    cs.newLineAtOffset(50, 780);

                    for (String rawLine : section.split("\n")) {
                        String line = unicodeSupported ? rawLine : stripAccents(rawLine);
                        for (String wrapped : wrapLine(line, 82)) {
                            cs.showText(wrapped);
                            cs.newLine();
                        }
                    }
                    cs.endText();
                }
            }
            pdf.save(file);
        }
    }

    private PDFont loadUnicodeFontOrFallback(PDDocument pdf) {
        String[] fontCandidates = {
                "C:\\Windows\\Fonts\\arial.ttf",
                "C:\\Windows\\Fonts\\tahoma.ttf",
                "C:\\Windows\\Fonts\\segoeui.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };
        for (String path : fontCandidates) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                try {
                    return PDType0Font.load(pdf, f);
                } catch (Exception ignored) {
                }
            }
        }
        return PDType1Font.HELVETICA;
    }

    private List<String> wrapLine(String line, int maxChars) {
        List<String> result = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            result.add("");
            return result;
        }
        String[] words = line.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.length() + w.length() + 1 > maxChars && !current.isEmpty()) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(" ");
            current.append(w);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private String stripAccents(String s) {
        if (s == null) return "";
        String nfd = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static Map<String, String[]> createSampleContentMap() {
        Map<String, String[]> map = new HashMap<>();

        map.put("Slide_Chien_luoc_Marketing_4P_Can_ban.pptx", new String[]{
                """
                CHƯƠNG 1: TỔNG QUAN VỀ MARKETING CĂN BẢN VÀ PHỐI THỨC MARKETING MIX (4P)
                Marketing là quá trình tạo dựng giá trị cho khách hàng và xây dựng mối quan hệ bền vững nhằm thu về giá trị từ khách hàng.
                Mô hình Marketing Mix 4P do E. Jerome McCarthy đề xuất gồm 4 thành tố cốt lõi:
                1. Product (Sản phẩm): Hàng hóa hoặc dịch vụ doanh nghiệp cung cấp ra thị trường để thỏa mãn nhu cầu của khách hàng mục tiêu.
                2. Price (Giá cả): Số tiền khách hàng phải chi trả để sở hữu hoặc sử dụng sản phẩm/dịch vụ.
                3. Place (Phân phối): Các kênh tiếp cận và địa điểm giúp đưa sản phẩm từ nhà sản xuất đến tay người tiêu dùng thuận tiện nhất.
                4. Promotion (Xúc tiến hỗn hợp / Truyền thông): Các hoạt động quảng cáo, PR, khuyến mãi, bán hàng cá nhân và Digital Marketing.
                """,
                """
                CHƯƠNG 2: CHIẾN LƯỢC SẢN PHẨM (PRODUCT) VÀ CHIẾN LƯỢC GIÁ (PRICE)
                I. Chiến lược Sản phẩm (Product):
                - Ba cấp độ của sản phẩm: Giá trị cốt lõi (Core Benefit), Sản phẩm thực tế (Actual Product: bao bì, thương hiệu, thiết kế, chất lượng) và Sản phẩm gia tăng (Augmented Product: bảo hành, hậu mãi, giao hàng).
                - Chu kỳ sống của sản phẩm (Product Life Cycle - PLC): Giai đoạn Giới thiệu (Introduction), Tăng trưởng (Growth), Bão hòa (Maturity) và Suy thoái (Decline).
                II. Chiến lược Định giá (Price - Thành tố thứ 2 trong 4P):
                - Định giá dựa trên chi phí (Cost-Based Pricing): Cộng biên lợi nhuận mục tiêu vào chi phí sản xuất đơn vị.
                - Định giá dựa trên giá trị cảm nhận (Value-Based Pricing): Dựa vào mức độ sẵn sàng chi trả của khách hàng (ví dụ: Apple, Starbucks).
                - Định giá hớt váng (Price Skimming): Đặt giá cao khi mới ra mắt sản phẩm công nghệ mới rồi giảm dần.
                - Định giá thâm nhập thị trường (Penetration Pricing): Đặt giá thấp ban đầu để nhanh chóng chiếm lĩnh thị phần.
                """,
                """
                CHƯƠNG 3: CHIẾN LƯỢC PHÂN PHỐI (PLACE), XÚC TIẾN (PROMOTION) & CASE STUDY THỰC TẾ
                I. Chiến lược Phân phối (Place - Thành tố thứ 3 trong 4P):
                - Kênh phân phối trực tiếp (Direct Channel - D2C): Bán trực tiếp qua website hãng, cửa hàng flagship.
                - Kênh phân phối gián tiếp (Indirect Channel): Nhà sản xuất -> Nhà bán buôn -> Nhà bán lẻ -> Người tiêu dùng (GT: tạp hóa truyền thống; MT: siêu thị, chuỗi tiện lợi).
                II. Chiến lược Xúc tiến (Promotion - Thành tố thứ 4 trong 4P):
                - Truyền thông tích hợp (IMC): Kết hợp Quảng cáo (Advertising), Quan hệ công chúng (PR), Khuyến mãi (Sales Promotion), Content Marketing và KOL/Influencer.
                III. Case Study Vinamilk & Highlands Coffee tại Việt Nam:
                - Vinamilk áp dụng 4P: Đa dạng dòng sữa tươi 100% và sữa hạt (Product), mức giá phổ thông phù hợp đại đa số gia đình Việt (Price), phủ sóng hơn 250.000 điểm bán lẻ truyền thống và siêu thị (Place), chiến dịch truyền thông gắn liền sức khỏe cộng đồng (Promotion).
                """
        });

        map.put("Giao_trinh_Giai_thuat_Nang_cao.pdf", new String[]{
                """
                CHƯƠNG 1: CÂY NHỊ PHÂN TÌM KIẾM CÂN BẰNG (AVL TREE & RED-BLACK TREE)
                1. Cây AVL (Adelson-Velsky và Landis):
                - Cây AVL là cây nhị phân tìm kiếm tự cân bằng, trong đó tại mọi nút, độ lệch chiều cao giữa cây con trái và cây con phải (Balance Factor) không vượt quá 1 (thuộc tập {-1, 0, 1}).
                - Độ phức tạp thời gian của các thao tác Tìm kiếm (Search), Thêm (Insert) và Xóa (Delete) trên cây AVL luôn đảm bảo O(log n) ở cả trường hợp trung bình và xấu nhất.
                - Bốn phép quay cân bằng cây AVL: Quay đơn trái (Left Rotation - trường hợp Right-Right), Quay đơn phải (Right Rotation - trường hợp Left-Left), Quay kép Trái-Phải (LR) và Quay kép Phải-Trái (RL).
                2. Cây Đỏ-Đen (Red-Black Tree):
                - Mỗi nút có màu Đỏ hoặc Đen; nút gốc luôn màu Đen; không có hai nút Đỏ liên tiếp; mọi đường đi từ một nút tới các nút lá NIL có cùng số nút Đen (Black-Height).
                """,
                """
                CHƯƠNG 2: THUẬT TOÁN ĐỒ THỊ (DIJKSTRA, FLOYD-WARSHALL, KRUSKAL) & QUY HOẠCH ĐỘNG
                1. Thuật toán tìm đường đi ngắn nhất:
                - Thuật toán Dijkstra: Tìm đường đi ngắn nhất từ một đỉnh nguồn tới mọi đỉnh còn lại trên đồ thị có trọng số không âm. Sử dụng hàng đợi ưu tiên (Priority Queue / Min-Heap) có độ phức tạp O((V + E) log V).
                - Thuật toán Floyd-Warshall: Quy hoạch động tìm đường đi ngắn nhất giữa mọi cặp đỉnh với công thức truy hồi dist[i][j] = min(dist[i][j], dist[i][k] + dist[k][j]), độ phức tạp O(V^3).
                2. Cây khung nhỏ nhất (Minimum Spanning Tree - MST):
                - Thuật toán Kruskal: Sắp xếp các cạnh theo trọng số tăng dần và dùng cấu trúc Disjoint Set Union (DSU / Union-Find) để kiểm tra chu trình, độ phức tạp O(E log E).
                3. Quy hoạch động (Dynamic Programming):
                - Hai tính chất cốt lõi: Bài toán con gối nhau (Overlapping Subproblems) và Cấu trúc con tối ưu (Optimal Substructure). Các bài toán kinh điển: Bài toán cái túi (0/1 Knapsack), Dãy con chung dài nhất (LCS), Dãy con tăng dài nhất (LIS).
                """
        });

        map.put("Slide_Chuong_4_Truy_van_SQL.pptx", new String[]{
                """
                CHƯƠNG 4: KỸ THUẬT TRUY VẤN SQL NÂNG CAO TRONG SQL SERVER
                1. Các phép kết nối bảng (JOIN):
                - INNER JOIN: Chỉ trả về các bản ghi có khóa khớp ở cả hai bảng.
                - LEFT OUTER JOIN: Trả về toàn bộ bản ghi bảng bên trái và các bản ghi khớp ở bảng bên phải (nếu không khớp điền NULL).
                - RIGHT OUTER JOIN và FULL OUTER JOIN: Hợp nhất dữ liệu từ hai bảng.
                2. Truy vấn con (Subquery) và Common Table Expressions (CTE):
                - Non-correlated Subquery: Truy vấn con độc lập, chạy một lần duy nhất trước khi truy vấn cha thực thi.
                - Correlated Subquery: Truy vấn con phụ thuộc vào từng dòng của truy vấn cha (thường dùng với EXISTS / NOT EXISTS).
                - CTE (WITH ... AS): Giúp tách truy vấn phức tạp thành các khối dễ đọc và hỗ trợ truy vấn đệ quy (Recursive CTE) để duyệt cây phân cấp phòng ban, danh mục.
                """,
                """
                WINDOW FUNCTIONS VÀ TỐI ƯU HÓA QUERY EXECUTION PLAN
                1. Các hàm cửa sổ (Window Functions):
                - ROW_NUMBER() OVER (PARTITION BY department_id ORDER BY salary DESC): Đánh số thứ tự liên tiếp trong từng nhóm.
                - RANK() và DENSE_RANK(): Xếp hạng có xử lý đồng hạng (DENSE_RANK không nhảy bậc sau khi đồng hạng).
                - SUM(), AVG(), LAG(), LEAD() OVER (...): Tính tổng lũy kế và so sánh với dòng trước/sau mà không làm giảm số dòng như GROUP BY.
                2. Tối ưu hóa truy vấn và Index trong SQL Server:
                - Clustered Index: Sắp xếp vật lý dữ liệu trong bảng theo khóa chính (mỗi bảng chỉ có 1 Clustered Index).
                - Non-Clustered Index: Cấu trúc B-Tree riêng trỏ tới dòng dữ liệu; nên tạo trên các cột thường xuất hiện trong mệnh đề WHERE, JOIN, ORDER BY.
                - Tránh dùng hàm trên cột được đánh index trong mệnh đề WHERE (Non-SARGable query) để tránh Index Scan chuyển thành Table Scan.
                """
        });

        map.put("De_thi_giua_ky_Kiem_thu_phan_mem_2023.docx", new String[]{
                """
                ĐỀ THI GIỮA KỲ MÔN KIỂM THỬ PHẦN MỀM (SOFTWARE TESTING - SWT301)
                Thời gian làm bài: 60 phút
                PHẦN I: LÝ THUYẾT VÀ CÁC CẤP ĐỘ KIỂM THỬ (4.0 ĐIỂM)
                Câu 1: Phân biệt 4 cấp độ kiểm thử phần mềm theo mô hình V-Model:
                - Kiểm thử đơn vị (Unit Testing): Kiểm tra từng hàm, phương thức hoặc class riêng lẻ (thường do lập trình viên viết bằng JUnit 5, Mockito).
                - Kiểm thử tích hợp (Integration Testing): Kiểm tra sự giao tiếp giữa các module, tầng Service - Repository - Database hoặc REST API.
                - Kiểm thử hệ thống (System Testing): Kiểm tra toàn bộ hệ thống hoàn chỉnh theo đặc tả yêu cầu chức năng và phi chức năng.
                - Kiểm thử chấp nhận (Acceptance Testing - UAT): Kiểm thử cùng khách hàng/người dùng cuối trước khi bàn giao.
                """,
                """
                PHẦN II: THIẾT KẾ TEST CASE BẰNG PHÂN VÙNG TƯƠNG ĐƯƠNG & GIÁ TRỊ BIÊN (6.0 ĐIỂM)
                Bài toán: Hệ thống thanh toán học phí trực tuyến StudyPay quy định số tiền giao dịch hợp lệ từ 50.000 VNĐ đến 50.000.000 VNĐ mỗi lần. Nếu nhập ngoài khoảng này hệ thống báo lỗi.
                ĐÁP ÁN BAREM:
                1. Phân vùng tương đương (Equivalence Partitioning - EP):
                - Phân vùng không hợp lệ 1 (Invalid Partition 1): Số tiền < 50.000 (Ví dụ: 20.000 VNĐ).
                - Phân vùng hợp lệ (Valid Partition): 50.000 <= Số tiền <= 50.000.000 (Ví dụ: 5.000.000 VNĐ).
                - Phân vùng không hợp lệ 2 (Invalid Partition 2): Số tiền > 50.000.000 (Ví dụ: 60.000.000 VNĐ).
                2. Phân tích giá trị biên (Boundary Value Analysis - BVA):
                - Các mốc giá trị biên cần kiểm thử: 49.999 (Fail), 50.000 (Pass), 50.001 (Pass), 49.999.999 (Pass), 50.000.000 (Pass), 50.000.001 (Fail).
                """
        });

        map.put("Tong_hop_Cong_thuc_Xac_Suat_Thong_Ke.pdf", new String[]{
                """
                TÓM TẮT CÔNG THỨC XÁC SUẤT VÀ THỐNG KÊ TOÁN HỌC (XSTK)
                1. Các công thức xác suất cơ bản:
                - Công thức cộng xác suất: P(A U B) = P(A) + P(B) - P(A B).
                - Công thức xác suất có điều kiện: P(A | B) = P(A B) / P(B) với P(B) > 0.
                - Công thức xác suất đầy đủ và Công thức Bayes: P(Bi | A) = [P(Bi) * P(A | Bi)] / Sum[P(Bj) * P(A | Bj)].
                2. Các phân phối xác suất thông dụng:
                - Phân phối Nhị thức B(n, p): P(X = k) = C(n, k) * p^k * (1 - p)^(n - k); Kỳ vọng E(X) = n*p; Phương sai Var(X) = n*p*(1 - p).
                - Phân phối Poisson P(lambda): P(X = k) = (e^(-lambda) * lambda^k) / k!; E(X) = Var(X) = lambda.
                - Phân phối Chuẩn N(mu, sigma^2): Chuẩn hóa Z = (X - mu) / sigma ~ N(0, 1).
                """,
                """
                ƯỚC LƯỢNG KHOẢNG TIN CẬY VÀ KIỂM ĐỊNH GIẢ THUYẾT THỐNG KÊ
                1. Ước lượng khoảng tin cậy cho kỳ vọng (Trung bình tổng thể mu):
                - Trường hợp đã biết phương sai tổng thể sigma^2 (hoặc cỡ mẫu n >= 30): Khoảng tin cậy (1 - alpha) là [x_bar - z_(alpha/2) * sigma / sqrt(n) ; x_bar + z_(alpha/2) * sigma / sqrt(n)].
                - Trường hợp chưa biết sigma^2 và cỡ mẫu nhỏ n < 30: Sử dụng phân phối Student t_(alpha/2; n-1) với độ lệch chuẩn mẫu hiệu chỉnh s.
                2. Kiểm định giả thuyết thống kê (Z-test và T-test):
                - Giả thuyết không H0: mu = mu0; Đối thuyết H1: mu != mu0 (hai phía) hoặc mu > mu0 / mu < mu0.
                - Quy tắc bác bỏ H0: Nếu giá trị thống kê kiểm định |Z_qs| > z_(alpha/2) hoặc p-value < mức ý nghĩa alpha (thường chọn alpha = 0.05 hoặc 0.01) thì bác bỏ H0.
                """
        });

        map.put("Giao_trinh_Kinh_te_Vi_mo_Dai_hoc.pdf", new String[]{
                """
                GIÁO TRÌNH KINH TẾ VI MÔ ĐẠI HỌC (MICROECONOMICS)
                CHƯƠNG 1: QUY LUẬT CUNG CẦU VÀ CÂN BẰNG THỊ TRƯỜNG
                1. Cầu thị trường (Demand):
                - Luật cầu (Law of Demand): Khi giá hàng hóa tăng (P tăng) và các yếu tố khác không đổi (ceteris paribus) thì lượng cầu giảm (Qd giảm), đường cầu dốc xuống.
                - Các yếu tố làm dịch chuyển đường cầu: Thu nhập người tiêu dùng, giá hàng hóa thay thế và bổ sung, thị hiếu, kỳ vọng và quy mô dân số.
                2. Cung thị trường (Supply) và Cân bằng:
                - Luật cung: Khi giá tăng thì lượng cung (Qs) tăng, đường cung dốc lên.
                - Điểm cân bằng thị trường (Equilibrium): Tại mức giá cân bằng P* nơi Qd = Qs. Nếu giá trần (Price Ceiling) thấp hơn P* sẽ gây thiếu hụt hàng hóa; nếu giá sàn (Price Floor) cao hơn P* sẽ gây dư thừa.
                """,
                """
                CHƯƠNG 2: ĐỘ CO GIÃN, LÝ THUYẾT HỮU DỤNG VÀ CẤU TRÚC THỊ TRƯỜNG
                1. Độ co giãn của cầu theo giá (Price Elasticity of Demand - Ed):
                - Công thức: Ed = (% thay đổi lượng cầu Qd) / (% thay đổi giá P) = (dQ/dP) * (P/Q).
                - Nếu |Ed| > 1 (Cầu co giãn nhiều): Giảm giá sẽ làm tăng tổng doanh thu (TR = P * Q).
                - Nếu |Ed| < 1 (Cầu ít co giãn - mặt hàng thiết yếu): Tăng giá sẽ làm tăng tổng doanh thu.
                2. Chi phí sản xuất và nguyên tắc tối đa hóa lợi nhuận:
                - Doanh nghiệp tối đa hóa lợi nhuận tại mức sản lượng Q* thỏa mãn điều kiện: Doanh thu biên bằng Chi phí biên (MR = MC).
                3. Bốn cấu trúc thị trường: Cạnh tranh hoàn hảo, Cạnh tranh độc quyền, Độc quyền nhóm (Oligopoly) và Độc quyền hoàn toàn (Monopoly).
                """
        });

        map.put("De_cuong_On_tap_Phap_luat_Dai_cuong.docx", new String[]{
                """
                ĐỀ CƯƠNG ÔN TẬP MÔN PHÁP LUẬT ĐẠI CƯƠNG
                CHỦ ĐỀ 1: NHÀ NƯỚC, QUY PHẠM PHÁP LUẬT VÀ QUAN HỆ PHÁP LUẬT
                1. Cấu trúc của Quy phạm pháp luật (3 bộ phận):
                - Giả định (Hypothesis): Nêu hoàn cảnh, điều kiện, chủ thể chịu sự tác động của quy phạm.
                - Quy định (Disposition): Nêu cách xử sự (quyền và nghĩa vụ) mà chủ thể được làm, phải làm hoặc không được làm.
                - Chế tài (Sanction): Biện pháp xử lý dự kiến áp dụng đối với chủ thể vi phạm pháp luật (Chế tài hình sự, hành chính, dân sự, kỷ luật).
                2. Quan hệ pháp luật:
                - Gồm 3 yếu tố: Chủ thể (cá nhân, pháp nhân có năng lực chủ thể gồm năng lực pháp luật và năng lực hành vi), Khách thể và Nội dung (quyền và nghĩa vụ pháp lý).
                """,
                """
                CHỦ ĐỀ 2: VI PHẠM PHÁP LUẬT, TRÁCH NHIỆM PHÁP LÝ VÀ HỢP ĐỒNG DÂN SỰ
                1. Dấu hiệu và Cấu thành của Vi phạm pháp luật:
                - 4 dấu hiệu: Là hành vi xác định của con người; Trái pháp luật; Có lỗi (cố ý hoặc vô ý); Do chủ thể có năng lực trách nhiệm pháp lý thực hiện.
                - 4 yếu tố cấu thành: Mặt khách quan (hành vi, hậu quả, mối quan hệ nhân quả), Mặt chủ quan (lỗi, động cơ, mục đích), Chủ thể và Khách thể.
                2. Pháp luật Dân sự và Hợp đồng:
                - Điều kiện có hiệu lực của giao dịch dân sự (Điều 117 Bộ luật Dân sự 2015): Chủ thể có năng lực pháp luật và năng lực hành vi dân sự phù hợp; Chủ thể tham gia hoàn toàn tự nguyện; Mục đích và nội dung không vi phạm điều cấm của luật, không trái đạo đức xã hội.
                """
        });

        map.put("Tong_hop_Tu_vung_Academic_English_Writing.pdf", new String[]{
                """
                SỔ TAY TỪ VỰNG VÀ CẤU TRÚC HỌC THUẬT (ACADEMIC ENGLISH WRITING - ENG201)
                PHẦN 1: TỪ NỐI HỌC THUẬT VÀ LIÊN TỪ (COHESIVE DEVICES & TRANSITIONS)
                1. Bổ sung luận điểm (Adding Information):
                - Furthermore, Moreover, In addition, Additionally, Not only ... but also ...
                2. Thể hiện sự tương phản / nhượng bộ (Contrast & Concession):
                - However, Nevertheless, Nonetheless, On the other hand, Conversely, Notwithstanding, Whereas, Albeit.
                3. Nguyên nhân và Kết quả (Cause & Effect):
                - Consequently, Therefore, As a consequence, Hence, Thus, Stemming from, Attributable to.
                """,
                """
                PHẦN 2: ĐỘNG TỪ HỌC THUẬT TRONG BÁO CÁO NGHIÊN CỨU (REPORTING VERBS & HEDGING)
                1. Động từ trích dẫn nghiên cứu (Reporting Verbs):
                - Khẳng định mạnh: Demonstrate, Establish, Confirm, Prove,Substantiate.
                - Đề xuất / Trung lập: Suggest, Indicate, Imply, Postulate, Posit, Contend, Argue.
                2. Ngôn ngữ thận trọng trong học thuật (Academic Hedging):
                - Tránh khẳng định tuyệt đối bằng cách sử dụng: "The findings tend to suggest that...", "It is plausible that...", "This may be attributed to...".
                """
        });

        map.put("De_cuong_on_tap_Kien_truc_may_tinh.pdf", new String[]{
                """
                ĐỀ CƯƠNG ÔN TẬP KIẾN TRÚC MÁY TÍNH (COMPUTER ARCHITECTURE - KTMT)
                PHẦN 1: TẬP LỆNH RISC/MIPS VÀ KỸ THUẬT PIPELINING TRONG CPU
                1. Kiến trúc RISC (Reduced Instruction Set Computer) và MIPS:
                - Các lệnh có độ dài cố định 32-bit, gồm 3 định dạng chính: R-type (thanh ghi: add, sub, and, or), I-type (tức thời & truy cập bộ nhớ: lw, sw, addi, beq) và J-type (nhảy: j, jal).
                2. Kỹ thuật đường ống lệnh 5 tầng (5-Stage Instruction Pipeline):
                - IF (Instruction Fetch) -> ID (Instruction Decode) -> EX (Execute) -> MEM (Memory Access) -> WB (Write Back).
                - Ba loại xung đột đường ống (Pipeline Hazards): Structural Hazard (xung đột tài nguyên), Data Hazard (phụ thuộc dữ liệu, giải quyết bằng Forwarding/Bypassing hoặc Stall) và Control Hazard (lệnh rẽ nhánh, giải quyết bằng Branch Prediction).
                """,
                """
                PHẦN 2: PHÂN CẤP BỘ NHỚ (CACHE MEMORY L1/L2/L3 & VIRTUAL MEMORY)
                1. Nguyên lý cục bộ (Principle of Locality):
                - Cục bộ về thời gian (Temporal Locality): Dữ liệu vừa được truy cập có khả năng cao sẽ được truy cập lại sớm.
                - Cục bộ về không gian (Spatial Locality): Các ô nhớ lân cận ô nhớ vừa truy cập có xác suất cao sẽ được truy cập tiếp theo.
                2. Ánh xạ bộ nhớ Cache:
                - Direct-Mapped Cache (Ánh xạ trực tiếp), Fully Associative Cache (Ánh xạ kết hợp đầy đủ) và N-Way Set-Associative Cache (Ánh xạ kết hợp theo tập).
                - Thời gian truy cập bộ nhớ trung bình: AMAT = Hit Time + (Miss Rate * Miss Penalty).
                """
        });

        map.put("Mau_Thiet_ke_Giao_dien_Nguoi_dung_Figma.pdf", new String[]{
                """
                TÀI LIỆU HƯỚNG DẪN DESIGN SYSTEM & UI/UX TRÊN FIGMA
                1. Hệ thống lưới (Grid System) và Khoảng cách (Spacing):
                - Sử dụng lưới 12 cột (12-Column Responsive Grid) cho Desktop (Max-width 1280px, Gutter 24px, Margin 32px) và lưới 4 cột cho Mobile.
                - Quy tắc 8-Point Grid System: Toàn bộ padding, margin, gap và kích thước component sử dụng bội số của 8px (8px, 16px, 24px, 32px, 48px) kết hợp 4px cho chi tiết nhỏ.
                2. Bảng màu (Color Palette) và Độ tương phản (WCAG Accessibility):
                - Màu chính (Primary: Indigo #4F46E5), Màu phụ (Secondary: Slate #475569), Trạng thái (Success #16A34A, Warning #D97706, Danger #DC2626).
                - Tỷ lệ tương phản văn bản tối thiểu đạt chuẩn WCAG AA là 4.5:1 đối với chữ thường và 3:1 đối với chữ lớn.
                """
        });

        return map;
    }
}
