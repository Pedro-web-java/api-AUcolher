package com.aucolher.api.animal.dto;

import com.aucolher.api.user.entity.UserType;
import com.aucolher.api.user.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Quem anunciou o animal, como aparece no card e na página de detalhes.
 * Só dados públicos: nada de e-mail ou endereço completo.
 * `userType` diz se o anúncio é de uma ONG ou de uma pessoa.
 */
public record AnimalOwnerDTO(
        Long id,
        String name,
        String photoUrl,
        UserType userType,
        @JsonProperty("isVerified") Boolean verified
) {

    public static AnimalOwnerDTO from(User owner) {
        return new AnimalOwnerDTO(owner.getId(), owner.getName(), owner.getPhotoUrl(), owner.getUserType(), owner.getVerified());
    }
}
