package com.aucolher.api.chat;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ChatReadId implements Serializable {

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "user_id")
    private Long userId;

    protected ChatReadId() {
    }

    public ChatReadId(Long requestId, Long userId) {
        this.requestId = requestId;
        this.userId = userId;
    }

    public Long getRequestId() { return requestId; }
    public Long getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatReadId other)) return false;
        return Objects.equals(requestId, other.requestId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, userId);
    }
}
