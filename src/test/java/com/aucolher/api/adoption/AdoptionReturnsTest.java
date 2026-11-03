package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdoptionRequestDTO;
import com.aucolher.api.adoption.dto.RegisterReturnDTO;
import com.aucolher.api.adoption.dto.RehomeDTO;
import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.ReturnReason;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalSize;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.match.MatchProfileRepository;
import com.aucolher.api.match.entity.*;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.aucolher.api.adoption.AdoptionRequestServiceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Taxa de reabandono: o score congelado na conclusão e o retorno ao doador. */
@ExtendWith(MockitoExtension.class)
class AdoptionReturnsTest {

    static final String NGO_ADOPTER_EMAIL = "rescue@email.com";

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

    private Animal animal;

    @BeforeEach
    void setUp() {
        animal = animal(AnimalStatus.AVAILABLE);
        lenient().when(requestRepository.saveAndFlush(any(AdoptionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(animalService.summarize(anyList())).thenAnswer(invocation -> {
            List<Animal> animals = invocation.getArgument(0);
            return animals.stream().map(each -> AnimalSummaryDTO.from(each, null)).toList();
        });
        lenient().when(matchProfileRepository.findAllById(anyList())).thenReturn(List.of());
    }

    // ===================== Score congelado =====================

    @Test
    void concludingFreezesTheScoreOfACompleteQuiz() {
        givenRequest(AdoptionStatus.AWAITING_DELIVERY, adopter());
        when(matchProfileRepository.findById(1L)).thenReturn(Optional.of(completeProfile()));

        AdoptionRequestDTO concluded = requestService.changeStatus(50L, ADOPTER_EMAIL, AdoptionStatus.CONCLUDED);

        assertThat(concluded.matchScoreAtAdoption()).isNotNull().isBetween(0, 100);
    }

    @Test
    void concludingWithoutACompleteQuizLeavesTheScoreEmpty() {
        givenRequest(AdoptionStatus.AWAITING_DELIVERY, adopter());
        MatchProfile halfDone = completeProfile();
        halfDone.setPreferredSize(null);
        when(matchProfileRepository.findById(1L)).thenReturn(Optional.of(halfDone));

        assertThat(requestService.changeStatus(50L, ADOPTER_EMAIL, AdoptionStatus.CONCLUDED).matchScoreAtAdoption()).isNull();
    }

    @Test
    void ngoAdoptionsHaveNoScore() {
        givenRequest(AdoptionStatus.AWAITING_DELIVERY, ngoAdopter());

        assertThat(requestService.changeStatus(50L, NGO_ADOPTER_EMAIL, AdoptionStatus.CONCLUDED).matchScoreAtAdoption()).isNull();
        verify(matchProfileRepository, never()).findById(any());
    }

    @Test
    void returnAndRehomeAreNotPlainStatusChanges() {
        givenRequest(AdoptionStatus.CONCLUDED, adopter());

        assertThatThrownBy(() -> requestService.changeStatus(50L, OWNER_EMAIL, AdoptionStatus.RETURNED))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> requestService.changeStatus(50L, ADOPTER_EMAIL, AdoptionStatus.REHOMED))
                .isInstanceOf(BusinessException.class);
    }

    // ===================== Animal retornado =====================

    @Test
    void ownerRegistersTheReturn() {
        AdoptionRequest request = givenConcluded(adopter());
        LocalDate yesterday = LocalDate.now().minusDays(1);

        AdoptionRequestDTO returned = requestService.registerReturn(50L, OWNER_EMAIL,
                new RegisterReturnDTO(yesterday, ReturnReason.OTHER_PETS, "  Não se adaptou com os gatos.  "));

        assertThat(returned.status()).isEqualTo(AdoptionStatus.RETURNED);
        assertThat(returned.returnInfo().type()).isEqualTo(AdoptionStatus.RETURNED);
        assertThat(returned.returnInfo().date()).isEqualTo(yesterday);
        assertThat(returned.returnInfo().note()).isEqualTo("Não se adaptou com os gatos.");
        assertThat(returned.returnInfo().recordedAt()).isNotNull();
        assertThat(returned.returnInfo().rehomedAnimalId()).isNull();
        assertThat(request.getConcludedAt()).isNotNull();
        verify(animalService).returnToOwner(animal);
        verify(entityManager).refresh(request, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void registeringAgainCorrectsTheDataWithoutTouchingTheAnimal() {
        AdoptionRequest request = givenConcluded(adopter());
        request.recordReturn(LocalDate.now().minusDays(2), ReturnReason.COSTS, null);

        requestService.registerReturn(50L, OWNER_EMAIL, new RegisterReturnDTO(LocalDate.now(), ReturnReason.BEHAVIOR, null));

        assertThat(request.getReturnReason()).isEqualTo(ReturnReason.BEHAVIOR);
        assertThat(request.getReturnDate()).isEqualTo(LocalDate.now());
        verify(animalService, never()).returnToOwner(any());
    }

    @Test
    void onlyTheDonorRegistersTheReturn() {
        givenConcluded(adopter());

        assertThatThrownBy(() -> requestService.registerReturn(50L, ADOPTER_EMAIL, validReturn()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Só quem doou o animal registra o retorno");
    }

    @Test
    void ngoAdoptionsHaveNoReturn() {
        givenConcluded(ngoAdopter());

        assertThatThrownBy(() -> requestService.registerReturn(50L, OWNER_EMAIL, validReturn()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Adoções feitas por ONG não têm retorno nem repasse");
    }

    @Test
    void onlyAConcludedAdoptionCanReturn() {
        givenRequest(AdoptionStatus.AWAITING_DELIVERY, adopter());

        assertThatThrownBy(() -> requestService.registerReturn(50L, OWNER_EMAIL, validReturn()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Só uma adoção concluída pode ter o retorno registrado");
    }

    @Test
    void aRehomedAnimalCannotBeReturned() {
        AdoptionRequest request = givenConcluded(adopter());
        request.rehome(ReturnReason.COSTS, null);

        assertThatThrownBy(() -> requestService.registerReturn(50L, OWNER_EMAIL, validReturn()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O adotante já anunciou este animal para uma nova adoção");
    }

    @Test
    void returnBodyIsCheckedWithTheContractMessages() {
        givenConcluded(adopter());
        LocalDate today = LocalDate.now();

        assertReturnFails(new RegisterReturnDTO(today, null, null), "Escolha o motivo");
        assertReturnFails(new RegisterReturnDTO(today, ReturnReason.OTHER, "   "), "Descreva o motivo");
        assertReturnFails(new RegisterReturnDTO(today, ReturnReason.COSTS, "x".repeat(501)),
                "A observação pode ter no máximo 500 caracteres");
        assertReturnFails(new RegisterReturnDTO(today.plusDays(1), ReturnReason.COSTS, null),
                "A data do retorno não pode ser futura");
        assertReturnFails(new RegisterReturnDTO(today.minusDays(30), ReturnReason.COSTS, null),
                "A data do retorno não pode ser anterior à adoção");
        verify(animalService, never()).returnToOwner(any());
    }

    // ===================== Republicar anúncio =====================

    @Test
    void draftIsTheOriginalListingForTheAdopter() {
        givenConcluded(adopter());

        requestService.getRehomeDraft(50L, ADOPTER_EMAIL);

        verify(animalService).toRehomeDraft(animal);
    }

    @Test
    void adopterRehomesTheAnimal() {
        AdoptionRequest request = givenConcluded(adopter());
        when(animalRepository.findRehomedListings(List.of(50L))).thenReturn(List.of(rehomedListing(50L, 99L)));

        AdoptionRequestDTO rehomed = requestService.rehome(50L, ADOPTER_EMAIL, rehomeBody(ReturnReason.HOUSING_OR_ROUTINE, "Vou me mudar."));

        assertThat(rehomed.status()).isEqualTo(AdoptionStatus.REHOMED);
        assertThat(rehomed.returnInfo().date()).isEqualTo(LocalDate.now());
        assertThat(rehomed.returnInfo().reason()).isEqualTo(ReturnReason.HOUSING_OR_ROUTINE);
        assertThat(rehomed.returnInfo().rehomedAnimalId()).isEqualTo(99L);
        verify(animalService).createRehomed(request.getAdopter(), null, 50L);
        verify(entityManager).refresh(request, LockModeType.PESSIMISTIC_WRITE);
        assertThat(animal.getStatus()).isEqualTo(AnimalStatus.ADOPTED);
    }

    @Test
    void onlyTheAdopterRehomes() {
        givenConcluded(adopter());

        assertThatThrownBy(() -> requestService.rehome(50L, OWNER_EMAIL, rehomeBody(ReturnReason.COSTS, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Só quem adotou pode republicar o animal");
        assertThatThrownBy(() -> requestService.getRehomeDraft(50L, OWNER_EMAIL))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ngoAdoptionsHaveNoRehome() {
        givenConcluded(ngoAdopter());

        assertThatThrownBy(() -> requestService.rehome(50L, NGO_ADOPTER_EMAIL, rehomeBody(ReturnReason.COSTS, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Adoções feitas por ONG não têm retorno nem repasse");
        assertThatThrownBy(() -> requestService.getRehomeDraft(50L, NGO_ADOPTER_EMAIL))
                .hasMessage("Adoções feitas por ONG não têm retorno nem repasse");
        assertThatThrownBy(() -> requestService.undoRehome(50L, NGO_ADOPTER_EMAIL))
                .hasMessage("Adoções feitas por ONG não têm retorno nem repasse");
    }

    @Test
    void aReturnedAnimalCannotBeRehomed() {
        AdoptionRequest request = givenConcluded(adopter());
        request.recordReturn(LocalDate.now(), ReturnReason.BEHAVIOR, null);

        assertThatThrownBy(() -> requestService.rehome(50L, ADOPTER_EMAIL, rehomeBody(ReturnReason.COSTS, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Este animal já voltou para quem doou");
        assertThatThrownBy(() -> requestService.getRehomeDraft(50L, ADOPTER_EMAIL))
                .hasMessage("Este animal já voltou para quem doou");
    }

    @Test
    void onlyAConcludedAdoptionCanBeRehomed() {
        givenRequest(AdoptionStatus.AWAITING_DELIVERY, adopter());

        assertThatThrownBy(() -> requestService.rehome(50L, ADOPTER_EMAIL, rehomeBody(ReturnReason.COSTS, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Só uma adoção concluída pode ser republicada");
    }

    @Test
    void rehomeNeedsAReason() {
        givenConcluded(adopter());

        assertThatThrownBy(() -> requestService.rehome(50L, ADOPTER_EMAIL, rehomeBody(null, null)))
                .hasMessage("Escolha o motivo");
        assertThatThrownBy(() -> requestService.rehome(50L, ADOPTER_EMAIL, rehomeBody(ReturnReason.OTHER, null)))
                .hasMessage("Descreva o motivo");
        verify(animalService, never()).createRehomed(any(), any(), any());
    }

    // ===================== Desisti de doar =====================

    @Test
    void undoingBeforeANewAdoptionRestoresTheConcludedAdoption() {
        AdoptionRequest request = givenRehomed();
        Animal listing = rehomedAnimal(AnimalStatus.AVAILABLE);
        when(animalRepository.findByOriginRequestId(50L)).thenReturn(Optional.of(listing));

        AdoptionRequestDTO undone = requestService.undoRehome(50L, ADOPTER_EMAIL);

        assertThat(undone.status()).isEqualTo(AdoptionStatus.CONCLUDED);
        assertThat(undone.returnInfo()).isNull();
        assertThat(request.getReturnReason()).isNull();
        assertThat(request.getReturnRecordedAt()).isNull();
        verify(entityManager).refresh(listing, LockModeType.PESSIMISTIC_WRITE);
        verify(animalService).withdrawRehomed(listing);
    }

    @Test
    void undoingAfterTheNewListingWasAdoptedFails() {
        AdoptionRequest request = givenRehomed();
        when(animalRepository.findByOriginRequestId(50L)).thenReturn(Optional.of(rehomedAnimal(AnimalStatus.ADOPTED)));

        assertThatThrownBy(() -> requestService.undoRehome(50L, ADOPTER_EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O anúncio já gerou uma nova adoção: não dá para desfazer");
        assertThat(request.getStatus()).isEqualTo(AdoptionStatus.REHOMED);
        verify(animalService, never()).withdrawRehomed(any());
    }

    @Test
    void onlyARehomedAdoptionCanBeUndone() {
        givenConcluded(adopter());

        assertThatThrownBy(() -> requestService.undoRehome(50L, ADOPTER_EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta adoção não foi republicada");
    }

    // ===================== Fixtures =====================

    /** O anúncio novo é validado na camada HTTP (@Valid); aqui a AnimalService é simulada. */
    private static RehomeDTO rehomeBody(ReturnReason reason, String note) {
        return new RehomeDTO(reason, note, null);
    }

    private AdoptionRequest givenRehomed() {
        AdoptionRequest request = givenConcluded(adopter());
        request.rehome(ReturnReason.COSTS, null);
        return request;
    }

    private static Animal rehomedAnimal(AnimalStatus status) {
        Animal listing = new Animal();
        listing.setId(99L);
        listing.setOwner(adopter());
        listing.setOriginRequestId(50L);
        listing.setStatus(status);
        return listing;
    }

    private static AnimalRepository.RehomedListing rehomedListing(Long requestId, Long animalId) {
        return new AnimalRepository.RehomedListing() {
            public Long getRequestId() { return requestId; }
            public Long getAnimalId() { return animalId; }
        };
    }

    private void assertReturnFails(RegisterReturnDTO body, String message) {
        assertThatThrownBy(() -> requestService.registerReturn(50L, OWNER_EMAIL, body))
                .isInstanceOf(BusinessException.class)
                .hasMessage(message);
    }

    private AdoptionRequest givenRequest(AdoptionStatus status, User adopter) {
        AdoptionRequest request = request(animal, adopter, status);
        when(requestRepository.findWithDetailsById(50L)).thenReturn(Optional.of(request));
        return request;
    }

    /** Adoção concluída há 10 dias, com o animal já adotado. */
    private AdoptionRequest givenConcluded(User adopter) {
        animal.setStatus(AnimalStatus.ADOPTED);
        AdoptionRequest request = givenRequest(AdoptionStatus.CONCLUDED, adopter);
        request.setConcludedAt(LocalDateTime.now().minusDays(10));
        return request;
    }

    private static RegisterReturnDTO validReturn() {
        return new RegisterReturnDTO(LocalDate.now(), ReturnReason.BEHAVIOR, null);
    }

    static User ngoAdopter() {
        return User.builder().id(3L).email(NGO_ADOPTER_EMAIL).name("Resgate").userType(UserType.NGO).build();
    }

    static MatchProfile completeProfile() {
        MatchProfile profile = new MatchProfile();
        profile.setUserId(1L);
        profile.setHousing(Housing.HOUSE_WITH_YARD);
        profile.setExerciseRoutine(ExerciseRoutine.VERY_ACTIVE);
        profile.setTimeAway(TimeAway.SHORT);
        profile.setHasChildren(false);
        profile.setHasDogs(false);
        profile.setHasCats(false);
        profile.setSpeciesPreference(SpeciesPreference.DOG);
        profile.setIdealPetProfile(IdealPetProfile.PROTECTIVE_INDEPENDENT);
        profile.setPreferredSize(AnimalSize.LARGE);
        return profile;
    }
}
