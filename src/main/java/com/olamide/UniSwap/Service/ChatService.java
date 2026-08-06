package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Dto.ChatMessageDTO;
import com.olamide.UniSwap.Dto.ConversationDTO;
import com.olamide.UniSwap.Dto.SendMessageRequest;
import com.olamide.UniSwap.Entity.ChatMessage;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<ConversationDTO> getConversations(Long userId) {
        List<ChatMessage> messages = chatMessageRepository.findInvolving(userId);
        Map<Long, ConversationDTO> byPartner = new LinkedHashMap<>();

        for (ChatMessage message : messages) {
            boolean incoming = message.getReceiver().getId().equals(userId);
            Long otherId = incoming ? message.getSender().getId() : message.getReceiver().getId();
            String otherUsername = incoming
                    ? message.getSender().getUsername()
                    : message.getReceiver().getUsername();

            ConversationDTO conversation = byPartner.computeIfAbsent(otherId,
                    id -> ConversationDTO.builder()
                            .otherUserId(otherId)
                            .otherUsername(otherUsername)
                            .build());

            if (conversation.getLastMessageAt() == null
                    || message.getCreatedAt().isAfter(conversation.getLastMessageAt())) {
                conversation.setLastMessage(message.getMessage());
                conversation.setLastMessageAt(message.getCreatedAt());
            }
            if (incoming && !message.isRead()) {
                conversation.setUnreadCount(conversation.getUnreadCount() + 1);
            }
        }

        return byPartner.values().stream()
                .sorted(Comparator.comparing(
                        ConversationDTO::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public List<ChatMessageDTO> getMessages(Long userId, Long otherId) {
        if (otherId == null || otherId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation partner");
        }
        chatMessageRepository.markThreadRead(userId, otherId);
        return chatMessageRepository.findThread(userId, otherId).stream()
                .map(ChatMessageDTO::fromEntity)
                .toList();
    }

    @Transactional
    public ChatMessageDTO sendMessage(Long userId, SendMessageRequest request) {
        Long receiverId = request.getReceiverId();
        if (receiverId == null || receiverId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot message yourself");
        }

        String text = request.getMessage();
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Message must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }

        User sender = userService.getById(userId);
        User receiver = userService.getById(receiverId);

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .message(text.trim())
                .read(false)
                .build());
        return ChatMessageDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return chatMessageRepository.countByReceiverIdAndReadFalse(userId);
    }
}
