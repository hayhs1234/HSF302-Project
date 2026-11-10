package com.studyhub.ai.service;

import com.studyhub.model.Document;
import com.studyhub.model.DocumentChunk;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class Bm25Retriever {

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    public static final double MIN_RELEVANCE_SCORE = 0.55;

    private static final Pattern NON_ALPHANUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Danh sách từ dừng (Stopwords) tiếng Việt phổ biến trong câu hỏi tra cứu
     * (dạng đã bỏ dấu để so khớp thống nhất)
     */
    private static final Set<String> STOPWORDS_ASCII = Set.of(
            "cho", "toi", "cac", "cua", "la", "va", "co", "trong", "nay", "nhung",
            "mot", "ve", "lien", "quan", "den", "tai", "lieu", "tep", "file", "files",
            "ban", "giup", "hay", "tim", "kiem", "biet", "hoi", "nao", "gi", "the",
            "nhu", "duoc", "khong", "voi", "tu", "theo", "tren", "duoi", "khi", "neu",
            "thi", "ma", "da", "se", "dang", "viec", "phan", "muc", "bai", "chuong",
            "can", "muon", "xin", "chao", "oi", "nhi", "nhe"
    );

    public static class ScoredChunk {
        private final DocumentChunk chunk;
        private final double score;
        private final int matchedTermCount;

        public ScoredChunk(DocumentChunk chunk, double score, int matchedTermCount) {
            this.chunk = chunk;
            this.score = score;
            this.matchedTermCount = matchedTermCount;
        }

        public DocumentChunk getChunk() {
            return chunk;
        }

        public double getScore() {
            return score;
        }

        public int getMatchedTermCount() {
            return matchedTermCount;
        }
    }

    /**
     * Chuẩn hóa chuỗi tiếng Việt: chữ thường, bỏ dấu, thay ký tự đặc biệt/gạch dưới bằng khoảng trắng
     */
    public static String normalizeAscii(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String lower = input.toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replace('_', ' ')
                .replace('-', ' ');
        String nfd = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(nfd).replaceAll("");
        return NON_ALPHANUM.matcher(withoutDiacritics).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    /**
     * Tách từ khóa có nghĩa từ câu hỏi (loại bỏ stopwords)
     */
    public static List<String> extractQueryTokens(String query) {
        String normalized = normalizeAscii(query);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        String[] rawTokens = normalized.split("\\s+");
        List<String> filtered = new ArrayList<>();
        for (String token : rawTokens) {
            if (token.length() < 2) continue;
            if (!STOPWORDS_ASCII.contains(token) && !filtered.contains(token)) {
                filtered.add(token);
            }
        }
        return filtered;
    }

    /**
     * Kiểm tra xem câu hỏi có phải là yêu cầu tổng quát trên tài liệu đang mở
     * (ví dụ: "Tóm tắt tài liệu này", "Nội dung chính là gì") không có từ khóa chuyên biệt hay không.
     */
    public static boolean isBroadDocumentOverviewQuery(String query) {
        String norm = normalizeAscii(query);
        if (norm.contains("tom tat") || norm.contains("noi dung chinh")
                || norm.contains("y chinh") || norm.contains("tong quan")
                || norm.contains("ket luan") || norm.contains("cau hoi trac nghiem")
                || norm.contains("on tap")) {
            List<String> tokens = extractQueryTokens(query);
            // Loại bỏ các từ chỉ hành động tổng quát để xem còn từ khóa chủ đề riêng nào không
            Set<String> overviewVerbs = Set.of("tom", "tat", "noi", "dung", "chinh", "tong", "ket", "luan", "tao", "cau", "trac", "nghiem", "on", "tap", "dan", "bao", "cao", "giai", "thich", "khai", "niem");
            long topicTokens = tokens.stream().filter(t -> !overviewVerbs.contains(t)).count();
            return topicTokens == 0;
        }
        return extractQueryTokens(query).isEmpty();
    }

    /**
     * Xếp hạng và lấy Top K đoạn trích phù hợp nhất theo thuật toán Okapi BM25
     */
    public List<ScoredChunk> retrieveTopK(String query, List<DocumentChunk> candidateChunks, int topK) {
        if (candidateChunks == null || candidateChunks.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<String> queryTokens = extractQueryTokens(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        int nDocs = candidateChunks.size();
        List<ChunkTokenStats> statsList = new ArrayList<>(nDocs);
        double totalLength = 0.0;

        for (DocumentChunk chunk : candidateChunks) {
            ChunkTokenStats stats = new ChunkTokenStats(chunk);
            statsList.add(stats);
            totalLength += stats.docLength;
        }

        double avgDocLength = nDocs > 0 ? Math.max(totalLength / nDocs, 1.0) : 100.0;

        // Tính Document Frequency (DF) và IDF cho từng từ khóa trong câu hỏi
        Map<String, Double> idfMap = new HashMap<>();
        for (String term : queryTokens) {
            int df = 0;
            for (ChunkTokenStats stats : statsList) {
                if (stats.getWeightedFrequency(term) > 0) {
                    df++;
                }
            }
            // Công thức Okapi BM25 IDF dương (Lucene BM25)
            double idf = Math.log(1.0 + (nDocs - df + 0.5) / (df + 0.5));
            idfMap.put(term, idf);
        }

        String normalizedPhrase = String.join(" ", queryTokens);
        List<ScoredChunk> results = new ArrayList<>();

        for (ChunkTokenStats stats : statsList) {
            double bm25Score = 0.0;
            int matchedTerms = 0;

            for (String term : queryTokens) {
                double tf = stats.getWeightedFrequency(term);
                if (tf > 0) {
                    matchedTerms++;
                    double idf = idfMap.getOrDefault(term, 0.5);
                    double normLength = 1.0 - B + B * (stats.docLength / avgDocLength);
                    double termScore = idf * ((tf * (K1 + 1.0)) / (tf + K1 * normLength));
                    bm25Score += termScore;
                }
            }

            if (matchedTerms == 0) {
                continue;
            }

            // Thưởng điểm nếu khớp cụm từ liên tiếp (Phrase Match Bonus)
            if (queryTokens.size() >= 2 && normalizedPhrase.length() >= 5) {
                if (stats.normalizedBody.contains(normalizedPhrase)) {
                    bm25Score += 2.5;
                }
                if (stats.normalizedTitleAndMeta.contains(normalizedPhrase)) {
                    bm25Score += 3.0;
                }
            }

            // Thưởng tỷ lệ bao phủ từ khóa câu hỏi (Query Coverage)
            double coverageRatio = (double) matchedTerms / queryTokens.size();
            bm25Score *= (0.7 + 0.3 * coverageRatio);

            if (bm25Score >= MIN_RELEVANCE_SCORE) {
                results.add(new ScoredChunk(stats.chunk, Math.round(bm25Score * 100.0) / 100.0, matchedTerms));
            }
        }

        results.sort((a, b) -> {
            int cmp = Double.compare(b.getScore(), a.getScore());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getChunk().getChunkIndex(), b.getChunk().getChunkIndex());
        });

        return results.stream().limit(topK).collect(Collectors.toList());
    }

    private static class ChunkTokenStats {
        final DocumentChunk chunk;
        final String normalizedBody;
        final String normalizedTitleAndMeta;
        final Map<String, Integer> bodyTermFreq = new HashMap<>();
        final Map<String, Integer> titleMetaTermFreq = new HashMap<>();
        final int docLength;

        ChunkTokenStats(DocumentChunk chunk) {
            this.chunk = chunk;
            this.normalizedBody = normalizeAscii(chunk.getContent());

            Document doc = chunk.getDocument();
            StringBuilder metaBuilder = new StringBuilder();
            if (doc != null) {
                if (doc.getTitle() != null) metaBuilder.append(doc.getTitle()).append(" ");
                if (doc.getSubject() != null) {
                    if (doc.getSubject().getName() != null) metaBuilder.append(doc.getSubject().getName()).append(" ");
                    if (doc.getSubject().getCode() != null) metaBuilder.append(doc.getSubject().getCode()).append(" ");
                }
                if (doc.getTags() != null) metaBuilder.append(doc.getTags()).append(" ");
                if (doc.getCategory() != null) metaBuilder.append(doc.getCategory()).append(" ");
            }
            this.normalizedTitleAndMeta = normalizeAscii(metaBuilder.toString());

            int count = 0;
            if (!normalizedBody.isEmpty()) {
                for (String token : normalizedBody.split("\\s+")) {
                    if (!token.isEmpty()) {
                        bodyTermFreq.merge(token, 1, Integer::sum);
                        count++;
                    }
                }
            }
            if (!normalizedTitleAndMeta.isEmpty()) {
                for (String token : normalizedTitleAndMeta.split("\\s+")) {
                    if (!token.isEmpty()) {
                        titleMetaTermFreq.merge(token, 1, Integer::sum);
                    }
                }
            }
            this.docLength = Math.max(count, 10);
        }

        double getWeightedFrequency(String term) {
            int bodyCount = bodyTermFreq.getOrDefault(term, 0);
            int metaCount = titleMetaTermFreq.getOrDefault(term, 0);
            // Trọng số tiêu đề/môn học/thẻ cao hơn để giúp tìm đúng tài liệu theo chủ đề
            return bodyCount + (metaCount * 2.5);
        }
    }
}
