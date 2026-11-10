package com.studyhub.repository;

import com.studyhub.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ChatMessage m WHERE m.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);
}
