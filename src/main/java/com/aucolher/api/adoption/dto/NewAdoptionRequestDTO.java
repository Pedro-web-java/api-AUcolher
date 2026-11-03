package com.aucolher.api.adoption.dto;

import jakarta.validation.constraints.NotNull;

/** Corpo do POST /api/adoption-requests: o animal pedido. Quem pede vem do token. */
public record NewAdoptionRequestDTO(
        @NotNull(message = "Informe o animal")
        Long animalId
) {
}
