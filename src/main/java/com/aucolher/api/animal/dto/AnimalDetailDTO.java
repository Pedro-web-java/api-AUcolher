package com.aucolher.api.animal.dto;

import com.aucolher.api.animal.entity.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Animal completo, com todas as fotos — página de detalhes e resposta do
 * cadastro/edição. Cidade e UF são as do perfil do dono.
 *
 * Dono e fotos são carregados sob demanda (lazy): monte este DTO dentro de
 * uma transação (open-in-view está desligado).
 */
public record AnimalDetailDTO(
        Long id,
        String name,
        Species species,
        String breed,
        Sex sex,
        Integer ageValue,
        AgeUnit ageUnit,
        AgeGroup ageGroup,
        AnimalSize size,

        // Saúde
        Boolean vaccinated,
        Boolean neutered,
        Boolean dewormed,
        Boolean specialNeeds,

        // Comportamento e compatibilidade
        Level energyLevel,
        Temperament temperament,
        Level independenceLevel,
        Level vocalization,
        Boolean goodWithChildren,
        Boolean goodWithDogs,
        Boolean goodWithCats,
        Boolean apartmentFriendly,

        // Anúncio
        String summary,
        String story,
        AnimalStatus status,
        List<String> photos,
        String city,
        String state,
        AnimalOwnerDTO owner,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AnimalDetailDTO from(Animal animal) {
        return new AnimalDetailDTO(
                animal.getId(),
                animal.getName(),
                animal.getSpecies(),
                animal.getBreed(),
                animal.getSex(),
                animal.getAgeValue(),
                animal.getAgeUnit(),
                animal.getAgeGroup(),
                animal.getSize(),
                animal.getVaccinated(),
                animal.getNeutered(),
                animal.getDewormed(),
                animal.getSpecialNeeds(),
                animal.getEnergyLevel(),
                animal.getTemperament(),
                animal.getIndependenceLevel(),
                animal.getVocalization(),
                animal.getGoodWithChildren(),
                animal.getGoodWithDogs(),
                animal.getGoodWithCats(),
                animal.getApartmentFriendly(),
                animal.getSummary(),
                animal.getStory(),
                animal.getStatus(),
                List.copyOf(animal.getPhotos()),
                animal.getOwner().getCity(),
                animal.getOwner().getState(),
                AnimalOwnerDTO.from(animal.getOwner()),
                animal.getCreatedAt(),
                animal.getUpdatedAt()
        );
    }
}
