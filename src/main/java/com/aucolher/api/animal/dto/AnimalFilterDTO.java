package com.aucolher.api.animal.dto;

import com.aucolher.api.animal.entity.*;
import com.aucolher.api.shared.validation.Sanitizer;

import java.util.List;

/**
 * Filtros da listagem pública, lidos da query string. Todos opcionais; os de
 * múltipla escolha têm nome no plural e aceitam o parâmetro repetido, como os
 * checkboxes do frontend (o plural também evita o conflito de `sizes` com o
 * `size` da paginação):
 * /api/animals?species=DOG&species=CAT&sizes=SMALL&city=Araranguá
 *
 * `search` procura o texto no nome, na raça e na cidade do animal.
 */
public record AnimalFilterDTO(
        String search,
        List<Species> species,
        List<AnimalSize> sizes,
        List<Sex> sexes,
        List<AgeGroup> ageGroups,
        List<Level> energyLevels,
        List<Temperament> temperaments,
        Boolean specialNeeds,
        String city,
        String state
) {

    public AnimalFilterDTO {
        search = Sanitizer.text(search);
        city = Sanitizer.text(city);
        state = Sanitizer.uppercase(state);
    }
}
