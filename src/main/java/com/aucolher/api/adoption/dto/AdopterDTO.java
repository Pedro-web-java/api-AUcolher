package com.aucolher.api.adoption.dto;

import com.aucolher.api.match.dto.MatchProfileDTO;
import com.aucolher.api.match.entity.MatchProfile;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;

/**
 * Quem pediu o animal, como o dono vê no card do pedido: dados públicos e as
 * respostas do quiz do AUmatch (`matchProfile`, nulo para ONG ou para quem
 * ainda não respondeu), com as quais o frontend monta o resumo do estilo de vida.
 */
public record AdopterDTO(
        Long id,
        String name,
        String photoUrl,
        UserType userType,
        String city,
        String state,
        MatchProfileDTO matchProfile
) {

    public static AdopterDTO from(User adopter, MatchProfile profile) {
        return new AdopterDTO(
                adopter.getId(),
                adopter.getName(),
                adopter.getPhotoUrl(),
                adopter.getUserType(),
                adopter.getCity(),
                adopter.getState(),
                profile == null ? null : MatchProfileDTO.from(profile));
    }
}
