package com.aucolher.api.adoption.dto;

import com.aucolher.api.adoption.entity.AdoptionReview;
import com.aucolher.api.adoption.entity.ReviewRole;
import com.aucolher.api.user.entity.User;

import java.time.LocalDateTime;

/** Uma avaliação dentro de um pedido. `updatedAt` só vem preenchida se o autor editou. */
public record ReviewDTO(
        Long id,
        int rating,
        String comment,
        ReviewAuthorDTO author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ReviewDTO from(AdoptionReview review) {
        return new ReviewDTO(
                review.getId(),
                review.getRating(),
                review.getComment(),
                ReviewAuthorDTO.from(authorOf(review)),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    /** O adotante escreve como ADOPTER; o dono do animal, como OWNER. */
    static User authorOf(AdoptionReview review) {
        return review.getAuthorRole() == ReviewRole.ADOPTER
                ? review.getRequest().getAdopter()
                : review.getRequest().getAnimal().getOwner();
    }
}
