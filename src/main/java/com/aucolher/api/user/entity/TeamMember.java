package com.aucolher.api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Integrante da equipe exibida no perfil público da ONG (ex: "Marina Costa", "Presidente"). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String role;
}
