package com.aucolher.api.match.dto;

import com.aucolher.api.match.MatchCriterion;
import com.aucolher.api.match.MatchResult.CriterionScore;

/**
 * Pontos de um critério no "Por que deu match?". O texto explicativo fica no
 * frontend, escolhido pela `key`. `answered: false` = a pessoa ainda não
 * respondeu a pergunta usada no critério.
 */
public record MatchCriterionDTO(
        MatchCriterion key,
        int points,
        int maxPoints,
        boolean answered
) {

    public static MatchCriterionDTO from(CriterionScore score) {
        return new MatchCriterionDTO(score.criterion(), score.points(), score.maxPoints(), score.answered());
    }
}
