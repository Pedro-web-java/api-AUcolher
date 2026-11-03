package com.aucolher.api.chat;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Até qual mensagem cada parte já leu. Só é gravada pelo upsert nativo do repositório (nunca volta para trás). */
@Entity
@Table(name = "chat_reads")
public class ChatRead {

    @EmbeddedId
    private ChatReadId id;

    @Column(name = "last_read_message_id", nullable = false)
    private Long lastReadMessageId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    protected ChatRead() {
    }

    public ChatReadId getId() { return id; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
    public LocalDateTime getReadAt() { return readAt; }
}
