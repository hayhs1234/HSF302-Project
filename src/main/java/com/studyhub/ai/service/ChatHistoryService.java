package com.studyhub.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.ai.dto.ChatResponseDto;
import com.studyhub.ai.dto.ChatTurnDto;
import com.studyhub.ai.dto.SourceChunkDto;
import com.studyhub.model.ChatConversation;
import com.studyhub.model.ChatMessage;
import com.studyhub.model.User;
import com.studyhub.repository.ChatConversationRepository;
import com.studyhub.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final String DEFAULT_TITLE = "Cuộc trò chuyện mới";

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public ChatHistoryService(ChatConversationRepository conversationRepository,
                              ChatMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = new ObjectMapper();
    }

    public List<ChatConversation> getUserConversations(Long userId) {
        if (userId == null) return Collections.emptyList();
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public Optional<ChatConversation> getConversationForUser(Long conversationId, Long userId) {
        if (conversationId == null || userId == null) return Optional.empty();
        return conversationRepository.findByIdAndUserId(conversationId, userId);
    }

    @Transactional
    public ChatConversation createEmptyConversation(User user, String mode, Long docId, String docTitle) {
        ChatConversation conv = new ChatConversation(user, DEFAULT_TITLE, mode, docId, docTitle);
        return conversationRepository.save(conv);
    }

    @Transactional
    public ChatConversation getOrCreateConversation(Long conversationId, User user, String firstMessage,
                                                    String mode, Long docId, String docTitle) {
        String generatedTitle = buildTitleFromMessage(firstMessage);
        if (conversationId != null && user != null && user.getId() != null) {
            Optional<ChatConversation> existingOpt = conversationRepository.findByIdAndUserId(conversationId, user.getId());
            if (existingOpt.isPresent()) {
                ChatConversation existing = existingOpt.get();
                if (DEFAULT_TITLE.equals(existing.getTitle()) && !generatedTitle.isBlank()) {
                    existing.setTitle(generatedTitle);
                }
                existing.setMode(mode != null ? mode : existing.getMode());
                if (docId != null) {
                    existing.setDocumentId(docId);
                    existing.setDocumentTitle(docTitle);
                }
                existing.setUpdatedAt(LocalDateTime.now());
                return conversationRepository.save(existing);
            }
        }
        String title = generatedTitle.isBlank() ? DEFAULT_TITLE : generatedTitle;
        ChatConversation created = new ChatConversation(user, title, mode, docId, docTitle);
        return conversationRepository.save(created);
    }

    public List<ChatTurnDto> getRecentTurns(Long conversationId, int maxMessages) {
        if (conversationId == null) return Collections.emptyList();
        List<ChatMessage> allMessages = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        if (allMessages.isEmpty()) return Collections.emptyList();

        int fromIndex = Math.max(0, allMessages.size() - maxMessages);
        List<ChatMessage> recent = allMessages.subList(fromIndex, allMessages.size());
        List<ChatTurnDto> turns = new ArrayList<>(recent.size());
        for (ChatMessage msg : recent) {
            turns.add(new ChatTurnDto(msg.getRole(), msg.getContent()));
        }
        return turns;
    }

    @Transactional
    public void saveExchange(ChatConversation conversation, String userQuestion, ChatResponseDto aiResponse) {
        if (conversation == null || conversation.getId() == null) return;

        ChatMessage userMsg = new ChatMessage(conversation, "user", userQuestion);
        userMsg.setMode(aiResponse.getMode());
        userMsg.setDocumentId(aiResponse.getDocumentId());
        userMsg.setDocumentTitle(aiResponse.getDocumentTitle());
        messageRepository.save(userMsg);

        ChatMessage assistantMsg = new ChatMessage(conversation, "assistant", aiResponse.getReply());
        assistantMsg.setMode(aiResponse.getMode());
        assistantMsg.setProvider(aiResponse.getProvider());
        assistantMsg.setModel(aiResponse.getModel());
        assistantMsg.setDocumentId(aiResponse.getDocumentId());
        assistantMsg.setDocumentTitle(aiResponse.getDocumentTitle());

        if (aiResponse.getSources() != null && !aiResponse.getSources().isEmpty()) {
            try {
                assistantMsg.setSourcesJson(objectMapper.writeValueAsString(aiResponse.getSources()));
            } catch (Exception e) {
                log.warn("Không thể tuần tự hóa nguồn trích dẫn: {}", e.getMessage());
            }
        }
        messageRepository.save(assistantMsg);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    public List<Map<String, Object>> getMessagesForDisplay(Long conversationId) {
        if (conversationId == null) return Collections.emptyList();
        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        List<Map<String, Object>> list = new ArrayList<>(messages.size());

        for (ChatMessage m : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("role", m.getRole());
            item.put("content", m.getContent());
            item.put("mode", m.getMode());
            item.put("provider", m.getProvider());
            item.put("model", m.getModel());
            item.put("documentId", m.getDocumentId());
            item.put("documentTitle", m.getDocumentTitle());
            item.put("timestamp", m.getFormattedTime());

            List<SourceChunkDto> sources = Collections.emptyList();
            if (m.getSourcesJson() != null && !m.getSourcesJson().isBlank()) {
                try {
                    sources = objectMapper.readValue(m.getSourcesJson(), new TypeReference<List<SourceChunkDto>>() {});
                } catch (Exception ignored) {
                }
            }
            item.put("sources", sources);
            list.add(item);
        }
        return list;
    }

    @Transactional
    public boolean deleteConversation(Long conversationId, Long userId) {
        Optional<ChatConversation> opt = getConversationForUser(conversationId, userId);
        if (opt.isEmpty()) return false;
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(opt.get());
        return true;
    }

    @Transactional
    public Optional<ChatConversation> renameConversation(Long conversationId, Long userId, String newTitle) {
        Optional<ChatConversation> opt = getConversationForUser(conversationId, userId);
        if (opt.isEmpty() || newTitle == null || newTitle.isBlank()) return Optional.empty();
        ChatConversation conv = opt.get();
        conv.setTitle(newTitle.trim().length() > 100 ? newTitle.trim().substring(0, 100) : newTitle.trim());
        conv.setUpdatedAt(LocalDateTime.now());
        return Optional.of(conversationRepository.save(conv));
    }

    private String buildTitleFromMessage(String message) {
        if (message == null || message.isBlank()) return DEFAULT_TITLE;
        String clean = message.trim().replaceAll("\\s+", " ");
        return clean.length() > 52 ? clean.substring(0, 50) + "…" : clean;
    }
}
