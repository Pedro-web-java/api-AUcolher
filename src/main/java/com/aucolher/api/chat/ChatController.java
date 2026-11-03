package com.aucolher.api.chat;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aucolher.api.chat.dto.ChatMessageDTO;
import com.aucolher.api.chat.dto.ChatSummaryDTO;
import com.aucolher.api.chat.dto.MarkReadDTO;
import com.aucolher.api.chat.dto.SendMessageDTO;
import com.aucolher.api.common.security.CurrentUserProvider;

/** Só PERSON e NGO (o admin recebe 403 pelo SecurityConfig). Novas mensagens chegam por polling. */
@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService service;
    private final CurrentUserProvider currentUser;

    public ChatController(ChatService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    public ChatSummaryDTO summary() {
        return service.summary(currentUser.requireUserId());
    }

    @GetMapping("/{requestId}/messages")
    public List<ChatMessageDTO> messages(@PathVariable long requestId,
                                         @RequestParam(required = false) Long after) {
        return service.listMessages(currentUser.requireUserId(), requestId, after);
    }

    @PostMapping("/{requestId}/messages")
    public ResponseEntity<ChatMessageDTO> send(@PathVariable long requestId,
                                               @RequestBody(required = false) SendMessageDTO body) {
        ChatMessageDTO created = service.send(currentUser.requireUserId(), requestId,
                body == null ? null : body.text());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{requestId}/read")
    public ResponseEntity<Void> read(@PathVariable long requestId,
                                     @RequestBody(required = false) MarkReadDTO body) {
        service.markRead(currentUser.requireUserId(), requestId, body == null ? null : body.lastMessageId());
        return ResponseEntity.noContent().build();
    }
}
