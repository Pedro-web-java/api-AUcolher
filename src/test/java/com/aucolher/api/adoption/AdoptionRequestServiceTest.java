package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdminAdoptionRequestDTO;
import com.aucolher.api.adoption.dto.AdoptionRequestDTO;
import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.*;
import com.aucolher.api.match.MatchProfileRepository;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import jakarta.persistence.EntityManager;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** Regras de criação e leitura dos pedidos de adoção, com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class AdoptionRequestServiceTest {

    static final String ADOPTER_EMAIL = "person@email.com";
    static final String OWNER_EMAIL = "ngo@email.com";
    static final String OTHER_EMAIL = "other@email.com";

    @Mock
    private AdoptionRequestRepository requestRepository;
    @Mock
    private AdoptionReviewRepository reviewRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private AnimalService animalService;
    @Mock
    private MatchProfileRepository matchProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AdoptionRequestService requestService;

    @Test
    void createSavesAPendingRequest() {
        Animal animal = animal(AnimalStatus.AVAILABLE);
        givenAdopter();
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal));
        when(requestRepository.findFirstByAdopterIdAndAnimalIdAndStatusNot(1L, 10L, AdoptionStatus.CANCELLED))
                .thenReturn(Optional.empty());
        when(requestRepository.saveAndFlush(any(AdoptionRequest.class))).thenAnswer(invocation -> {
            AdoptionRequest saved = invocation.getArgument(0);
            saved.setId(50L);
            return saved;
        });
        givenCards();

        AdoptionRequestDTO created = requestService.create(ADOPTER_EMAIL, 10L);

        assertThat(created.id()).isEqualTo(50L);
        assertThat(created.status()).isEqualTo(AdoptionStatus.PENDING);
        assertThat(created.adopter().id()).isEqualTo(1L);
        assertThat(created.animal().owner().id()).isEqualTo(2L);
        // Sem respostas do quiz, o score de base (moradia + convivência)
        assertThat(created.matchScore()).isEqualTo(30);
    }

    @Test
    void cannotRequestYourOwnAnimal() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner()));
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.AVAILABLE)));

        assertThatThrownBy(() -> requestService.create(OWNER_EMAIL, 10L)).isInstanceOf(ForbiddenException.class);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void adoptedAnimalCannotBeRequested() {
        givenAdopter();
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.ADOPTED)));

        assertThatThrownBy(() -> requestService.create(ADOPTER_EMAIL, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("adotado");
    }

    @Test
    void inactiveAnimalIsNotFound() {
        givenAdopter();
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.INACTIVE)));

        assertThatThrownBy(() -> requestService.create(ADOPTER_EMAIL, 10L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectedRequestIsFinal() {
        givenExistingRequest(AdoptionStatus.REJECTED);

        assertThatThrownBy(() -> requestService.create(ADOPTER_EMAIL, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recusou");
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void openRequestBlocksADuplicate() {
        givenExistingRequest(AdoptionStatus.PENDING);

        assertThatThrownBy(() -> requestService.create(ADOPTER_EMAIL, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já enviou");
    }

    @Test
    void receivedListBuildsEachAnimalCardOnce() {
        Animal animal = animal(AnimalStatus.AVAILABLE);
        User secondAdopter = User.builder().id(3L).email(OTHER_EMAIL).name("Outra").userType(UserType.PERSON).build();
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner()));
        when(requestRepository.findByAnimalOwnerIdOrderByCreatedAtDescIdDesc(2L))
                .thenReturn(List.of(request(animal, adopter(), AdoptionStatus.PENDING), request(animal, secondAdopter, AdoptionStatus.ACCEPTED)));
        givenCards();

        List<AdoptionRequestDTO> received = requestService.listReceived(OWNER_EMAIL);

        assertThat(received).extracting(dto -> dto.adopter().id()).containsExactly(1L, 3L);
        verify(animalService).summarize(argThat(animals -> animals.size() == 1));
    }

    @Test
    void onlyThePartiesSeeARequest() {
        AdoptionRequest request = request(animal(AnimalStatus.AVAILABLE), adopter(), AdoptionStatus.PENDING);
        when(requestRepository.findWithDetailsById(50L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.get(50L, OTHER_EMAIL)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminListShowsEveryRequestWithTheAnimalCover() {
        Animal animal = animal(AnimalStatus.AVAILABLE);
        when(requestRepository.findAllByOrderByCreatedAtDescIdDesc())
                .thenReturn(List.of(request(animal, adopter(), AdoptionStatus.CONCLUDED)));
        AnimalRepository.CoverPhoto cover = mock(AnimalRepository.CoverPhoto.class);
        when(cover.getAnimalId()).thenReturn(10L);
        when(cover.getUrl()).thenReturn("https://fotos/thor.jpg");
        when(animalRepository.findCoverPhotos(List.of(10L))).thenReturn(List.of(cover));

        List<AdminAdoptionRequestDTO> all = requestService.listAllForAdmin();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).owner().userType()).isEqualTo(UserType.NGO);
        assertThat(all.get(0).adopter().id()).isEqualTo(1L);
        assertThat(all.get(0).animal().coverPhoto()).isEqualTo("https://fotos/thor.jpg");
    }

    // ===================== Fixtures =====================

    private void givenAdopter() {
        when(userRepository.findByEmail(ADOPTER_EMAIL)).thenReturn(Optional.of(adopter()));
    }

    private void givenExistingRequest(AdoptionStatus status) {
        Animal animal = animal(AnimalStatus.AVAILABLE);
        givenAdopter();
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal));
        when(requestRepository.findFirstByAdopterIdAndAnimalIdAndStatusNot(1L, 10L, AdoptionStatus.CANCELLED))
                .thenReturn(Optional.of(request(animal, adopter(), status)));
    }

    /** Os cards saem na ordem recebida, como no AnimalService real. */
    private void givenCards() {
        when(animalService.summarize(anyList())).thenAnswer(invocation -> {
            List<Animal> animals = invocation.getArgument(0);
            return animals.stream().map(animal -> AnimalSummaryDTO.from(animal, null)).toList();
        });
        when(matchProfileRepository.findAllById(anyList())).thenReturn(List.of());
    }

    static User adopter() {
        return User.builder().id(1L).email(ADOPTER_EMAIL).name("Pessoa").userType(UserType.PERSON).city("Criciúma").state("SC").build();
    }

    static User owner() {
        return User.builder().id(2L).email(OWNER_EMAIL).name("ONG").userType(UserType.NGO).build();
    }

    static Animal animal(AnimalStatus status) {
        Animal animal = new Animal();
        animal.setId(10L);
        animal.setOwner(owner());
        animal.setName("Thor");
        animal.setSpecies(Species.DOG);
        animal.setSize(AnimalSize.LARGE);
        animal.setEnergyLevel(Level.HIGH);
        animal.setIndependenceLevel(Level.LOW);
        animal.setTemperament(Temperament.PROTECTIVE);
        animal.setGoodWithChildren(false);
        animal.setGoodWithDogs(false);
        animal.setGoodWithCats(false);
        animal.setApartmentFriendly(false);
        animal.setStatus(status);
        return animal;
    }

    static AdoptionRequest request(Animal animal, User adopter, AdoptionStatus status) {
        AdoptionRequest request = new AdoptionRequest(animal, adopter);
        request.setId(50L);
        request.setStatus(status);
        return request;
    }
}
