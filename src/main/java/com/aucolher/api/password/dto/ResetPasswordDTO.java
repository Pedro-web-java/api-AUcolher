package com.aucolher.api.password.dto;

public record ResetPasswordDTO(String token, String newPassword) {
}
