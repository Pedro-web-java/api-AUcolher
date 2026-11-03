package com.aucolher.api.adoption.dto;

import com.aucolher.api.shared.validation.Sanitizer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo do PUT /api/adoption-requests/{id}/review: as estrelas e um comentário opcional. */
public record SaveReviewDTO(
        @NotNull(message = "Dê de 1 a 5 estrelas")
        @Min(value = 1, message = "Dê de 1 a 5 estrelas")
        @Max(value = 5, message = "Dê de 1 a 5 estrelas")
        Integer rating,

        @Size(max = 500, message = "O comentário deve ter no máximo 500 caracteres")
        String comment
) {

    public SaveReviewDTO {
        comment = Sanitizer.text(comment);
    }
}
