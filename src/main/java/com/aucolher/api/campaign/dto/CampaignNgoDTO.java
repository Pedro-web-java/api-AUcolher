package com.aucolher.api.campaign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CampaignNgoDTO(
        Long id,
        String name,
        @JsonProperty("isVerified") boolean isVerified,
        String photoUrl,
        String city,
        String state) {
}
