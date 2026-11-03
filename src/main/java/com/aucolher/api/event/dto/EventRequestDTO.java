package com.aucolher.api.event.dto;

import com.aucolher.api.event.entity.EventCategory;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

/** Corpo de POST e PUT /api/events (o PUT substitui todos os campos). */
public record EventRequestDTO(
        @NotBlank(message = "Informe o título do evento.")
        @Size(min = 3, max = 120, message = "O título deve ter de 3 a 120 caracteres.")
        String title,

        @NotBlank(message = "Informe a descrição do evento.")
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres.")
        String description,

        @NotNull(message = "Escolha uma categoria.")
        EventCategory category,

        /** Só com category = OTHER; nas outras o service grava null. */
        @Size(max = 30, message = "O nome da categoria deve ter no máximo 30 caracteres.")
        String customCategory,

        @NotNull(message = "Informe a data do evento.")
        @FutureOrPresent(message = "A data do evento não pode estar no passado.")
        LocalDate date,

        @NotNull(message = "Informe o horário de início.")
        LocalTime startTime,

        LocalTime endTime,

        @NotBlank(message = "Informe o nome do local.")
        @Size(max = 120, message = "O nome do local deve ter no máximo 120 caracteres.")
        String venueName,

        @Pattern(regexp = "\\d{8}", message = "O CEP deve ter 8 dígitos.")
        String cep,

        @NotBlank(message = "Informe a rua.")
        @Size(max = 150, message = "A rua deve ter no máximo 150 caracteres.")
        String street,

        @Size(max = 20, message = "O número deve ter no máximo 20 caracteres.")
        String number,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres.")
        String complement,

        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.")
        String district,

        @NotBlank(message = "Informe a cidade.")
        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.")
        String city,

        @NotBlank(message = "Informe a UF.")
        @Pattern(regexp = "[A-Z]{2}", message = "Informe a UF com 2 letras.")
        String state,

        @Min(value = 1, message = "A capacidade deve ser de pelo menos 1.")
        Integer capacity,

        String coverUrl
) {
    /** Normaliza: apara espaços, texto em branco vira null, CEP só com dígitos, UF em maiúsculas. */
    public EventRequestDTO {
        title = clean(title);
        description = clean(description);
        customCategory = clean(customCategory);
        venueName = clean(venueName);
        cep = cep == null ? null : clean(cep.replaceAll("\\D", ""));
        street = clean(street);
        number = clean(number);
        complement = clean(complement);
        district = clean(district);
        city = clean(city);
        state = clean(state);
        if (state != null) {
            state = state.toUpperCase();
        }
        coverUrl = clean(coverUrl);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
