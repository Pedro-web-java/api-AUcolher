package com.aucolher.api.adoption.dto;

import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.ReturnReason;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * O retorno (RETURNED) ou o repasse (REHOMED) de uma adoção, visto pelas duas
 * partes. `type` repete o status; `rehomedAnimalId` é o anúncio novo do repasse.
 */
public record ReturnInfoDTO(
        AdoptionStatus type,
        LocalDate date,
        ReturnReason reason,
        String note,
        LocalDateTime recordedAt,
        Long rehomedAnimalId
) {

    /** Nulo fora de RETURNED e REHOMED. */
    public static ReturnInfoDTO of(AdoptionRequest request, Long rehomedAnimalId) {
        if (request.getStatus() != AdoptionStatus.RETURNED && request.getStatus() != AdoptionStatus.REHOMED) {
            return null;
        }
        return new ReturnInfoDTO(
                request.getStatus(),
                request.getReturnDate(),
                request.getReturnReason(),
                request.getReturnNote(),
                request.getReturnRecordedAt(),
                request.getStatus() == AdoptionStatus.REHOMED ? rehomedAnimalId : null);
    }
}
