package com.aucolher.api.auth.dto;

import com.aucolher.api.shared.validation.PhotoUrl;
import com.aucolher.api.shared.validation.Sanitizer;
import jakarta.validation.constraints.*;

public record PersonRegistrationDTO(

        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 2, max = 150, message = "O nome deve ter entre 2 e 150 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
        String password,

        @Size(max = PhotoUrl.MAX_LENGTH, message = "A foto é grande demais")
        @Pattern(regexp = PhotoUrl.FORMAT, message = "Foto em formato inválido")
        String photoUrl
) {

    public PersonRegistrationDTO {
        name = Sanitizer.text(name);
        email = Sanitizer.text(email);
        photoUrl = Sanitizer.text(photoUrl);
    }
}
