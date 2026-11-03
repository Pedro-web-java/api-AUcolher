package com.aucolher.api.adoption.dto;

import com.aucolher.api.adoption.entity.ReturnReason;
import com.aucolher.api.shared.validation.Sanitizer;

import java.time.LocalDate;

/**
 * Corpo do POST /api/adoption-requests/{id}/return: quando o animal voltou,
 * por quê e uma observação opcional (obrigatória em OTHER). Sem anotações de
 * validação: o contrato confere o corpo por último (depois do papel e do
 * status), então a Service valida, com as frases do contrato.
 */
public record RegisterReturnDTO(
        LocalDate returnDate,
        ReturnReason reason,
        String note
) {

    public RegisterReturnDTO {
        note = Sanitizer.text(note);
    }
}
