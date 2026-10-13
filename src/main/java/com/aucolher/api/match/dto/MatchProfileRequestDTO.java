package com.aucolher.api.match.dto;

import com.aucolher.api.animal.entity.AnimalSize;
import com.aucolher.api.match.entity.*;
import jakarta.validation.constraints.AssertTrue;

/**
 * Respostas do quiz do AUmatch (PUT /api/match/profile). É uma substituição
 * completa: pergunta que não vier fica sem resposta. Todas são opcionais,
 * porque "Minha conta" permite salvar só parte delas.
 *
 * "Quem mora com você?" chega como três booleanos: crianças, cães e gatos
 * (todos false = "Ninguém").
 */
public record MatchProfileRequestDTO(
        Housing housing,
        ExerciseRoutine exerciseRoutine,
        TimeAway timeAway,
        Boolean hasChildren,
        Boolean hasDogs,
        Boolean hasCats,
        SpeciesPreference speciesPreference,
        IdealPetProfile idealPetProfile,
        AnimalSize preferredSize
) {

    /** Os três booleanos vêm juntos ou nenhum vem. Método de validação, não um campo do JSON. */
    @AssertTrue(message = "Informe crianças, cães e gatos juntos na pergunta \"Quem mora com você?\"")
    public boolean isHouseholdValid() {
        return (hasChildren == null) == (hasDogs == null) && (hasDogs == null) == (hasCats == null);
    }
}
