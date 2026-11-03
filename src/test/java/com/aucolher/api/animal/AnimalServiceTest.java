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
import org.springframework.context.ApplicationEventPublisher;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
    void leavingTheListingNotifiesTheAdoptionRequests() {
        Animal animal = animal(AnimalStatus.AVAILABLE);
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal));
        when(animalRepository.saveAndFlush(animal)).thenReturn(animal);

        animalService.deactivate(10L, OWNER_EMAIL);
        verify(eventPublisher).publishEvent(new AnimalLeftListingEvent(10L));

        // Voltar ao ar não cancela nada
        animalService.changeStatus(10L, OWNER_EMAIL, AnimalStatus.AVAILABLE);
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    void unknownProfileReturns404() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalService.listByOwner(99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===================== Moderação =====================

    @Test
    void bannedOwnerHasNoPublicAnimals() {
        User banned = user(1L, OWNER_EMAIL);
        banned.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> animalService.listByOwner(1L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminStillSeesTheAnimalsOfABannedOwner() {
        User banned = user(1L, OWNER_EMAIL);
        banned.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(banned));
        when(userRepository.findByEmail("admin@email.com")).thenReturn(Optional.of(
                User.builder().id(9L).email("admin@email.com").userType(UserType.ADMIN).build()));
        when(animalRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(1L, AnimalStatus.AVAILABLE)).thenReturn(List.of());

        assertThat(animalService.listByOwner(1L, "admin@email.com")).isEmpty();
    }

    @Test
    void removedOrBannedListingIsHiddenFromEveryoneButTheAdmin() {
        Animal removed = animal(AnimalStatus.REMOVED);
        when(animalRepository.findById(10L)).thenReturn(Optional.of(removed));
        when(userRepository.findByEmail("admin@email.com")).thenReturn(Optional.of(
                User.builder().id(9L).email("admin@email.com").userType(UserType.ADMIN).build()));
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(user(1L, OWNER_EMAIL)));

        assertThatThrownBy(() -> animalService.getDetail(10L, OWNER_EMAIL)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(animalService.getDetail(10L, "admin@email.com").status()).isEqualTo(AnimalStatus.REMOVED);

        Animal ofBannedOwner = animal(AnimalStatus.AVAILABLE);
        ofBannedOwner.getOwner().setActive(false);
        when(animalRepository.findById(10L)).thenReturn(Optional.of(ofBannedOwner));
        assertThatThrownBy(() -> animalService.getDetail(10L, null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ownerCannotTouchAListingRemovedByModeration() {
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.REMOVED)));

        assertThatThrownBy(() -> animalService.changeStatus(10L, OWNER_EMAIL, AnimalStatus.AVAILABLE))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Este anúncio foi removido pela moderação e não pode mais ser alterado.");
        assertThatThrownBy(() -> animalService.update(10L, OWNER_EMAIL, validRequest()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> animalService.deactivate(10L, OWNER_EMAIL))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void onlyModerationRemovesAListing() {
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.AVAILABLE)));

        assertThatThrownBy(() -> animalService.changeStatus(10L, OWNER_EMAIL, AnimalStatus.REMOVED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void moderationRemovalCancelsTheRequestsWithItsOwnReason() {
        Animal listing = animal(AnimalStatus.AVAILABLE);
        when(animalRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(animalRepository.saveAndFlush(listing)).thenReturn(listing);

        animalService.removeByModeration(10L);

        assertThat(listing.getStatus()).isEqualTo(AnimalStatus.REMOVED);
        verify(eventPublisher).publishEvent(new AnimalLeftListingEvent(10L, true));
    }

    @Test
    void moderationKeepsAdoptedAndAlreadyRemovedListings() {
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.ADOPTED)));
        assertThatThrownBy(() -> animalService.removeByModeration(10L))
                .hasMessage("Este animal já foi adotado: o anúncio fica só como histórico.");

        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.REMOVED)));
        assertThatThrownBy(() -> animalService.removeByModeration(10L))
                .hasMessage("Este anúncio já foi removido.");
    }

    // ===================== Retorno e repasse =====================

    @Test
    void returningAnAdoptionTakesTheListingOffline() {
        Animal adopted = animal(AnimalStatus.ADOPTED);

        animalService.returnToOwner(adopted);

        assertThat(adopted.getStatus()).isEqualTo(AnimalStatus.INACTIVE);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rehomeDraftCopiesTheListingWithItsPhotos() {
        Animal original = animal(AnimalStatus.ADOPTED);
        original.setName("Thor");
        original.setStory("Resgatado na estrada.");

        AnimalRequestDTO draft = animalService.toRehomeDraft(original);

        assertThat(draft.name()).isEqualTo("Thor");
        assertThat(draft.story()).isEqualTo("Resgatado na estrada.");
        assertThat(draft.photos()).containsExactly("https://fotos/1.jpg");
    }

    @Test
    void rehomedListingBelongsToTheAdopterAndPointsToTheRequest() {
        User adopter = user(5L, OTHER_EMAIL);
        when(animalRepository.saveAndFlush(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Animal listing = animalService.createRehomed(adopter, validRequest(), 50L);

        assertThat(listing.getOwner()).isSameAs(adopter);
        assertThat(listing.getOriginRequestId()).isEqualTo(50L);
        assertThat(listing.getStatus()).isEqualTo(AnimalStatus.AVAILABLE);
        assertThat(listing.getPhotos()).containsExactly("https://fotos/1.jpg", "https://fotos/2.jpg");
    }

    @Test
    void rehomingRequiresTheAdopterCityAndState() {
        User adopter = user(5L, OTHER_EMAIL);
        adopter.setState(null);

        assertThatThrownBy(() -> animalService.createRehomed(adopter, validRequest(), 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cidade e a UF");
    }

    @Test
    void withdrawingARehomedListingTakesItOfflineAndUnlinksIt() {
        Animal listing = animal(AnimalStatus.AVAILABLE);
        listing.setOriginRequestId(50L);

        animalService.withdrawRehomed(listing);

        assertThat(listing.getStatus()).isEqualTo(AnimalStatus.INACTIVE);
        assertThat(listing.getOriginRequestId()).isNull();
        verify(eventPublisher).publishEvent(new AnimalLeftListingEvent(10L));
    }

    @Test
    void withdrawingAnOfflineRehomedListingOnlyUnlinksIt() {
        Animal listing = animal(AnimalStatus.INACTIVE);
        listing.setOriginRequestId(50L);

        animalService.withdrawRehomed(listing);

        assertThat(listing.getStatus()).isEqualTo(AnimalStatus.INACTIVE);
        assertThat(listing.getOriginRequestId()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deletingTheAccountTakesOnlyTheListedAnimalsOffline() {
        Animal listed = animal(AnimalStatus.AVAILABLE);
        when(animalRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(1L, AnimalStatus.AVAILABLE))
                .thenReturn(List.of(listed));

        animalService.unlistAllOf(1L);

        assertThat(listed.getStatus()).isEqualTo(AnimalStatus.INACTIVE);
        // Os pedidos já foram cancelados com ACCOUNT_DELETED: nada de ANIMAL_UNAVAILABLE
        verifyNoInteractions(eventPublisher);
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
