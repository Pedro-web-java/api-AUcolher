package com.aucolher.api.user.dto;

import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.UserType;
import com.aucolher.api.user.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Usuário devolvido em todas as respostas de autenticação (login,
 * cadastro e OAuth2) e na edição do perfil. Campos de ONG/endereço vêm
 * nulos (e as listas vazias) quando não se aplicam.
 *
 * Equipe e horários são coleções lazy: monte este DTO dentro de uma
 * transação (open-in-view está desligado).
 */
public record UserResponseDTO(
        Long id,
        String name,
        String email,
        UserType userType,
        AuthProvider provider,
        LocalDateTime createdAt, // base do "Membro desde [ano]" no perfil
        String photoUrl,
        String bio,

        // Perfil de ONG
        String cnpj,
        String institutionalEmail,
        @JsonProperty("isVerified") Boolean verified,
        String instagram,
        String twitter,
        String facebook,
        Integer foundedYear,
        List<TeamMemberDTO> team,
        List<VisitingHourDTO> visitingHours,

        // Endereço
        String cep,
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state
) {

    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getUserType(),
                user.getProvider(),
                user.getCreatedAt(),
                user.getPhotoUrl(),
                user.getBio(),
                user.getCnpj(),
                user.getInstitutionalEmail(),
                user.getVerified(),
                user.getInstagram(),
                user.getTwitter(),
                user.getFacebook(),
                user.getFoundedYear(),
                user.getTeam().stream().map(TeamMemberDTO::from).toList(),
                user.getVisitingHours().stream().map(VisitingHourDTO::from).toList(),
                user.getCep(),
                user.getStreet(),
                user.getNumber(),
                user.getComplement(),
                user.getDistrict(),
                user.getCity(),
                user.getState()
        );
    }
}
