package com.aucolher.api.match;

/**
 * Critérios do score de compatibilidade do AUmatch, com a pontuação máxima
 * de cada um — somam 100. O frontend usa a chave para escolher o texto e o
 * ícone do "Por que deu match?".
 */
public enum MatchCriterion {
    /** Moradia × animal que se adapta a apartamento. */
    HOUSING(15),
    /** Rotina de exercícios × energia do animal. */
    ENERGY(20),
    /** Tempo fora de casa × independência do animal. */
    INDEPENDENCE(20),
    /** Quem mora com a pessoa × convivência com crianças, cães e gatos. */
    LIVING(15),
    /** Perfil de pet ideal × temperamento do animal. */
    TEMPERAMENT(15),
    /** Porte preferido × porte do animal. */
    SIZE(15);

    private final int maxPoints;

    MatchCriterion(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public int maxPoints() {
        return maxPoints;
    }
}
