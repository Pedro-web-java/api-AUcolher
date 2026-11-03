package com.aucolher.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Código que a API mandou ao frontend depois do login Google. */
public record GoogleExchangeDTO(

        @NotBlank(message = "O login com o Google expirou. Tente novamente.")
        String code
) {
}
