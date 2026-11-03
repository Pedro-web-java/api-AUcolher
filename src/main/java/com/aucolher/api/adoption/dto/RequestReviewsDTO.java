package com.aucolher.api.adoption.dto;

/**
 * As duas avaliações de um pedido concluído: `adopter` é a que o adotante
 * escreveu (sobre o dono) e `owner` a que o dono escreveu (sobre o adotante).
 * Nulas enquanto ninguém avaliou.
 */
public record RequestReviewsDTO(
        ReviewDTO adopter,
        ReviewDTO owner
) {

    public static final RequestReviewsDTO NONE = new RequestReviewsDTO(null, null);
}
