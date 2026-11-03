package com.aucolher.api.campaign.dto;

import com.aucolher.api.campaign.entity.CampaignCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Corpo de POST e PUT /api/campaigns (o PUT substitui todos os campos). */
public record CampaignRequestDTO(
        @NotBlank(message = "Informe o título da campanha.")
        @Size(min = 3, max = 120, message = "O título deve ter de 3 a 120 caracteres.")
        String title,

        @NotBlank(message = "Informe a descrição da campanha.")
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres.")
        String description,

        @NotNull(message = "Escolha uma categoria.")
        CampaignCategory category,

        /** Só com category = OTHER; nas outras o service grava null. */
        @Size(max = 30, message = "O nome da categoria deve ter no máximo 30 caracteres.")
        String customCategory,

        @JsonProperty("isUrgent")
        Boolean isUrgent,

        @NotNull(message = "Informe a meta da campanha.")
        @Min(value = 50, message = "A meta mínima é de R$ 50.")
        @Max(value = 1_000_000, message = "A meta máxima é de R$ 1.000.000.")
        Long goalAmount,

        LocalDate deadline,

        String coverUrl) {
}
