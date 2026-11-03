package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdoptionRequestDTO;
import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.CancelReason;
import com.aucolher.api.animal.AnimalLeftListingEvent;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.match.MatchProfileRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.aucolher.api.adoption.AdoptionRequestServiceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** A máquina de status dos pedidos: quem pode cada transição e o que ela muda. */
@ExtendWith(MockitoExtension.class)
class AdoptionTransitionsTest {

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
        // As respostas só são montadas nos casos que dão certo
        lenient().when(requestRepository.saveAndFlush(any(AdoptionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(animalService.summarize(anyList())).thenAnswer(invocation -> {
            List<Animal> animals = invocation.getArgument(0);
            return animals.stream().map(each -> AnimalSummaryDTO.from(each, null)).toList();
        });
        lenient().when(matchProfileRepository.findAllById(anyList())).thenReturn(List.of());
    }

    @ParameterizedTest(name = "{0} → {1} por {2}: {3}")
    @CsvSource({
            "PENDING, ACCEPTED, OWNER, OK",
            "PENDING, ACCEPTED, ADOPTER, 403",
            "AWAITING_DELIVERY, ACCEPTED, ADOPTER, OK",
            "AWAITING_DELIVERY, ACCEPTED, OWNER, 400",
            "ACCEPTED, ACCEPTED, ADOPTER, OK",
            "PENDING, REJECTED, OWNER, OK",
            "PENDING, REJECTED, ADOPTER, 403",
            "ACCEPTED, REJECTED, OWNER, 400",
            "ACCEPTED, AWAITING_DELIVERY, OWNER, OK",
            "ACCEPTED, AWAITING_DELIVERY, ADOPTER, 403",
            "PENDING, AWAITING_DELIVERY, OWNER, 400",
            "AWAITING_DELIVERY, CONCLUDED, ADOPTER, OK",
            "AWAITING_DELIVERY, CONCLUDED, OWNER, 403",
            "ACCEPTED, CONCLUDED, ADOPTER, 400",
            "PENDING, CANCELLED, ADOPTER, OK",
            "PENDING, CANCELLED, OWNER, 400",
            "ACCEPTED, CANCELLED, ADOPTER, OK",
            "ACCEPTED, CANCELLED, OWNER, OK",
            "AWAITING_DELIVERY, CANCELLED, ADOPTER, OK",
            "AWAITING_DELIVERY, CANCELLED, OWNER, OK",
            "CONCLUDED, CANCELLED, ADOPTER, 400",
            "CONCLUDED, CANCELLED, OWNER, 400",
            "REJECTED, ACCEPTED, OWNER, 400",
            "CANCELLED, ACCEPTED, OWNER, 400",
            "REJECTED, CANCELLED, ADOPTER, 400",
            "ACCEPTED, PENDING, OWNER, 400",
    })
    void transitionMatrix(AdoptionStatus from, AdoptionStatus target, String role, String expected) {
        AdoptionRequest request = givenRequest(from);
        String email = role.equals("OWNER") ? OWNER_EMAIL : ADOPTER_EMAIL;

        switch (expected) {
            case "OK" -> assertThat(requestService.changeStatus(50L, email, target).status()).isEqualTo(target);
            case "400" -> assertThatThrownBy(() -> requestService.changeStatus(50L, email, target))
                    .isInstanceOf(BusinessException.class);
            case "403" -> assertThatThrownBy(() -> requestService.changeStatus(50L, email, target))
                    .isInstanceOf(ForbiddenException.class);
            default -> throw new IllegalArgumentException(expected);
        }
        if (!expected.equals("OK")) {
            assertThat(request.getStatus()).isEqualTo(from);
        }
    }

    @Test
    void strangerCannotChangeARequest() {
        givenRequest(AdoptionStatus.PENDING);

        assertThatThrownBy(() -> requestService.changeStatus(50L, OTHER_EMAIL, AdoptionStatus.ACCEPTED))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void repeatingTheCurrentStatusChangesNothing() {
        givenRequest(AdoptionStatus.ACCEPTED);

        assertThat(requestService.changeStatus(50L, OWNER_EMAIL, AdoptionStatus.ACCEPTED).status()).isEqualTo(AdoptionStatus.ACCEPTED);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancellingRecordsWhoGaveUpAndFromWhere() {
        AdoptionRequest byOwner = givenRequest(AdoptionStatus.ACCEPTED);
        requestService.changeStatus(50L, OWNER_EMAIL, AdoptionStatus.CANCELLED);

        assertThat(byOwner.getCancelReason()).isEqualTo(CancelReason.OWNER_WITHDREW);
        assertThat(byOwner.getCancelledFromStatus()).isEqualTo(AdoptionStatus.ACCEPTED);
        assertThat(byOwner.getClosedAt()).isNotNull();

        AdoptionRequest byAdopter = givenRequest(AdoptionStatus.AWAITING_DELIVERY);
        AdoptionRequestDTO cancelled = requestService.changeStatus(50L, ADOPTER_EMAIL, AdoptionStatus.CANCELLED);

        assertThat(byAdopter.getCancelReason()).isEqualTo(CancelReason.ADOPTER_WITHDREW);
        assertThat(cancelled.cancelledFromStatus()).isEqualTo(AdoptionStatus.AWAITING_DELIVERY);
    }

    @Test
    void concludingAdoptsTheAnimalAndCancelsTheOtherRequests() {
        AdoptionRequest winner = givenRequest(AdoptionStatus.AWAITING_DELIVERY);
        AdoptionRequest pending = otherRequest(51L, AdoptionStatus.PENDING);
        AdoptionRequest accepted = otherRequest(52L, AdoptionStatus.ACCEPTED);
        when(requestRepository.findByAnimalIdAndStatusIn(10L, AdoptionStatus.ACTIVE)).thenReturn(List.of(winner, pending, accepted));

        AdoptionRequestDTO concluded = requestService.changeStatus(50L, ADOPTER_EMAIL, AdoptionStatus.CONCLUDED);

        assertThat(concluded.status()).isEqualTo(AdoptionStatus.CONCLUDED);
        assertThat(concluded.concludedAt()).isNotNull();
        assertThat(animal.getStatus()).isEqualTo(AnimalStatus.ADOPTED);
        assertThat(List.of(pending, accepted)).allSatisfy(other -> {
            assertThat(other.getStatus()).isEqualTo(AdoptionStatus.CANCELLED);
            assertThat(other.getCancelReason()).isEqualTo(CancelReason.ANIMAL_ADOPTED);
        });
        assertThat(accepted.getCancelledFromStatus()).isEqualTo(AdoptionStatus.ACCEPTED);
        verify(entityManager).refresh(animal, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void concludingFailsWhenSomeoneElseAdoptedFirst() {
        givenRequest(AdoptionStatus.AWAITING_DELIVERY);
        animal.setStatus(AnimalStatus.ADOPTED);

        assertThatThrownBy(() -> requestService.changeStatus(50L, ADOPTER_EMAIL, AdoptionStatus.CONCLUDED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já foi adotado");
    }

    @Test
    void animalLeavingTheListingCancelsItsActiveRequests() {
        AdoptionRequest pending = otherRequest(51L, AdoptionStatus.PENDING);
        AdoptionRequest awaiting = otherRequest(52L, AdoptionStatus.AWAITING_DELIVERY);
        when(requestRepository.findByAnimalIdAndStatusIn(10L, AdoptionStatus.ACTIVE)).thenReturn(List.of(pending, awaiting));

        requestService.onAnimalLeftListing(new AnimalLeftListingEvent(10L));

        assertThat(List.of(pending, awaiting)).allSatisfy(request -> {
            assertThat(request.getStatus()).isEqualTo(AdoptionStatus.CANCELLED);
            assertThat(request.getCancelReason()).isEqualTo(CancelReason.ANIMAL_UNAVAILABLE);
        });
        assertThat(awaiting.getCancelledFromStatus()).isEqualTo(AdoptionStatus.AWAITING_DELIVERY);
    }

    @Test
    void listingRemovedByModerationCancelsWithItsOwnReason() {
        AdoptionRequest accepted = otherRequest(51L, AdoptionStatus.ACCEPTED);
        when(requestRepository.findByAnimalIdAndStatusIn(10L, AdoptionStatus.ACTIVE)).thenReturn(List.of(accepted));

        requestService.onAnimalLeftListing(new AnimalLeftListingEvent(10L, true));

        assertThat(accepted.getCancelReason()).isEqualTo(CancelReason.LISTING_REMOVED);
    }

    // ===================== Fixtures =====================

    private AdoptionRequest givenRequest(AdoptionStatus status) {
        AdoptionRequest request = request(animal, adopter(), status);
        when(requestRepository.findWithDetailsById(50L)).thenReturn(Optional.of(request));
        return request;
    }

    private AdoptionRequest otherRequest(Long id, AdoptionStatus status) {
        User otherAdopter = User.builder().id(id).email("adopter" + id + "@email.com").name("Outro").userType(UserType.PERSON).build();
        AdoptionRequest request = request(animal, otherAdopter, status);
        request.setId(id);
        return request;
    }
}
