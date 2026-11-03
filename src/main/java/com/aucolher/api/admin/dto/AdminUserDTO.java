package com.aucolher.api.admin.dto;

import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Uma conta no painel do admin. `active` = false é conta banida pela
 * moderação ou, com `deletedAt` preenchido, excluída pela própria pessoa
 * (anonimizada, sem volta); `animalsCount` conta os anúncios dela em
 * qualquer status.
 */
public record AdminUserDTO(
        Long id,
        String name,
        String email,
        UserType userType,
        String photoUrl,
        String city,
        String state,
        @JsonProperty("isVerified") boolean verified,
        boolean active,
        LocalDateTime createdAt,
        long animalsCount,
        LocalDateTime moderatedAt,
        LocalDateTime deletedAt
) {

    public static AdminUserDTO from(User user, long animalsCount) {
        return new AdminUserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getUserType(),
                user.getPhotoUrl(),
                user.getCity(),
                user.getState(),
                Boolean.TRUE.equals(user.getVerified()),
                Boolean.TRUE.equals(user.getActive()),
                user.getCreatedAt(),
                animalsCount,
                user.getModeratedAt(),
                user.getDeletedAt());
    }
}
