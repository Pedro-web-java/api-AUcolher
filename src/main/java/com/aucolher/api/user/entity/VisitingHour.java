package com.aucolher.api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Faixa de horário de visitas da ONG (ex: "Terça a sexta", "14h às 18h"). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VisitingHour {

    @Column(nullable = false, length = 80)
    private String days;

    @Column(nullable = false, length = 80)
    private String hours;
}
