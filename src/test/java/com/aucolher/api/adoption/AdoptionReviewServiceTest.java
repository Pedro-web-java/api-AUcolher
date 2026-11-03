package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdoptionRequestDTO;
import com.aucolher.api.adoption.dto.RequestReviewsDTO;
import com.aucolher.api.adoption.dto.SaveReviewDTO;
import com.aucolher.api.adoption.dto.UserReviewsDTO;
import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionReview;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.ReviewRole;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.aucolher.api.adoption.AdoptionRequestServiceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Regras das avaliações: quem avalia, quando, e o resumo público de um perfil. */
@ExtendWith(MockitoExtension.class)
class AdoptionReviewServiceTest {

    @Mock
    private AdoptionRequestService requestService;
    @Mock
    private AdoptionRequestRepository requestRepository;
    @Mock
    private AdoptionReviewRepository reviewRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdoptionReviewService reviewService;

    @Test
    void adopterReviewsTheOwnerAfterTheAdoption() {
        givenRequest(AdoptionStatus.CONCLUDED);
        when(reviewRepository.findByRequestIdAndAuthorRole(50L, ReviewRole.ADOPTER)).thenReturn(Optional.empty());
        givenResponse();

        reviewService.saveMine(50L, ADOPTER_EMAIL, new SaveReviewDTO(5, "  Tudo certo, recomendo!  "));

        ArgumentCaptor<AdoptionReview> saved = ArgumentCaptor.forClass(AdoptionReview.class);
        verify(reviewRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAuthorRole()).isEqualTo(ReviewRole.ADOPTER);
        assertThat(saved.getValue().getRating()).isEqualTo(5);
        assertThat(saved.getValue().getComment()).isEqualTo("Tudo certo, recomendo!");
        assertThat(saved.getValue().getUpdatedAt()).isNull();
    }

    @Test
    void editingKeepsOneReviewAndMarksTheEdit() {
        AdoptionRequest request = givenRequest(AdoptionStatus.CONCLUDED);
        AdoptionReview existing = review(request, ReviewRole.OWNER, 3);
        when(reviewRepository.findByRequestIdAndAuthorRole(50L, ReviewRole.OWNER)).thenReturn(Optional.of(existing));
        givenResponse();

        reviewService.saveMine(50L, OWNER_EMAIL, new SaveReviewDTO(4, null));

        verify(reviewRepository).saveAndFlush(existing);
        assertThat(existing.getRating()).isEqualTo(4);
        assertThat(existing.getUpdatedAt()).isNotNull();
    }

    @Test
    void returnedOrRehomedAdoptionsCanStillBeReviewed() {
        givenRequest(AdoptionStatus.RETURNED);
        when(reviewRepository.findByRequestIdAndAuthorRole(50L, ReviewRole.OWNER)).thenReturn(Optional.empty());
        givenResponse();

        reviewService.saveMine(50L, OWNER_EMAIL, new SaveReviewDTO(4, null));

        verify(reviewRepository).saveAndFlush(any(AdoptionReview.class));
    }

    @Test
    void reviewsWaitForTheAdoptionToConclude() {
        givenRequest(AdoptionStatus.AWAITING_DELIVERY);

        assertThatThrownBy(() -> reviewService.saveMine(50L, ADOPTER_EMAIL, new SaveReviewDTO(5, null)))
                .isInstanceOf(BusinessException.class);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyThePartiesReview() {
        givenRequest(AdoptionStatus.CONCLUDED);

        assertThatThrownBy(() -> reviewService.saveMine(50L, OTHER_EMAIL, new SaveReviewDTO(5, null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reviewService.deleteMine(50L, OTHER_EMAIL))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletingRemovesOnlyMyReview() {
        AdoptionRequest request = givenRequest(AdoptionStatus.CONCLUDED);
        AdoptionReview mine = review(request, ReviewRole.ADOPTER, 5);
        when(reviewRepository.findByRequestIdAndAuthorRole(50L, ReviewRole.ADOPTER)).thenReturn(Optional.of(mine));

        reviewService.deleteMine(50L, ADOPTER_EMAIL);

        verify(reviewRepository).delete(mine);
    }

    @Test
    void summaryAveragesTheStarsAndCountsAdoptions() {
        AdoptionRequest request = request(animal(AnimalStatus.ADOPTED), adopter(), AdoptionStatus.CONCLUDED);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(reviewRepository.findAboutUser(2L, ReviewRole.ADOPTER, ReviewRole.OWNER))
                .thenReturn(List.of(review(request, ReviewRole.ADOPTER, 5), review(request, ReviewRole.ADOPTER, 4),
                        review(request, ReviewRole.ADOPTER, 4)));
        when(requestRepository.countByStatusInvolving(2L, AdoptionStatus.HAPPENED)).thenReturn(2L);

        UserReviewsDTO summary = reviewService.summaryOf(2L);

        assertThat(summary.average()).isEqualTo(4.3);
        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.distribution()).containsExactly(
                Map.entry(5, 1L), Map.entry(4, 2L), Map.entry(3, 0L), Map.entry(2, 0L), Map.entry(1, 0L));
        assertThat(summary.adoptionsCount()).isEqualTo(2);
        // A avaliação do adotante aparece com o nome dele e o nome do animal
        assertThat(summary.reviews().get(0).author().id()).isEqualTo(1L);
        assertThat(summary.reviews().get(0).animalName()).isEqualTo("Thor");
    }

    @Test
    void summaryOfAnUnknownProfileReturns404() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.summaryOf(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ===================== Fixtures =====================

    private AdoptionRequest givenRequest(AdoptionStatus status) {
        AdoptionRequest request = request(animal(AnimalStatus.ADOPTED), adopter(), status);
        when(requestService.findRequest(50L)).thenReturn(request);
        return request;
    }

    /** A resposta do PUT é o pedido montado pela AdoptionRequestService (simulada aqui). */
    private void givenResponse() {
        when(requestService.toDtos(anyList())).thenReturn(List.of(new AdoptionRequestDTO(
                50L, AdoptionStatus.CONCLUDED, null, null, null, null, null, null, null, null, 0, null, null, RequestReviewsDTO.NONE)));
    }

    private static AdoptionReview review(AdoptionRequest request, ReviewRole role, int rating) {
        AdoptionReview review = new AdoptionReview(request, role);
        review.setId(70L + rating);
        review.setRating(rating);
        return review;
    }
}
