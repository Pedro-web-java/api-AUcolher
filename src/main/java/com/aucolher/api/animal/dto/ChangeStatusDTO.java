package com.aucolher.api.animal.dto;

import com.aucolher.api.animal.entity.AnimalStatus;
import jakarta.validation.constraints.NotNull;

/** Corpo do PATCH /api/animals/{id}/status — ex: { "status": "ADOPTED" }. */
public record ChangeStatusDTO(

        @NotNull(message = "Informe o novo status")
        AnimalStatus status
) {}
