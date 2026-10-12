package com.aucolher.api.auth.dto;

import com.aucolher.api.shared.validation.FoundedYear;
import com.aucolher.api.shared.validation.PhotoUrl;
import com.aucolher.api.shared.validation.Sanitizer;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CNPJ;

/**
 * Cadastro de ONG. O construtor compacto normaliza os campos (ver
 * {@link Sanitizer}), então as validações abaixo só precisam checar o
 * formato canônico — CNPJ e CEP já chegam aqui apenas com dígitos.
 */
public record NgoRegistrationDTO(

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

        @NotBlank(message = "O CNPJ é obrigatório")
        // @CNPJ confere os dígitos verificadores, mas dá como válida qualquer
        // sequência de dígitos repetidos (00.000.000/0000-00 passa) — o lookahead barra isso
        @Pattern(regexp = "^(?!(\\d)\\1{13}$).*$", message = "CNPJ inválido")
        @CNPJ(message = "CNPJ inválido")
        String cnpj,

        // ===================== Opcionais =====================

        @Size(max = PhotoUrl.MAX_LENGTH, message = "A foto é grande demais")
        @Pattern(regexp = PhotoUrl.FORMAT, message = "Foto em formato inválido")
        String photoUrl,

        @Size(max = 500, message = "A bio deve ter no máximo 500 caracteres")
        String bio,

        @Email(message = "E-mail institucional em formato inválido")
        @Size(max = 150, message = "O e-mail institucional deve ter no máximo 150 caracteres")
        String institutionalEmail,

        @Pattern(regexp = "^[A-Za-z0-9._]{1,30}$", message = "Usuário do Instagram inválido")
        String instagram,

        @Pattern(regexp = "^[A-Za-z0-9_]{1,15}$", message = "Usuário do X (Twitter) inválido")
        String twitter,

        @Size(max = 255, message = "O link do Facebook deve ter no máximo 255 caracteres")
        @Pattern(
                regexp = "^https?://([\\w-]+\\.)*(facebook|fb)\\.com/.+$",
                message = "Informe o link da página no Facebook"
        )
        String facebook,

        @FoundedYear
        Integer foundedYear,

        // ===================== Endereço (obrigatório para ONG) =====================

        @NotBlank(message = "O CEP é obrigatório")
        @Pattern(regexp = "^\\d{8}$", message = "CEP em formato inválido")
        String cep,

        @NotBlank(message = "O logradouro é obrigatório")
        @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres")
        String street,

        @NotBlank(message = "O número é obrigatório")
        @Size(max = 20, message = "O número deve ter no máximo 20 caracteres")
        String number,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres")
        String complement,

        @NotBlank(message = "O bairro é obrigatório")
        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres")
        String district,

        @NotBlank(message = "A cidade é obrigatória")
        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres")
        String city,

        @NotBlank(message = "O estado é obrigatório")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Estado deve ser a sigla da UF (ex: SC)")
        String state
) {

    public NgoRegistrationDTO {
        name = Sanitizer.text(name);
        email = Sanitizer.text(email);
        cnpj = Sanitizer.digitsOnly(cnpj);
        photoUrl = Sanitizer.text(photoUrl);
        bio = Sanitizer.text(bio);
        institutionalEmail = Sanitizer.text(institutionalEmail);
        instagram = Sanitizer.withoutAt(instagram);
        twitter = Sanitizer.withoutAt(twitter);
        facebook = Sanitizer.withProtocol(facebook);
        cep = Sanitizer.digitsOnly(cep);
        street = Sanitizer.text(street);
        number = Sanitizer.text(number);
        complement = Sanitizer.text(complement);
        district = Sanitizer.text(district);
        city = Sanitizer.text(city);
        state = Sanitizer.uppercase(state);
        // senha não passa por trim: espaços podem fazer parte dela
    }
}
