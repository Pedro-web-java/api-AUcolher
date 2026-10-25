package com.aucolher.api.campaign.dto;

import com.aucolher.api.campaign.entity.CampaignCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CampaignRequestDTO(
        @NotBlank @Size(min = 3, max = 120) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotNull CampaignCategory category,
        @Size(max = 30) String customCategory,
        @JsonProperty("isUrgent") Boolean isUrgent,
        @NotNull @Min(50) @Max(1_000_000) Long goalAmount,
        LocalDate deadline,
        String coverUrl) {
}
