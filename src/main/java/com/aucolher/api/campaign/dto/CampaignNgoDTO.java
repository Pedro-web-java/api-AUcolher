package com.aucolher.api.campaign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** `active` = false quando a ONG foi banida (o admin vê; nas rotas públicas ela nem aparece). */
public record CampaignNgoDTO(
        Long id,
        String name,
        @JsonProperty("isVerified") boolean isVerified,
        String photoUrl,
        String city,
        String state,
        boolean active) {
}
