package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdoptionRequestDTO;
import com.aucolher.api.adoption.dto.SaveReviewDTO;
import com.aucolher.api.adoption.dto.UserReviewDTO;
import com.aucolher.api.adoption.dto.UserReviewsDTO;
import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionReview;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.ReviewRole;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Avaliações das adoções. Depois que um pedido é concluído, cada parte pode
 * avaliar a outra uma vez — e editar ou apagar a própria avaliação quando quiser.
 */
@Service
@RequiredArgsConstructor
public class AdoptionReviewService {

    private final AdoptionRequestService requestService;
    private final AdoptionRequestRepository requestRepository;
    private final AdoptionReviewRepository reviewRepository;
    private final UserRepository userRepository;

    /** Cria ou edita a avaliação de quem está logado. Devolve o pedido já com ela. */
    @Transactional
    public AdoptionRequestDTO saveMine(Long requestId, String email, SaveReviewDTO body) {
        AdoptionRequest request = requestService.findRequest(requestId);
        ReviewRole role = roleOf(request, email);
        // O retorno e o repasse não apagam a adoção: as avaliações continuam liberadas
        if (!AdoptionStatus.HAPPENED.contains(request.getStatus())) {
            throw new BusinessException("Só dá para avaliar depois que a adoção for concluída");
        }

        AdoptionReview review = reviewRepository.findByRequestIdAndAuthorRole(requestId, role)
                .orElseGet(() -> new AdoptionReview(request, role));
        review.edit(body.rating(), body.comment());
        reviewRepository.saveAndFlush(review);
        return requestService.toDtos(List.of(request)).get(0);
    }

    /** Apaga a avaliação de quem está logado. Apagar o que não existe não dá erro. */
    @Transactional
    public void deleteMine(Long requestId, String email) {
        AdoptionRequest request = requestService.findRequest(requestId);
        reviewRepository.findByRequestIdAndAuthorRole(requestId, roleOf(request, email))
                .ifPresent(reviewRepository::delete);
    }

    /**
     * Reputação pública de uma conta: o que adotantes e donos disseram sobre
     * ela, a média (uma casa decimal), quantas de cada nota e quantas adoções
     * ela concluiu.
     */
    @Transactional(readOnly = true)
    public UserReviewsDTO summaryOf(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuário não encontrado");
        }

        List<AdoptionReview> reviews = reviewRepository.findAboutUser(userId, ReviewRole.ADOPTER, ReviewRole.OWNER);

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            distribution.put(stars, 0L);
        }
        reviews.forEach(review -> distribution.merge(review.getRating(), 1L, Long::sum));

        double average = reviews.stream().mapToInt(AdoptionReview::getRating).average().orElse(0);

        return new UserReviewsDTO(
                Math.round(average * 10) / 10.0,
                reviews.size(),
                distribution,
                requestRepository.countByStatusInvolving(userId, AdoptionStatus.HAPPENED),
                reviews.stream().map(UserReviewDTO::from).toList());
    }

    /** O adotante avalia como ADOPTER; o dono do animal, como OWNER. */
    private static ReviewRole roleOf(AdoptionRequest request, String email) {
        if (request.isAdopter(email)) return ReviewRole.ADOPTER;
        if (request.isOwnedBy(email)) return ReviewRole.OWNER;
        throw new ForbiddenException("Só quem participou da adoção pode avaliá-la");
    }
}
