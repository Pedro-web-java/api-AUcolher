package com.aucolher.api.adoption.dto;

import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.CancelReason;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Um pedido de adoção, igual para as duas partes. O dono do animal vem em
 * `animal.owner`. `matchScore` é a compatibilidade (0 a 100) entre as
 * respostas atuais do quiz do adotante e o animal, calculada na hora.
 * `matchScoreAtAdoption` é o score congelado na conclusão (nulo sem quiz
 * completo). `return` traz o retorno ou o repasse (nulo fora de RETURNED e REHOMED).
 * `reviews` traz as avaliações das duas partes (só existem depois da adoção).
 */
public record AdoptionRequestDTO(
        Long id,
        AdoptionStatus status,
        CancelReason cancelReason,
        AdoptionStatus cancelledFromStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime concludedAt,
        LocalDateTime closedAt,
        AnimalSummaryDTO animal,
        AdopterDTO adopter,
        int matchScore,
        Integer matchScoreAtAdoption,
        @JsonProperty("return") ReturnInfoDTO returnInfo,
        RequestReviewsDTO reviews
) {

    public static AdoptionRequestDTO of(AdoptionRequest request, AnimalSummaryDTO animal, AdopterDTO adopter,
                                        int matchScore, Long rehomedAnimalId, RequestReviewsDTO reviews) {
        return new AdoptionRequestDTO(
                request.getId(),
                request.getStatus(),
                request.getCancelReason(),
                request.getCancelledFromStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getConcludedAt(),
                request.getClosedAt(),
                animal,
                adopter,
                matchScore,
                request.getMatchScoreAtAdoption(),
                ReturnInfoDTO.of(request, rehomedAnimalId),
                reviews);
    }
}
