package com.aucolher.api.user.dto;

import com.aucolher.api.shared.validation.Sanitizer;
import com.aucolher.api.user.entity.TeamMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Integrante da equipe da ONG — colunas de ngo_team. Usado na edição do perfil e nas respostas. */
public record TeamMemberDTO(

        @NotBlank(message = "Informe o nome de cada integrante da equipe")
        @Size(max = 150, message = "O nome do integrante deve ter no máximo 150 caracteres")
        String name,

        @Size(max = 100, message = "A função do integrante deve ter no máximo 100 caracteres")
        String role
) {

    public TeamMemberDTO {
        name = Sanitizer.text(name);
        role = Sanitizer.text(role);
    }

    public static TeamMemberDTO from(TeamMember member) {
        return new TeamMemberDTO(member.getName(), member.getRole());
    }
}
