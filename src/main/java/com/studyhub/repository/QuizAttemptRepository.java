package com.studyhub.repository;

import com.studyhub.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByUserIdOrderByStartedAtDesc(Long userId);

    @Query("SELECT DISTINCT a FROM QuizAttempt a LEFT JOIN FETCH a.questions WHERE a.id = :id AND a.user.id = :userId")
    Optional<QuizAttempt> findWithQuestions(@Param("id") Long id, @Param("userId") Long userId);

    /** Các bài đã nộp theo thời gian (dùng vẽ biểu đồ tiến bộ) */
    @Query("SELECT a FROM QuizAttempt a WHERE a.user.id = :userId AND a.status = 'SUBMITTED' ORDER BY a.submittedAt ASC")
    List<QuizAttempt> findSubmittedChronological(@Param("userId") Long userId);

    /** Độ chính xác theo tài liệu: [documentTitle, số bài, tổng câu, tổng câu đúng, điểm TB] */
    @Query("SELECT COALESCE(a.documentTitle, 'Tổng hợp'), COUNT(a), SUM(a.totalQuestions), SUM(a.correctCount), AVG(a.score) " +
           "FROM QuizAttempt a WHERE a.user.id = :userId AND a.status = 'SUBMITTED' " +
           "GROUP BY COALESCE(a.documentTitle, 'Tổng hợp')")
    List<Object[]> statsByDocument(@Param("userId") Long userId);

    /** Độ chính xác theo mức độ: [difficulty, tổng câu, tổng câu đúng] */
    @Query("SELECT a.difficulty, SUM(a.totalQuestions), SUM(a.correctCount) " +
           "FROM QuizAttempt a WHERE a.user.id = :userId AND a.status = 'SUBMITTED' GROUP BY a.difficulty")
    List<Object[]> statsByDifficulty(@Param("userId") Long userId);
}
