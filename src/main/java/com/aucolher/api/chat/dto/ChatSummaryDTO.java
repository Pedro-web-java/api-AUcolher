package com.aucolher.api.chat.dto;

import java.util.List;

public record ChatSummaryDTO(long totalUnread, List<ConversationSummaryDTO> conversations) {
}
