package com.aucolher.api.admin.dto;

import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Um anúncio no painel do admin: o mesmo card da vitrine (com `owner.active`)
 * mais `listingStatus`, a situação do ponto de vista da plataforma.
 */
public record AdminAnimalDTO(
        @JsonUnwrapped AnimalSummaryDTO animal,
        ListingStatus listingStatus
) {
}
