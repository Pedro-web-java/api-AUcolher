package com.aucolher.api.adoption.dto;

import com.aucolher.api.adoption.entity.AdoptionReview;

import java.time.LocalDateTime;

/** Uma avaliação recebida por uma conta, como aparece no perfil público. */
public record UserReviewDTO(
        Long id,
        Long requestId,
        int rating,
        String comment,
        ReviewAuthorDTO author,
        String animalName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserReviewDTO from(AdoptionReview review) {
        return new UserReviewDTO(
                review.getId(),
                review.getRequest().getId(),
                review.getRating(),
                review.getComment(),
                ReviewAuthorDTO.from(ReviewDTO.authorOf(review)),
                review.getRequest().getAnimal().getName(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}
