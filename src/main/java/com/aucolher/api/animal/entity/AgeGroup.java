package com.aucolher.api.animal.entity;

/**
 * Faixa etária usada no filtro da listagem. Não vem do cliente: é calculada
 * a partir da idade sempre que o animal é salvo (ver {@link Animal}).
 */
public enum AgeGroup {
    PUPPY,
    ADULT,
    SENIOR;

    private static final int MONTHS_UNTIL_ADULT = 12;
    private static final int MONTHS_UNTIL_SENIOR = 96; // 8 anos

    /** Mesma regra do deriveAgeGroup do frontend: menos de 1 ano é filhote, a partir de 8 anos é idoso. */
    public static AgeGroup of(int ageValue, AgeUnit unit) {
        int months = unit == AgeUnit.MONTHS ? ageValue : ageValue * 12;
        if (months < MONTHS_UNTIL_ADULT) return PUPPY;
        if (months < MONTHS_UNTIL_SENIOR) return ADULT;
        return SENIOR;
    }
}
