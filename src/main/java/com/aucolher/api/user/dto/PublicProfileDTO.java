package com.aucolher.api.user.dto;

import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Perfil público de uma conta (GET /api/users/{id}/public-profile). Usa os
 * mesmos nomes de campo do UserResponseDTO, para o frontend montar os dois
 * com o mesmo mapper, mas nunca traz e-mail de login nem provedor.
 *
 * ONG mostra o perfil institucional completo, com endereço da sede. Pessoa
 * física mostra só cidade e UF: rua, número e CEP de quem adota não são públicos.
 *
 * Equipe e horários são coleções lazy: monte este DTO dentro de uma transação.
 */
public record PublicProfileDTO(
        Long id,
        String name,
        UserType userType,
        LocalDateTime createdAt,
        String photoUrl,
        String bio,

        // Perfil de ONG (nulos ou vazios para pessoa)
        String cnpj,
        String institutionalEmail,
        @JsonProperty("isVerified") Boolean verified,
        String instagram,
        String twitter,
        String facebook,
        Integer foundedYear,
        List<TeamMemberDTO> team,
        List<VisitingHourDTO> visitingHours,

        // Endereço (só cidade e UF para pessoa)
        String cep,
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state
) {

    public static PublicProfileDTO from(User user) {
        boolean ngo = user.getUserType() == UserType.NGO;
        return new PublicProfileDTO(
                user.getId(),
                user.getName(),
                user.getUserType(),
                user.getCreatedAt(),
                user.getPhotoUrl(),
                user.getBio(),
                ngo ? user.getCnpj() : null,
                ngo ? user.getInstitutionalEmail() : null,
                ngo && Boolean.TRUE.equals(user.getVerified()),
                ngo ? user.getInstagram() : null,
                ngo ? user.getTwitter() : null,
                ngo ? user.getFacebook() : null,
                ngo ? user.getFoundedYear() : null,
                ngo ? user.getTeam().stream().map(TeamMemberDTO::from).toList() : List.of(),
                ngo ? user.getVisitingHours().stream().map(VisitingHourDTO::from).toList() : List.of(),
                ngo ? user.getCep() : null,
                ngo ? user.getStreet() : null,
                ngo ? user.getNumber() : null,
                ngo ? user.getComplement() : null,
                ngo ? user.getDistrict() : null,
                user.getCity(),
                user.getState()
        );
    }
}
