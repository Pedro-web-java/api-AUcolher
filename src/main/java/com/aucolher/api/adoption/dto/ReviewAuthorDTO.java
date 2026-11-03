package com.aucolher.api.adoption.dto;

import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Quem escreveu uma avaliação — só dados públicos. */
public record ReviewAuthorDTO(
        Long id,
        String name,
        String photoUrl,
        @JsonProperty("isOng") boolean ong
) {

    public static ReviewAuthorDTO from(User author) {
        return new ReviewAuthorDTO(author.getId(), author.getName(), author.getPhotoUrl(), author.getUserType() == UserType.NGO);
    }
}
