package com.aucolher.api.match.dto;

import com.aucolher.api.animal.entity.AnimalSize;
import com.aucolher.api.match.entity.*;

/**
 * Perfil AUmatch da pessoa logada. `complete` diz se as 7 perguntas do quiz
 * já têm resposta — é o que decide se o frontend abre o quiz sozinho.
 */
public record MatchProfileDTO(
        Housing housing,
        ExerciseRoutine exerciseRoutine,
        TimeAway timeAway,
        Boolean hasChildren,
        Boolean hasDogs,
        Boolean hasCats,
        SpeciesPreference speciesPreference,
        IdealPetProfile idealPetProfile,
        AnimalSize preferredSize,
        boolean complete
) {

    public static MatchProfileDTO from(MatchProfile profile) {
        return new MatchProfileDTO(
                profile.getHousing(),
                profile.getExerciseRoutine(),
                profile.getTimeAway(),
                profile.getHasChildren(),
                profile.getHasDogs(),
                profile.getHasCats(),
                profile.getSpeciesPreference(),
                profile.getIdealPetProfile(),
                profile.getPreferredSize(),
                profile.isComplete()
        );
    }

    /** Quem ainda não respondeu nada: tudo vazio, em vez de 404. */
    public static MatchProfileDTO empty() {
        return new MatchProfileDTO(null, null, null, null, null, null, null, null, null, false);
    }
}
