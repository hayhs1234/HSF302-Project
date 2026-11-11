package com.studyhub.repository;

import com.studyhub.model.QuizAttemptQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizAttemptQuestionRepository extends JpaRepository<QuizAttemptQuestion, Long> {

    /** Sổ tay câu sai: các câu trả lời sai hoặc bỏ trống gần nhất của người dùng */
    @Query("SELECT q FROM QuizAttemptQuestion q JOIN FETCH q.attempt a " +
           "WHERE a.user.id = :userId AND a.status = 'SUBMITTED' AND (q.correct = false OR q.correct IS NULL) " +
           "ORDER BY a.submittedAt DESC, q.questionOrder ASC")
    List<QuizAttemptQuestion> findRecentWrong(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(q) FROM QuizAttemptQuestion q " +
           "WHERE q.attempt.user.id = :userId AND q.attempt.status = 'SUBMITTED' AND (q.correct = false OR q.correct IS NULL)")
    long countWrong(@Param("userId") Long userId);

    @Query("SELECT q FROM QuizAttemptQuestion q WHERE q.attempt.id = :attemptId AND q.questionOrder = :order AND q.attempt.user.id = :userId")
    Optional<QuizAttemptQuestion> findForAnswer(@Param("attemptId") Long attemptId,
                                                @Param("order") int order,
                                                @Param("userId") Long userId);
}
