package com.aucolher.api.match;

import com.aucolher.api.adoption.AdoptionRequestRepository;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.*;
import com.aucolher.api.match.dto.MatchCardDTO;
import com.aucolher.api.match.dto.MatchProfileDTO;
import com.aucolher.api.match.dto.MatchProfileRequestDTO;
import com.aucolher.api.match.entity.*;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras do perfil e do deck do AUmatch, com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final String EMAIL = "person@email.com";
    private static final Long USER_ID = 7L;

    @Mock
    private MatchProfileRepository matchProfileRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private AnimalService animalService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdoptionRequestRepository adoptionRequestRepository;

    @InjectMocks
    private MatchService matchService;

    @Test
    void personWithoutProfileGetsEmptyAnswers() {
        givenUser();
        when(matchProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        MatchProfileDTO profile = matchService.getProfile(EMAIL);

        assertThat(profile.complete()).isFalse();
        assertThat(profile.housing()).isNull();
        assertThat(profile.hasChildren()).isNull();
    }

    @Test
    void firstAnswersCreateTheProfile() {
        givenUser();
        when(matchProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(matchProfileRepository.saveAndFlush(any(MatchProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchProfileDTO saved = matchService.saveProfile(EMAIL, completeRequest());

        ArgumentCaptor<MatchProfile> captor = ArgumentCaptor.forClass(MatchProfile.class);
        verify(matchProfileRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.complete()).isTrue();
        assertThat(saved.preferredSize()).isEqualTo(AnimalSize.SMALL);
    }

    @Test
    void savingReplacesEveryAnswer() {
        givenUser();
        MatchProfile existing = new MatchProfile(USER_ID);
        existing.setHousing(Housing.APARTMENT);
        existing.setHasChildren(true);
        existing.setHasDogs(true);
        existing.setHasCats(true);
        when(matchProfileRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(matchProfileRepository.saveAndFlush(existing)).thenReturn(existing);

        // Só o porte: as outras respostas ficam vazias (substituição completa)
        MatchProfileDTO saved = matchService.saveProfile(EMAIL,
                new MatchProfileRequestDTO(null, null, null, null, null, null, null, null, AnimalSize.LARGE));

        assertThat(saved.housing()).isNull();
        assertThat(saved.hasChildren()).isNull();
        assertThat(saved.preferredSize()).isEqualTo(AnimalSize.LARGE);
        assertThat(saved.complete()).isFalse();
    }

    @Test
    void householdAnsweredAsNobodyStillCountsAsAnswered() {
        MatchProfile profile = new MatchProfile(USER_ID);
        profile.setHousing(Housing.FARM);
        profile.setExerciseRoutine(ExerciseRoutine.ACTIVE);
        profile.setTimeAway(TimeAway.LONG);
        profile.setHasChildren(false);
        profile.setHasDogs(false);
        profile.setHasCats(false);
        profile.setSpeciesPreference(SpeciesPreference.BOTH);
        profile.setIdealPetProfile(IdealPetProfile.CALM_COMPANION);
        profile.setPreferredSize(AnimalSize.MEDIUM);

        assertThat(profile.isComplete()).isTrue();
    }

    @Test
    void deckKeepsOnlyTheWantedSpeciesAndRanksByScore() {
        givenUser();
        MatchProfile profile = new MatchProfile(USER_ID);
        profile.setSpeciesPreference(SpeciesPreference.DOG);
        profile.setPreferredSize(AnimalSize.SMALL);
        when(matchProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        Animal largeDog = animal(1L, Species.DOG, AnimalSize.LARGE, 1);
        Animal smallDog = animal(2L, Species.DOG, AnimalSize.SMALL, 2);
        Animal cat = animal(3L, Species.CAT, AnimalSize.SMALL, 3);
        Animal other = animal(4L, Species.OTHER, AnimalSize.SMALL, 4);
        givenCandidates(largeDog, cat, smallDog, other);

        List<MatchCardDTO> deck = matchService.deck(EMAIL);

        assertThat(deck).extracting(card -> card.animal().id()).containsExactly(2L, 1L);
        assertThat(deck.get(0).matchScore()).isGreaterThan(deck.get(1).matchScore());
        assertThat(deck.get(0).criteria()).hasSize(MatchCriterion.values().length);
    }

    @Test
    void deckWithoutProfileShowsEverySpeciesNewestFirst() {
        givenUser();
        when(matchProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        // Sem respostas, todo animal tem o mesmo score de base: vale o mais recente
        givenCandidates(
                animal(1L, Species.DOG, AnimalSize.MEDIUM, 1),
                animal(2L, Species.OTHER, AnimalSize.MEDIUM, 3),
                animal(3L, Species.CAT, AnimalSize.MEDIUM, 2));

        List<MatchCardDTO> deck = matchService.deck(EMAIL);

        assertThat(deck).extracting(card -> card.animal().id()).containsExactly(1L, 3L, 2L);
        assertThat(deck).extracting(MatchCardDTO::matchScore).containsOnly(30);
    }

    @Test
    void deckHidesAnimalsThePersonAlreadyRequested() {
        givenUser();
        when(matchProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(adoptionRequestRepository.findRequestedAnimalIds(USER_ID, AdoptionStatus.CANCELLED)).thenReturn(List.of(2L));
        givenCandidates(
                animal(1L, Species.DOG, AnimalSize.MEDIUM, 1),
                animal(2L, Species.DOG, AnimalSize.MEDIUM, 2));

        assertThat(matchService.deck(EMAIL)).extracting(card -> card.animal().id()).containsExactly(1L);
    }

    @Test
    void deckAsksOnlyForAvailableAnimalsOfOtherAccounts() {
        givenUser();
        when(matchProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        givenCandidates();

        assertThat(matchService.deck(EMAIL)).isEmpty();
        verify(animalRepository).findByStatusAndOwnerActiveTrueAndOwnerIdNot(AnimalStatus.AVAILABLE, USER_ID);
    }

    // ===================== Fixtures =====================

    private void givenUser() {
        User user = User.builder().id(USER_ID).email(EMAIL).name("Pessoa").userType(UserType.PERSON).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    /** O repositório devolve os candidatos; os cards saem na ordem recebida, como no AnimalService real. */
    private void givenCandidates(Animal... animals) {
        when(animalRepository.findByStatusAndOwnerActiveTrueAndOwnerIdNot(AnimalStatus.AVAILABLE, USER_ID))
                .thenReturn(List.of(animals));
        when(animalService.summarize(anyList())).thenAnswer(invocation -> {
            List<Animal> ranked = invocation.getArgument(0);
            return ranked.stream().map(animal -> AnimalSummaryDTO.from(animal, null)).toList();
        });
    }

    /** Animal de outra conta; `daysAgo` define a data do anúncio (menor = mais recente). */
    private static Animal animal(Long id, Species species, AnimalSize size, int daysAgo) {
        Animal animal = new Animal();
        animal.setId(id);
        animal.setOwner(User.builder().id(99L).email("ngo@email.com").name("ONG").userType(UserType.NGO).build());
        animal.setSpecies(species);
        animal.setSize(size);
        animal.setEnergyLevel(Level.MODERATE);
        animal.setIndependenceLevel(Level.MODERATE);
        animal.setTemperament(Temperament.CALM);
        animal.setGoodWithChildren(true);
        animal.setGoodWithDogs(true);
        animal.setGoodWithCats(true);
        animal.setApartmentFriendly(true);
        animal.setStatus(AnimalStatus.AVAILABLE);
        animal.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        return animal;
    }

    private static MatchProfileRequestDTO completeRequest() {
        return new MatchProfileRequestDTO(
                Housing.APARTMENT,
                ExerciseRoutine.SEDENTARY,
                TimeAway.SHORT,
                true, false, true,
                SpeciesPreference.DOG,
                IdealPetProfile.CALM_COMPANION,
                AnimalSize.SMALL);
    }
}
