package com.aucolher.api.chat.dto;

import java.time.LocalDateTime;

import com.aucolher.api.chat.ChatMessage;
import com.aucolher.api.chat.MessageType;
import com.aucolher.api.chat.SystemEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * USER: senderId e text preenchidos, systemEvent nulo.
 * SYSTEM: só systemEvent. createdAt sem fuso, como no resto da API.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChatMessageDTO(
        Long id,
        MessageType type,
        Long senderId,
        String text,
        SystemEvent systemEvent,
        LocalDateTime createdAt) {

    public static ChatMessageDTO from(ChatMessage m) {
        return new ChatMessageDTO(m.getId(), m.getType(), m.getSenderId(), m.getText(),
                m.getSystemEvent(), m.getCreatedAt());
    }
}
