package com.aucolher.api.auth.dto;

import com.aucolher.api.shared.validation.Sanitizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginDTO(

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        String password
) {

    public LoginDTO {
        // Autocompletar do navegador costuma trazer espaço no fim do e-mail
        email = Sanitizer.text(email);
    }
}
