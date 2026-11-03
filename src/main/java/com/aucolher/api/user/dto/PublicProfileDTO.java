package com.aucolher.api.user.dto;

import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Perfil público de uma conta (GET /api/users/{id}/public-profile). Usa os
 * mesmos nomes de campo do UserResponseDTO, para o frontend montar os dois
 * com o mesmo mapper. Nunca traz o provedor.
 *
 * ONG mostra o perfil institucional completo, com endereço da sede e o
 * e-mail da conta como contato público. Pessoa física mostra só cidade e UF:
 * e-mail, rua, número e CEP de quem adota não são públicos.
 *
 * Equipe e horários são coleções lazy: monte este DTO dentro de uma transação.
 */
public record PublicProfileDTO(
        Long id,
        String name,
        String email, // só ONG: o contato público é o e-mail de login
        UserType userType,
        LocalDateTime createdAt,
        String photoUrl,
        String coverUrl, // banner: só ONG
        String bio,

        // Perfil de ONG (nulos ou vazios para pessoa)
        String cnpj,
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
        String state,

        // false = conta banida pela moderação (só o admin chega a ver)
        boolean active
) {

    public static PublicProfileDTO from(User user) {
        boolean ngo = user.getUserType() == UserType.NGO;
        return new PublicProfileDTO(
                user.getId(),
                user.getName(),
                ngo ? user.getEmail() : null,
                user.getUserType(),
                user.getCreatedAt(),
                user.getPhotoUrl(),
                ngo ? user.getCoverUrl() : null,
                user.getBio(),
                ngo ? user.getCnpj() : null,
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
                user.getState(),
                Boolean.TRUE.equals(user.getActive())
        );
    }
}
