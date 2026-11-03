package com.aucolher.api.adoption.dto;

import com.aucolher.api.adoption.entity.ReturnReason;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.shared.validation.Sanitizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do POST /api/adoption-requests/{id}/rehome: por que o adotante vai
 * repassar o animal e o anúncio novo, revisado por ele. O anúncio passa pelas
 * mesmas validações do cadastro; o motivo e a nota, pelas regras do retorno
 * (conferidas na Service, com as frases do contrato).
 */
public record RehomeDTO(
        ReturnReason reason,
        String note,

        @NotNull(message = "Preencha o anúncio do animal")
        @Valid
        AnimalRequestDTO animal
) {

    public RehomeDTO {
        note = Sanitizer.text(note);
    }
}
