package com.aucolher.api.campaign.dto;

import com.aucolher.api.campaign.entity.CampaignCategory;
import com.aucolher.api.campaign.entity.CampaignStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CampaignResponseDTO(
        Long id,
        String title,
        String description,
        CampaignCategory category,
        String customCategory,
        @JsonProperty("isUrgent") boolean isUrgent,
        BigDecimal goalAmount,
        BigDecimal raisedAmount,
        long donationsCount,
        String coverUrl,
        LocalDate deadline,
        CampaignStatus status,
        @JsonProperty("acceptingDonations") boolean acceptingDonations,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        CampaignNgoDTO ngo) {
}
