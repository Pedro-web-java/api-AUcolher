package com.aucolher.api.match;

import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalSize;
import com.aucolher.api.animal.entity.Level;
import com.aucolher.api.animal.entity.Temperament;
import com.aucolher.api.match.MatchResult.CriterionScore;
import com.aucolher.api.match.entity.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O algoritmo do AUmatch: cruza as respostas do quiz com o comportamento do
 * animal e dá um score de 0 a 100, somando os critérios de {@link MatchCriterion}.
 *
 * O frontend tem uma cópia em features/aumatch/utils/matchScore.js (cards de
 * pedidos recebidos e textos do "Por que deu match?") — mudou aqui, muda lá.
 *
 * Regras:
 * - Escalas ordenadas (energia, independência, porte): igual vale o máximo,
 *   vizinho vale 40% e oposto vale zero.
 * - Moradia e convivência sem resposta não restringem nada (valem o máximo);
 *   os outros critérios sem resposta valem zero.
 * - Porte sem resposta: quem mora em apartamento conta como preferindo pequeno.
 */
public final class MatchScoreCalculator {

    /** Fração do máximo que vale um nível vizinho (ex.: energia moderada para quem quer alta). */
    private static final double NEIGHBOR_RATIO = 0.4;

    private static final Map<IdealPetProfile, Set<Temperament>> TEMPERAMENTS_BY_PROFILE = Map.of(
            IdealPetProfile.CALM_COMPANION, EnumSet.of(Temperament.CALM, Temperament.AFFECTIONATE),
            IdealPetProfile.PLAYFUL_ACTIVE, EnumSet.of(Temperament.PLAYFUL),
            IdealPetProfile.PROTECTIVE_INDEPENDENT, EnumSet.of(Temperament.PROTECTIVE, Temperament.INDEPENDENT));

    private MatchScoreCalculator() {
    }

    /** Score de um animal para um perfil. Perfil nulo = ninguém respondeu nada ainda. */
    public static MatchResult score(MatchProfile profile, Animal animal) {
        MatchProfile answers = profile != null ? profile : new MatchProfile();

        List<CriterionScore> criteria = List.of(
                housing(answers, animal),
                energy(answers, animal),
                independence(answers, animal),
                living(answers, animal),
                temperament(answers, animal),
                size(answers, animal));

        int total = criteria.stream().mapToInt(CriterionScore::points).sum();
        return new MatchResult(Math.max(0, Math.min(100, total)), criteria);
    }

    private static CriterionScore housing(MatchProfile answers, Animal animal) {
        int max = MatchCriterion.HOUSING.maxPoints();
        int points = answers.getHousing() == Housing.APARTMENT
                ? (Boolean.TRUE.equals(animal.getApartmentFriendly()) ? max : 0)
                : max;
        return new CriterionScore(MatchCriterion.HOUSING, points, answers.getHousing() != null);
    }

    private static CriterionScore energy(MatchProfile answers, Animal animal) {
        Level desired = answers.getExerciseRoutine() == null ? null : switch (answers.getExerciseRoutine()) {
            case SEDENTARY -> Level.LOW;
            case ACTIVE -> Level.MODERATE;
            case VERY_ACTIVE -> Level.HIGH;
        };
        return new CriterionScore(MatchCriterion.ENERGY,
                proximity(desired, animal.getEnergyLevel(), MatchCriterion.ENERGY.maxPoints()),
                desired != null);
    }

    private static CriterionScore independence(MatchProfile answers, Animal animal) {
        Level desired = answers.getTimeAway() == null ? null : switch (answers.getTimeAway()) {
            case SHORT -> Level.LOW;
            case REGULAR -> Level.MODERATE;
            case LONG -> Level.HIGH;
        };
        return new CriterionScore(MatchCriterion.INDEPENDENCE,
                proximity(desired, animal.getIndependenceLevel(), MatchCriterion.INDEPENDENCE.maxPoints()),
                desired != null);
    }

    /**
     * Cada grupo que mora com a pessoa (crianças, cães, gatos) precisa de um
     * animal que conviva bem com ele: os pontos são proporcionais aos grupos
     * compatíveis. "Ninguém" ou sem resposta vale o máximo.
     */
    private static CriterionScore living(MatchProfile answers, Animal animal) {
        int max = MatchCriterion.LIVING.maxPoints();
        if (!answers.hasHouseholdAnswer()) {
            return new CriterionScore(MatchCriterion.LIVING, max, false);
        }

        List<Boolean> compatibility = new ArrayList<>();
        if (answers.getHasChildren()) compatibility.add(animal.getGoodWithChildren());
        if (answers.getHasDogs()) compatibility.add(animal.getGoodWithDogs());
        if (answers.getHasCats()) compatibility.add(animal.getGoodWithCats());

        if (compatibility.isEmpty()) {
            return new CriterionScore(MatchCriterion.LIVING, max, true);
        }
        long compatible = compatibility.stream().filter(Boolean.TRUE::equals).count();
        int points = (int) Math.round((double) max * compatible / compatibility.size());
        return new CriterionScore(MatchCriterion.LIVING, points, true);
    }

    private static CriterionScore temperament(MatchProfile answers, Animal animal) {
        IdealPetProfile desired = answers.getIdealPetProfile();
        boolean matches = desired != null && TEMPERAMENTS_BY_PROFILE.get(desired).contains(animal.getTemperament());
        return new CriterionScore(MatchCriterion.TEMPERAMENT,
                matches ? MatchCriterion.TEMPERAMENT.maxPoints() : 0,
                desired != null);
    }

    private static CriterionScore size(MatchProfile answers, Animal animal) {
        AnimalSize preferred = answers.getPreferredSize();
        if (preferred == null && answers.getHousing() == Housing.APARTMENT) {
            preferred = AnimalSize.SMALL;
        }
        return new CriterionScore(MatchCriterion.SIZE,
                proximity(preferred, animal.getSize(), MatchCriterion.SIZE.maxPoints()),
                answers.getPreferredSize() != null);
    }

    /**
     * Distância entre dois valores de uma escala ordenada — Level
     * ({@code LOW < MODERATE < HIGH}) e AnimalSize ({@code SMALL < MEDIUM < LARGE})
     * são declarados em ordem, então a posição no enum é a posição na escala.
     */
    private static <E extends Enum<E>> int proximity(E desired, E actual, int max) {
        if (desired == null || actual == null) return 0;
        return switch (Math.abs(desired.ordinal() - actual.ordinal())) {
            case 0 -> max;
            case 1 -> (int) Math.round(max * NEIGHBOR_RATIO);
            default -> 0;
        };
    }
}
