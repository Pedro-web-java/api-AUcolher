package com.aucolher.api.animal;

import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.animal.entity.*;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.UserType;
import com.aucolher.api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regras de negócio da AnimalService, com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    private static final String OWNER_EMAIL = "owner@email.com";
    private static final String OTHER_EMAIL = "other@email.com";

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnimalService animalService;

    @Test
    void createRequiresOwnerCityAndState() {
        User withoutAddress = user(1L, OWNER_EMAIL);
        withoutAddress.setCity(null);
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(withoutAddress));

        assertThatThrownBy(() -> animalService.create(OWNER_EMAIL, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cidade e a UF");
        verify(animalRepository, never()).save(any());
    }

    @Test
    void createSavesOwnerAndPhotosInOrder() {
        User owner = user(1L, OWNER_EMAIL);
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(animalRepository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = animalService.create(OWNER_EMAIL, validRequest());

        assertThat(response.owner().id()).isEqualTo(1L);
        assertThat(response.city()).isEqualTo("Araranguá");
        assertThat(response.photos()).containsExactly("https://fotos/1.jpg", "https://fotos/2.jpg");
        assertThat(response.status()).isEqualTo(AnimalStatus.AVAILABLE);
    }

    @Test
    void inactiveAnimalIsHiddenFromNonOwners() {
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.INACTIVE)));

        assertThatThrownBy(() -> animalService.getDetail(10L, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> animalService.getDetail(10L, OTHER_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(animalService.getDetail(10L, OWNER_EMAIL).id()).isEqualTo(10L);
    }

    @Test
    void onlyOwnerCanUpdate() {
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.AVAILABLE)));

        assertThatThrownBy(() -> animalService.update(10L, OTHER_EMAIL, validRequest()))
                .isInstanceOf(ForbiddenException.class);
        verify(animalRepository, never()).saveAndFlush(any());
    }

    @Test
    void adoptedAnimalIsFrozen() {
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.ADOPTED)));

        assertThatThrownBy(() -> animalService.update(10L, OWNER_EMAIL, validRequest()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> animalService.changeStatus(10L, OWNER_EMAIL, AnimalStatus.AVAILABLE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> animalService.deactivate(10L, OWNER_EMAIL))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteDeactivatesWithoutRemoving() {
        Animal animal = animal(AnimalStatus.AVAILABLE);
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal));
        when(animalRepository.saveAndFlush(animal)).thenReturn(animal);

        animalService.deactivate(10L, OWNER_EMAIL);

        assertThat(animal.getStatus()).isEqualTo(AnimalStatus.INACTIVE);
        verify(animalRepository, never()).delete(any(Animal.class));
    }

    @Test
    void unknownProfileReturns404() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> animalService.listByOwner(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===================== Dados de teste =====================

    private static User user(Long id, String email) {
        return User.builder()
                .id(id)
                .name("ONG Teste")
                .email(email)
                .userType(UserType.NGO)
                .city("Araranguá")
                .state("SC")
                .build();
    }

    private static Animal animal(AnimalStatus status) {
        Animal animal = new Animal();
        animal.setId(10L);
        animal.setOwner(user(1L, OWNER_EMAIL));
        animal.setStatus(status);
        animal.getPhotos().add("https://fotos/1.jpg");
        return animal;
    }

    private static AnimalRequestDTO validRequest() {
        return new AnimalRequestDTO(
                "Thor", Species.DOG, "Vira-lata", Sex.MALE, 3, AgeUnit.YEARS, AnimalSize.LARGE,
                true, true, true, false,
                Level.HIGH, Temperament.PROTECTIVE, Level.MODERATE, Level.MODERATE,
                true, true, false, false,
                "Protetor e brincalhão", "Thor foi resgatado ainda filhote.",
                List.of("https://fotos/1.jpg", "https://fotos/2.jpg"));
    }
}
