package com.aucolher.api.user.dto;

import com.aucolher.api.shared.validation.FoundedYear;
import com.aucolher.api.shared.validation.PhotoUrl;
import com.aucolher.api.shared.validation.Sanitizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Edição do perfil pela própria conta ("Minha conta"). Serve aos dois tipos:
 * os campos de ONG (e o endereço completo) só são gravados em contas ONG —
 * a Service ignora o que um usuário comum mandar neles. E-mail, senha e CNPJ
 * não mudam por aqui.
 *
 * É uma substituição completa (PUT): campo opcional que não vier é apagado.
 * Mesma normalização do cadastro (ver {@link Sanitizer}).
 */
public record ProfileUpdateDTO(

        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 2, max = 150, message = "O nome deve ter entre 2 e 150 caracteres")
        String name,

        @Size(max = PhotoUrl.MAX_LENGTH, message = "A foto é grande demais")
        @Pattern(regexp = PhotoUrl.FORMAT, message = "Foto em formato inválido")
        String photoUrl,

        @Size(max = 500, message = "A bio deve ter no máximo 500 caracteres")
        String bio,

        // ===================== Perfil de ONG =====================

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

        @Valid
        @Size(max = 20, message = "A equipe pode ter no máximo 20 integrantes")
        List<TeamMemberDTO> team,

        @Valid
        @Size(max = 20, message = "Cadastre no máximo 20 faixas de horário de visita")
        List<VisitingHourDTO> visitingHours,

        // ===================== Endereço =====================
        // Obrigatório para ONG (conferido na Service); o usuário comum usa só CEP, cidade e UF

        @Pattern(regexp = "^\\d{8}$", message = "CEP em formato inválido")
        String cep,

        @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres")
        String street,

        @Size(max = 20, message = "O número deve ter no máximo 20 caracteres")
        String number,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres")
        String complement,

        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres")
        String district,

        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres")
        String city,

        @Pattern(regexp = "^[A-Z]{2}$", message = "Estado deve ser a sigla da UF (ex: SC)")
        String state
) {

    public ProfileUpdateDTO {
        name = Sanitizer.text(name);
        photoUrl = Sanitizer.text(photoUrl);
        bio = Sanitizer.text(bio);
        institutionalEmail = Sanitizer.text(institutionalEmail);
        instagram = Sanitizer.withoutAt(instagram);
        twitter = Sanitizer.withoutAt(twitter);
        facebook = Sanitizer.withProtocol(facebook);
        team = team == null ? List.of() : team;
        visitingHours = visitingHours == null ? List.of() : visitingHours;
        cep = Sanitizer.digitsOnly(cep);
        street = Sanitizer.text(street);
        number = Sanitizer.text(number);
        complement = Sanitizer.text(complement);
        district = Sanitizer.text(district);
        city = Sanitizer.text(city);
        state = Sanitizer.uppercase(state);
    }
}
