package com.aucolher.api.campaign.dto;

import com.aucolher.api.campaign.entity.CampaignCategory;
import com.aucolher.api.campaign.entity.CampaignStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A campanha (contrato §2). `removed`, `removedByModeration` e `moderatedAt`
 * são do AdminCampaign (§7.1): nas rotas públicas a campanha excluída nem
 * aparece, então eles só fazem diferença no painel do admin.
 */
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
        boolean removed,
        boolean removedByModeration,
        LocalDateTime moderatedAt,
        CampaignNgoDTO ngo) {
}
