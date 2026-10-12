package com.aucolher.api.auth.dto;

import com.aucolher.api.user.dto.UserResponseDTO;

public record AuthResponseDTO(
        String token,
        String type,
        UserResponseDTO user
) {
    public AuthResponseDTO(String token, UserResponseDTO user) {
        this(token, "Bearer", user);
    }
}
