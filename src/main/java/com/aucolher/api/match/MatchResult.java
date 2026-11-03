package com.aucolher.api.match;

import java.util.List;

/** Resultado do score de um animal para uma pessoa: o total (0 a 100) e os pontos de cada critério. */
public record MatchResult(int score, List<CriterionScore> criteria) {

    /**
     * Pontos de um critério. `answered` diz se a pessoa respondeu a pergunta
     * usada nele — o frontend sugere completar o quiz quando algum não foi.
     */
    public record CriterionScore(MatchCriterion criterion, int points, boolean answered) {

        public int maxPoints() {
            return criterion.maxPoints();
        }
    }
}
