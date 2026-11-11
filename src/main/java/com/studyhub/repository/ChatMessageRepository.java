package com.studyhub.repository;

import com.studyhub.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.id = :id AND m.conversation.user.id = :userId")
    Optional<ChatMessage> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Thống kê đánh giá câu trả lời AI theo chế độ (RAG / GENERAL): [mode, feedback, count]
     */
    @Query("SELECT m.mode, m.feedback, COUNT(m) FROM ChatMessage m " +
           "WHERE m.role = 'assistant' AND m.feedback IS NOT NULL AND m.conversation.user.id = :userId " +
           "GROUP BY m.mode, m.feedback")
    List<Object[]> countFeedbackByMode(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ChatMessage m WHERE m.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);
}
