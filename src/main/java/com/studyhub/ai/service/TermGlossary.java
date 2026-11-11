package com.studyhub.ai.service;

import java.util.*;

/**
 * Từ điển thuật ngữ học thuật Việt - Anh - viết tắt để tìm kiếm theo khái niệm thay vì từng chữ rời rạc.
 * Ví dụ: "cơ sở dữ liệu", "database", "CSDL" được coi là cùng một khái niệm khi truy hồi tài liệu.
 * Mọi thuật ngữ được viết ở dạng đã chuẩn hóa (chữ thường, bỏ dấu), khớp với Bm25Retriever.normalizeAscii.
 */
public final class TermGlossary {

    private static final List<List<String>> SYNONYM_GROUPS = List.of(
            // Cơ sở dữ liệu
            List.of("co so du lieu", "database", "databases", "csdl", "db"),
            List.of("he quan tri co so du lieu", "dbms", "hqtcsdl", "database management system"),
            List.of("truy van", "query", "queries", "sql query"),
            List.of("khoa chinh", "primary key", "pk"),
            List.of("khoa ngoai", "foreign key", "fk"),
            List.of("chuan hoa", "normalization", "normal form"),
            List.of("rang buoc toan ven", "integrity constraint", "constraint"),
            List.of("giao tac", "transaction", "transactions"),
            List.of("chi muc", "index", "indexes"),
            List.of("du lieu", "data"),
            // Lập trình & thuật toán
            List.of("cau truc du lieu", "data structure", "data structures", "ctdl"),
            List.of("giai thuat", "thuat toan", "algorithm", "algorithms"),
            List.of("cau truc du lieu va giai thuat", "dsa", "ctdl gt"),
            List.of("do phuc tap", "complexity", "big o"),
            List.of("de quy", "recursion", "recursive"),
            List.of("sap xep", "sort", "sorting"),
            List.of("tim kiem nhi phan", "binary search"),
            List.of("ngan xep", "stack"),
            List.of("hang doi", "queue"),
            List.of("hang doi uu tien", "priority queue", "heap"),
            List.of("danh sach lien ket", "linked list"),
            List.of("do thi", "graph", "graphs"),
            List.of("cay nhi phan", "binary tree"),
            List.of("duong di ngan nhat", "shortest path"),
            List.of("quy hoach dong", "dynamic programming", "dp"),
            List.of("chia de tri", "divide and conquer"),
            List.of("lap trinh huong doi tuong", "huong doi tuong", "object oriented", "oop"),
            List.of("ke thua", "inheritance"),
            List.of("da hinh", "polymorphism"),
            List.of("dong goi", "encapsulation"),
            // Hệ thống & mạng
            List.of("mang may tinh", "computer network", "computer networks", "networking"),
            List.of("kien truc may tinh", "computer architecture"),
            List.of("he dieu hanh", "operating system", "os"),
            List.of("dia chi ip", "ip address"),
            List.of("bo dinh tuyen", "router", "routing", "dinh tuyen"),
            // Công nghệ phần mềm
            List.of("kiem thu phan mem", "kiem thu", "software testing", "testing", "test case"),
            List.of("cong nghe phan mem", "software engineering"),
            List.of("thiet ke giao dien", "giao dien nguoi dung", "user interface", "ui", "ux"),
            List.of("yeu cau phan mem", "software requirement", "requirements"),
            // AI
            // Không dùng từ đơn "ai" vì trong tiếng Việt "ai" còn có nghĩa là "người nào"
            List.of("tri tue nhan tao", "artificial intelligence"),
            List.of("hoc may", "machine learning", "ml"),
            List.of("hoc sau", "deep learning"),
            // Kinh tế, kỹ năng, khác
            // "vi mô" và "vĩ mô" trùng nhau sau khi bỏ dấu nên gộp chung một nhóm
            List.of("kinh te vi mo", "microeconomics", "macroeconomics", "vi mo"),
            List.of("cung cau", "supply and demand", "cung va cau"),
            List.of("xac suat", "probability"),
            List.of("thong ke", "statistics"),
            List.of("lam viec nhom", "teamwork", "team work"),
            List.of("tiep thi", "marketing")
    );

    /** Cụm/từ chuẩn hóa -> chỉ số nhóm đồng nghĩa */
    private static final Map<String, Integer> TERM_TO_GROUP = new HashMap<>();
    /** Các cụm nhiều chữ, sắp dài nhất trước để "co so du lieu" được nhận trước "du lieu" */
    private static final List<String> PHRASES_LONGEST_FIRST;

    static {
        List<String> phrases = new ArrayList<>();
        for (int i = 0; i < SYNONYM_GROUPS.size(); i++) {
            for (String term : SYNONYM_GROUPS.get(i)) {
                TERM_TO_GROUP.putIfAbsent(term, i);
                if (term.contains(" ")) phrases.add(term);
            }
        }
        phrases.sort(Comparator.comparingInt(String::length).reversed());
        PHRASES_LONGEST_FIRST = Collections.unmodifiableList(phrases);
    }

    private TermGlossary() {
    }

    /**
     * Tách câu hỏi đã chuẩn hóa thành các khái niệm. Mỗi khái niệm là danh sách các cách viết tương đương
     * (từ đơn hoặc cụm nhiều chữ). Cụm thuật ngữ được nhận diện trước khi lọc từ dừng,
     * nên "tài liệu về cơ sở dữ liệu" vẫn giữ được khái niệm "cơ sở dữ liệu".
     */
    public static List<List<String>> toConcepts(String normalizedQuery, Set<String> stopwords) {
        List<List<String>> concepts = new ArrayList<>();
        Set<Integer> usedGroups = new HashSet<>();
        Set<String> usedPlainTokens = new HashSet<>();
        if (normalizedQuery == null || normalizedQuery.isBlank()) return concepts;

        String padded = " " + normalizedQuery + " ";
        for (String phrase : PHRASES_LONGEST_FIRST) {
            String needle = " " + phrase + " ";
            if (padded.contains(needle)) {
                int group = TERM_TO_GROUP.get(phrase);
                if (usedGroups.add(group)) {
                    concepts.add(SYNONYM_GROUPS.get(group));
                }
                padded = padded.replace(needle, " | ");
            }
        }

        for (String token : padded.trim().split("\\s+")) {
            if (token.isEmpty() || token.equals("|") || token.length() < 2) continue;
            Integer group = TERM_TO_GROUP.get(token);
            if (group != null) {
                if (usedGroups.add(group)) concepts.add(SYNONYM_GROUPS.get(group));
            } else if (!stopwords.contains(token) && usedPlainTokens.add(token)) {
                concepts.add(List.of(token));
            }
        }
        return concepts;
    }
}
