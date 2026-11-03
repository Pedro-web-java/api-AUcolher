package com.aucolher.api.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ConversationSummaryDTO(Long requestId, long unreadCount, ChatMessageDTO lastMessage) {
}
