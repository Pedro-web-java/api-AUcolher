package com.aucolher.api.match;

import com.aucolher.api.animal.entity.*;
import com.aucolher.api.match.MatchResult.CriterionScore;
import com.aucolher.api.match.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O algoritmo do AUmatch, critério por critério. Os mesmos casos valem para
 * a cópia do frontend (features/aumatch/utils/matchScore.js).
 */
class MatchScoreCalculatorTest {

    @Test
    void criteriaAddUpTo100() {
        assertThat(Arrays.stream(MatchCriterion.values()).mapToInt(MatchCriterion::maxPoints).sum()).isEqualTo(100);
    }

    @Test
    void noAnswersOnlyKeepHousingAndLiving() {
        MatchResult result = MatchScoreCalculator.score(null, animal());

        assertThat(result.score()).isEqualTo(30);
        assertThat(result.criteria()).extracting(CriterionScore::answered).containsOnly(false);
        assertThat(result.criteria()).extracting(CriterionScore::criterion).containsExactly(MatchCriterion.values());
    }

    @Test
    void perfectMatchScores100() {
        MatchProfile profile = new MatchProfile();
        profile.setHousing(Housing.APARTMENT);
        profile.setExerciseRoutine(ExerciseRoutine.SEDENTARY);
        profile.setTimeAway(TimeAway.LONG);
        household(profile, true, false, true);
        profile.setIdealPetProfile(IdealPetProfile.CALM_COMPANION);
        profile.setPreferredSize(AnimalSize.SMALL);

        Animal animal = animal();
        animal.setApartmentFriendly(true);
        animal.setEnergyLevel(Level.LOW);
        animal.setIndependenceLevel(Level.HIGH);
        animal.setGoodWithChildren(true);
        animal.setGoodWithCats(true);
        animal.setTemperament(Temperament.AFFECTIONATE);
        animal.setSize(AnimalSize.SMALL);

        MatchResult result = MatchScoreCalculator.score(profile, animal);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.criteria()).extracting(CriterionScore::answered).containsOnly(true);
    }

    @ParameterizedTest
    @CsvSource({
            "APARTMENT, true, 15",
            "APARTMENT, false, 0",
            "HOUSE_WITH_YARD, false, 15",
            "FARM, false, 15",
    })
    void housing(Housing housing, boolean apartmentFriendly, int expected) {
        MatchProfile profile = new MatchProfile();
        profile.setHousing(housing);
        Animal animal = animal();
        animal.setApartmentFriendly(apartmentFriendly);

        assertThat(points(profile, animal, MatchCriterion.HOUSING)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "SEDENTARY, LOW, 20",
            "SEDENTARY, MODERATE, 8",
            "SEDENTARY, HIGH, 0",
            "ACTIVE, HIGH, 8",
            "VERY_ACTIVE, HIGH, 20",
    })
    void energy(ExerciseRoutine routine, Level energy, int expected) {
        MatchProfile profile = new MatchProfile();
        profile.setExerciseRoutine(routine);
        Animal animal = animal();
        animal.setEnergyLevel(energy);

        assertThat(points(profile, animal, MatchCriterion.ENERGY)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "SHORT, LOW, 20",
            "REGULAR, LOW, 8",
            "LONG, MODERATE, 8",
            "LONG, LOW, 0",
    })
    void independence(TimeAway timeAway, Level independence, int expected) {
        MatchProfile profile = new MatchProfile();
        profile.setTimeAway(timeAway);
        Animal animal = animal();
        animal.setIndependenceLevel(independence);

        assertThat(points(profile, animal, MatchCriterion.INDEPENDENCE)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "crianças={0} cães={1} gatos={2} | animal {3}/{4}/{5} → {6}")
    @CsvSource({
            // Ninguém em casa: não restringe
            "false, false, false, false, false, false, 15",
            // Crianças + gatos, animal bom só com crianças: metade
            "true, false, true, true, false, false, 8",
            // Os três grupos, animal ruim só com gatos: dois terços
            "true, true, true, true, true, false, 10",
            "true, false, false, false, true, true, 0",
            "false, true, false, false, true, false, 15",
    })
    void living(boolean children, boolean dogs, boolean cats,
                boolean goodWithChildren, boolean goodWithDogs, boolean goodWithCats, int expected) {
        MatchProfile profile = new MatchProfile();
        household(profile, children, dogs, cats);
        Animal animal = animal();
        animal.setGoodWithChildren(goodWithChildren);
        animal.setGoodWithDogs(goodWithDogs);
        animal.setGoodWithCats(goodWithCats);

        assertThat(points(profile, animal, MatchCriterion.LIVING)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "CALM_COMPANION, CALM, 15",
            "CALM_COMPANION, AFFECTIONATE, 15",
            "PLAYFUL_ACTIVE, CALM, 0",
            "PROTECTIVE_INDEPENDENT, INDEPENDENT, 15",
            "PROTECTIVE_INDEPENDENT, PLAYFUL, 0",
    })
    void temperament(IdealPetProfile idealPetProfile, Temperament temperament, int expected) {
        MatchProfile profile = new MatchProfile();
        profile.setIdealPetProfile(idealPetProfile);
        Animal animal = animal();
        animal.setTemperament(temperament);

        assertThat(points(profile, animal, MatchCriterion.TEMPERAMENT)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "SMALL, SMALL, 15",
            "SMALL, MEDIUM, 6",
            "SMALL, LARGE, 0",
            "LARGE, MEDIUM, 6",
    })
    void size(AnimalSize preferred, AnimalSize size, int expected) {
        MatchProfile profile = new MatchProfile();
        profile.setPreferredSize(preferred);
        Animal animal = animal();
        animal.setSize(size);

        assertThat(points(profile, animal, MatchCriterion.SIZE)).isEqualTo(expected);
    }

    @Test
    void apartmentWithoutPreferredSizeCountsAsSmall() {
        MatchProfile profile = new MatchProfile();
        profile.setHousing(Housing.APARTMENT);
        Animal small = animal();
        small.setSize(AnimalSize.SMALL);
        Animal large = animal();
        large.setSize(AnimalSize.LARGE);

        CriterionScore smallSize = criterion(MatchScoreCalculator.score(profile, small), MatchCriterion.SIZE);
        assertThat(smallSize.points()).isEqualTo(15);
        assertThat(smallSize.answered()).isFalse(); // o quiz ainda pede o porte
        assertThat(points(profile, large, MatchCriterion.SIZE)).isZero();
    }

    @Test
    void houseWithoutPreferredSizeScoresNothingOnSize() {
        MatchProfile profile = new MatchProfile();
        profile.setHousing(Housing.HOUSE_WITH_YARD);

        assertThat(points(profile, animal(), MatchCriterion.SIZE)).isZero();
    }

    // ===================== Fixtures =====================

    private static int points(MatchProfile profile, Animal animal, MatchCriterion criterion) {
        return criterion(MatchScoreCalculator.score(profile, animal), criterion).points();
    }

    private static CriterionScore criterion(MatchResult result, MatchCriterion criterion) {
        return result.criteria().stream().filter(score -> score.criterion() == criterion).findFirst().orElseThrow();
    }

    private static void household(MatchProfile profile, boolean children, boolean dogs, boolean cats) {
        profile.setHasChildren(children);
        profile.setHasDogs(dogs);
        profile.setHasCats(cats);
    }

    /** Animal "neutro": nada combina com perfil nenhum até o teste mudar o que interessa. */
    private static Animal animal() {
        Animal animal = new Animal();
        animal.setSpecies(Species.DOG);
        animal.setSize(AnimalSize.LARGE);
        animal.setEnergyLevel(Level.HIGH);
        animal.setIndependenceLevel(Level.LOW);
        animal.setTemperament(Temperament.PROTECTIVE);
        animal.setGoodWithChildren(false);
        animal.setGoodWithDogs(false);
        animal.setGoodWithCats(false);
        animal.setApartmentFriendly(false);
        return animal;
    }
}
