package com.aucolher.api.adoption.dto;

import com.aucolher.api.adoption.entity.AdoptionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do PATCH /api/adoption-requests/{id}/status: o status novo. Quem pode
 * pedir cada um (dono ou adotante) e de qual status ele parte é regra da Service.
 */
public record ChangeRequestStatusDTO(
        @NotNull(message = "Informe o novo status")
        AdoptionStatus status
) {
}
