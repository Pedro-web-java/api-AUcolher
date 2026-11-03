package com.aucolher.api.admin.dto;

import com.aucolher.api.shared.validation.Sanitizer;
import com.aucolher.api.user.dto.ProfileUpdateDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo do PUT /api/admin/users/{id}: o e-mail de login e o perfil inteiro,
 * com as mesmas validações de "Minha conta" (`profile`).
 */
public record AdminUserUpdateDTO(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @NotNull(message = "Envie o perfil da conta")
        @Valid
        ProfileUpdateDTO profile
) {

    public AdminUserUpdateDTO {
        email = Sanitizer.text(email);
    }
}
