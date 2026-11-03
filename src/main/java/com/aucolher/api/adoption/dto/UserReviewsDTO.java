package com.aucolher.api.adoption.dto;

import java.util.List;
import java.util.Map;

/**
 * Reputação pública de uma conta: média e quantidade das avaliações
 * recebidas, quantas de cada nota (`distribution`, chaves de 5 a 1), as
 * avaliações e o número de adoções concluídas em que ela participou (como
 * dona do animal ou como adotante).
 */
public record UserReviewsDTO(
        double average,
        long count,
        Map<Integer, Long> distribution,
        long adoptionsCount,
        List<UserReviewDTO> reviews
) {
}
