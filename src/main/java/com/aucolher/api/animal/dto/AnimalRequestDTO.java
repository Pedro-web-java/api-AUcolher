package com.aucolher.api.animal.dto;

import com.aucolher.api.animal.entity.*;
import com.aucolher.api.shared.validation.PhotoUrl;
import com.aucolher.api.shared.validation.Sanitizer;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Cadastro (POST) e edição (PUT) de um animal — os dois recebem o anúncio
 * inteiro. Os campos seguem as etapas do formulário do frontend: dados
 * básicos, saúde, comportamento/compatibilidade e anúncio.
 *
 * Cidade e UF não vêm aqui: são as do perfil do dono.
 */
public record AnimalRequestDTO(

        // ===================== Dados básicos =====================

        @NotBlank(message = "O nome do animal é obrigatório")
        @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres")
        String name,

        @NotNull(message = "Informe a espécie")
        Species species,

        @NotBlank(message = "A raça é obrigatória (use \"SRD\" ou \"Vira-lata\" se não souber)")
        @Size(max = 60, message = "A raça deve ter no máximo 60 caracteres")
        String breed,

        @NotNull(message = "Informe o sexo")
        Sex sex,

        @NotNull(message = "Informe a idade")
        Integer ageValue,

        @NotNull(message = "Informe se a idade está em anos ou meses")
        AgeUnit ageUnit,

        @NotNull(message = "Informe o porte")
        AnimalSize size,

        // ===================== Saúde (não informado = não) =====================

        Boolean vaccinated,
        Boolean neutered,
        Boolean dewormed,
        Boolean specialNeeds,

        // ===================== Comportamento e compatibilidade =====================

        @NotNull(message = "Informe o nível de energia")
        Level energyLevel,

        @NotNull(message = "Informe o temperamento")
        Temperament temperament,

        @NotNull(message = "Informe o nível de independência")
        Level independenceLevel,

        @NotNull(message = "Informe o nível de vocalização")
        Level vocalization,

        @NotNull(message = "Informe se o animal é bom com crianças")
        Boolean goodWithChildren,

        @NotNull(message = "Informe se o animal é bom com outros cães")
        Boolean goodWithDogs,

        @NotNull(message = "Informe se o animal é bom com gatos")
        Boolean goodWithCats,

        @NotNull(message = "Informe se o animal vive bem em apartamento")
        Boolean apartmentFriendly,

        // ===================== Anúncio =====================

        @NotBlank(message = "O resumo é obrigatório")
        @Size(max = 200, message = "O resumo deve ter no máximo 200 caracteres")
        String summary,

        @NotBlank(message = "A história é obrigatória")
        @Size(max = 3000, message = "A história deve ter no máximo 3000 caracteres")
        String story,

        @Size(min = 1, max = 4, message = "Envie de 1 a 4 fotos")
        List<
                @NotBlank(message = "Foto vazia")
                @Size(max = PhotoUrl.MAX_LENGTH, message = "Uma das fotos é grande demais")
                @Pattern(regexp = PhotoUrl.FORMAT, message = "Foto em formato inválido")
                String> photos
) {

    public AnimalRequestDTO {
        name = Sanitizer.text(name);
        breed = Sanitizer.text(breed);
        summary = Sanitizer.text(summary);
        story = Sanitizer.text(story);
        vaccinated = Boolean.TRUE.equals(vaccinated);
        neutered = Boolean.TRUE.equals(neutered);
        dewormed = Boolean.TRUE.equals(dewormed);
        specialNeeds = Boolean.TRUE.equals(specialNeeds);
        photos = photos == null ? List.of() : photos.stream().map(Sanitizer::text).toList();
    }

    /**
     * Filhotes são informados em meses (0 a 11) e os demais em anos (1 a 30),
     * como no formulário. É um método de validação, não um campo do JSON.
     */
    @AssertTrue(message = "Idade inválida: informe de 0 a 11 meses ou de 1 a 30 anos")
    public boolean isAgeValid() {
        if (ageValue == null || ageUnit == null) return true; // o @NotNull de cada campo já acusa
        return ageUnit == AgeUnit.MONTHS
                ? ageValue >= 0 && ageValue <= 11
                : ageValue >= 1 && ageValue <= 30;
    }
}
