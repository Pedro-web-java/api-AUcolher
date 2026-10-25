package com.aucolher.api.match.dto;

import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.match.MatchResult;

import java.util.List;

/** Um card do deck do AUmatch: o animal, o score de compatibilidade (0 a 100) e os pontos de cada critério. */
public record MatchCardDTO(
        AnimalSummaryDTO animal,
        int matchScore,
        List<MatchCriterionDTO> criteria
) {

    public static MatchCardDTO of(AnimalSummaryDTO animal, MatchResult result) {
        return new MatchCardDTO(
                animal,
                result.score(),
                result.criteria().stream().map(MatchCriterionDTO::from).toList());
    }
}
