package com.aucolher.api.animal.dto;

import com.aucolher.api.animal.entity.*;

import java.time.LocalDateTime;

/**
 * Animal "de card": listagem, meus animais, perfil público e favoritos.
 *
 * Traz só a foto de capa e deixa a história de fora — com até 4 fotos em
 * data URL por animal, uma página de 12 animais completos passaria de 10 MB.
 * Para o anúncio inteiro, use GET /api/animals/{id} (AnimalDetailDTO).
 */
public record AnimalSummaryDTO(
        Long id,
        String name,
        Species species,
        String breed,
        Sex sex,
        Integer ageValue,
        AgeUnit ageUnit,
        AgeGroup ageGroup,
        AnimalSize size,
        Boolean vaccinated,
        Boolean neutered,
        Boolean dewormed,
        Boolean specialNeeds,
        Level energyLevel,
        Temperament temperament,
        Boolean goodWithChildren,
        Boolean goodWithDogs,
        Boolean goodWithCats,
        Boolean apartmentFriendly,
        String summary,
        AnimalStatus status,
        String coverPhoto,
        String city,
        String state,
        AnimalOwnerDTO owner,
        LocalDateTime createdAt
) {

    public static AnimalSummaryDTO from(Animal animal, String coverPhoto) {
        return new AnimalSummaryDTO(
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
                animal.getGoodWithChildren(),
                animal.getGoodWithDogs(),
                animal.getGoodWithCats(),
                animal.getApartmentFriendly(),
                animal.getSummary(),
                animal.getStatus(),
                coverPhoto,
                animal.getOwner().getCity(),
                animal.getOwner().getState(),
                AnimalOwnerDTO.from(animal.getOwner()),
                animal.getCreatedAt()
        );
    }
}
