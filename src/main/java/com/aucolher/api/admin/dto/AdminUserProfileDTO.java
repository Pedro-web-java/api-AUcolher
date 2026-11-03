package com.aucolher.api.admin.dto;

import com.aucolher.api.user.dto.UserResponseDTO;
import com.aucolher.api.user.entity.User;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * A conta inteira de uma pessoa ou ONG para o admin editar: os mesmos campos
 * de "Minha conta" (com o e-mail de login e `moderatedAt`) e se ela está ativa.
 */
public record AdminUserProfileDTO(
        @JsonUnwrapped UserResponseDTO user,
        boolean active
) {

    public static AdminUserProfileDTO from(User user) {
        return new AdminUserProfileDTO(UserResponseDTO.from(user), Boolean.TRUE.equals(user.getActive()));
    }
}
