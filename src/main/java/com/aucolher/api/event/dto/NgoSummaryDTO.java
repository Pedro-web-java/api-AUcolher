package com.aucolher.api.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NgoSummaryDTO(
        Long id,
        String name,
        @JsonProperty("isVerified") boolean isVerified,
        String photoUrl,
        // false = ONG banida pela moderação (só o admin chega a ver)
        boolean active
) {
}
