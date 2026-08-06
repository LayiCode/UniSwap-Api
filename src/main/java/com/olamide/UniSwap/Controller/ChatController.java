package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.ChatMessageDTO;
import com.olamide.UniSwap.Dto.ConversationDTO;
import com.olamide.UniSwap.Dto.SendMessageRequest;
import com.olamide.UniSwap.Service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> conversations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(chatService.getConversations(currentUserId(principal)));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessageDTO>> messages(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("with") Long otherId
    ) {
        return ResponseEntity.ok(chatService.getMessages(currentUserId(principal), otherId));
    }

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageDTO> send(
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(currentUserId(principal), request));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(Map.of("count", chatService.getUnreadCount(currentUserId(principal))));
    }

    private Long currentUserId(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }
}
