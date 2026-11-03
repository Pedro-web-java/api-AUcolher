package com.aucolher.api.chat;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** O id do pedido de adoção é o id da conversa. */
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** Nulo nas mensagens de sistema. */
    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private MessageType type;

    @Column(name = "text", length = 1000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_event", length = 30)
    private SystemEvent systemEvent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ChatMessage() {
    }

    public static ChatMessage fromUser(Long requestId, Long senderId, String text) {
        ChatMessage m = new ChatMessage();
        m.requestId = requestId;
        m.senderId = senderId;
        m.type = MessageType.USER;
        m.text = text;
        return m;
    }

    public static ChatMessage system(Long requestId, SystemEvent event) {
        ChatMessage m = new ChatMessage();
        m.requestId = requestId;
        m.type = MessageType.SYSTEM;
        m.systemEvent = event;
        return m;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public Long getSenderId() { return senderId; }
    public MessageType getType() { return type; }
    public String getText() { return text; }
    public SystemEvent getSystemEvent() { return systemEvent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
