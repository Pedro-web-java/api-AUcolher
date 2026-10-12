package com.aucolher.api.user.dto;

import com.aucolher.api.shared.validation.Sanitizer;
import com.aucolher.api.user.entity.VisitingHour;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Faixa de horário de visita da ONG — colunas de ngo_visiting_hours. Usado na edição do perfil e nas respostas. */
public record VisitingHourDTO(

        @NotBlank(message = "Informe os dias de cada horário de visita")
        @Size(max = 80, message = "Os dias do horário de visita devem ter no máximo 80 caracteres")
        String days,

        @NotBlank(message = "Informe o horário de cada faixa de visita")
        @Size(max = 80, message = "O horário de visita deve ter no máximo 80 caracteres")
        String hours
) {

    public VisitingHourDTO {
        days = Sanitizer.text(days);
        hours = Sanitizer.text(hours);
    }

    public static VisitingHourDTO from(VisitingHour slot) {
        return new VisitingHourDTO(slot.getDays(), slot.getHours());
    }
}
